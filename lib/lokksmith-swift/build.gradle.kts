import java.security.MessageDigest
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    id("testlogger-conventions")
    id("spotless-conventions")
}

/** Name of the produced framework, and of the Swift module consumers import. */
val frameworkName = "Lokksmith"

/** Minimum iOS version of the produced framework. Must match `platforms` in `Package.swift`. */
val iosDeploymentTarget = "15.0"

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    // No XCFramework is registered here on purpose. `createSwiftXCFramework` assembles the
    // published one from these per-target frameworks, because the plugin's `assembleXCFramework`
    // is not reproducible and SPM pins the archive by checksum.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = frameworkName
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Deliberately `implementation`, not `api`: lokksmith-core is linked into the framework
            // but not exported to Objective-C, so the generated Swift surface contains only the
            // Swift-facing facade declared in this module.
            //
            // This also means the facade must not reference core types in its public signatures,
            // which is why it declares its own models rather than re-exposing core's.
            implementation(project(":lokksmith-core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * Compiles and **links** `src/swiftApiTest` against the release framework.
 *
 * The exported Objective-C surface is what Swift consumers actually see, and it is easy to regress
 * without noticing: Kotlin default arguments do not survive interop, `Flow` and `suspend` map in
 * specific ways, and enum entries are renamed. Compiling real Swift is the only way to catch that.
 *
 * Linking rather than only type-checking matters because the framework is static. A type-check
 * resolves the interface alone and cannot see an unresolved symbol from a transitive dependency
 * (CryptoKit, Ktor's Darwin engine, DataStore), which is exactly what would break a consumer.
 *
 * It links the framework produced by the target directly rather than a slice of the XCFramework:
 * the slice directory is named after the architectures it contains, so adding a target would
 * silently rename it, and `check` does not need a packaged XCFramework.
 *
 * Registered only on macOS. `kotlin.native.ignoreDisabledTargets` skips the compile tasks
 * elsewhere, but a `dependsOn` on one of them would still have to resolve.
 */
if (HostManager.hostIsMac) {
    val swiftApiSmokeTest =
        tasks.register<Exec>("swiftApiSmokeTest") {
            group = "verification"
            description = "Compiles and links the Swift API sample against the release framework."

            val linkTask = "linkReleaseFrameworkIosSimulatorArm64"
            dependsOn(linkTask)

            val frameworkDir = layout.buildDirectory.dir("bin/iosSimulatorArm64/releaseFramework")
            val source = layout.projectDirectory.file("src/swiftApiTest/SwiftApiSmokeTest.swift")
            val binary = layout.buildDirectory.file("swiftApiTest/SwiftApiSmokeTest")

            inputs.file(source)
            inputs.dir(frameworkDir)
            outputs.file(binary)

            commandLine(
                "xcrun",
                "--sdk",
                "iphonesimulator",
                "swiftc",
                "-target",
                "arm64-apple-ios$iosDeploymentTarget-simulator",
                "-F",
                frameworkDir.get().asFile.absolutePath,
                "-framework",
                frameworkName,
                "-o",
                binary.get().asFile.absolutePath,
                source.asFile.absolutePath,
            )

            doFirst { binary.get().asFile.parentFile.mkdirs() }
        }

    tasks.named("check") { dependsOn(swiftApiSmokeTest) }
}

/**
 * Assembles the XCFramework that is published to Swift Package Manager.
 *
 * SPM pins the archive by checksum, so the same source must always produce the same bytes. The
 * slice binaries already are identical between builds, but `xcodebuild -create-xcframework` writes
 * `AvailableLibraries` in a non-deterministic order, which alone changes the checksum. It ignores
 * argument order, so the entries are sorted afterwards. `sort_keys` also fixes the key order within
 * each entry.
 *
 * The Kotlin plugin's `assembleXCFramework` is not used here because its output cannot be
 * normalised without rewriting a file the plugin owns.
 */
val createSwiftXCFramework =
    if (HostManager.hostIsMac) {
        tasks.register<Exec>("createSwiftXCFramework") {
            group = "publishing"
            description = "Assembles a reproducible $frameworkName XCFramework."

            val slices =
                listOf("IosArm64" to "iosArm64", "IosSimulatorArm64" to "iosSimulatorArm64")
            slices.forEach { (taskSuffix, _) -> dependsOn("linkReleaseFramework$taskSuffix") }

            val frameworks =
                slices.map { (_, targetDir) ->
                    layout.buildDirectory.dir(
                        "bin/$targetDir/releaseFramework/$frameworkName.framework"
                    )
                }
            val output = layout.buildDirectory.dir("swift/$frameworkName.xcframework")

            frameworks.forEach { inputs.dir(it) }
            outputs.dir(output)

            val outputPath = output.get().asFile.absolutePath
            val frameworkArgs =
                frameworks.joinToString(" ") { "-framework '${it.get().asFile.absolutePath}'" }

            commandLine(
                "bash",
                "-euo",
                "pipefail",
                "-c",
                """
                rm -rf '$outputPath'
                xcrun xcodebuild -create-xcframework $frameworkArgs -output '$outputPath'
                python3 - '$outputPath/Info.plist' <<'PY'
                import plistlib, sys
                path = sys.argv[1]
                with open(path, 'rb') as f:
                    plist = plistlib.load(f)
                plist['AvailableLibraries'].sort(key=lambda library: library['LibraryIdentifier'])
                with open(path, 'wb') as f:
                    plistlib.dump(plist, f, sort_keys=True)
                PY
                """
                    .trimIndent(),
            )
        }
    } else {
        null
    }

/**
 * Archives the XCFramework for Swift Package Manager and prints its checksum.
 *
 * SPM expects the SHA-256 of the archive, which is what `swift package compute-checksum` returns.
 * The release workflow stamps this checksum into `Package.swift` before the tag is created, so the
 * archive must be byte-identical every time it is built from the same source.
 */
if (createSwiftXCFramework != null) {
    tasks.register<Zip>("packageSwiftArtifact") {
        group = "publishing"
        description = "Archives the $frameworkName XCFramework for Swift Package Manager."

        dependsOn(createSwiftXCFramework)

        from(layout.buildDirectory.dir("swift")) { include("$frameworkName.xcframework/**") }
        archiveFileName = "$frameworkName.xcframework.zip"
        destinationDirectory = layout.buildDirectory.dir("swift/archive")

        // SPM pins the archive by checksum, so the same inputs must always produce the same bytes.
        // Without these, entry order and file timestamps vary between builds.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        val archive = destinationDirectory.file("$frameworkName.xcframework.zip")
        doLast {
            val bytes = archive.get().asFile.readBytes()
            val checksum =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
                    "%02x".format(byte)
                }
            logger.lifecycle("Archive:  ${archive.get().asFile}")
            logger.lifecycle("Checksum: $checksum")
        }
    }
}
