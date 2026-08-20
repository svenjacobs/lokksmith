import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
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

    val xcframework = XCFramework(frameworkName)

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = frameworkName
            isStatic = true
            xcframework.add(this)
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
    }
}

/**
 * Type-checks `src/swiftApiTest` against the assembled XCFramework.
 *
 * The exported Objective-C surface is what Swift consumers actually see, and it is easy to regress
 * without noticing: Kotlin default arguments do not survive interop, `Flow` and `suspend` map in
 * specific ways, and enum entries are renamed. Compiling real Swift is the only way to catch that.
 */
val swiftApiSmokeTest =
    tasks.register<Exec>("swiftApiSmokeTest") {
        group = "verification"
        description = "Type-checks the Swift API sample against the assembled XCFramework."

        onlyIf { HostManager.hostIsMac }
        dependsOn("assemble${frameworkName}ReleaseXCFramework")

        val simulatorFramework =
            layout.buildDirectory.dir(
                "XCFrameworks/release/$frameworkName.xcframework/ios-arm64-simulator"
            )
        val source = layout.projectDirectory.file("src/swiftApiTest/SwiftApiSmokeTest.swift")

        inputs.file(source)
        inputs.dir(simulatorFramework)

        commandLine(
            "xcrun",
            "--sdk",
            "iphonesimulator",
            "swiftc",
            "-target",
            "arm64-apple-ios$iosDeploymentTarget-simulator",
            "-F",
            simulatorFramework.get().asFile.absolutePath,
            "-typecheck",
            source.asFile.absolutePath,
        )
    }

tasks.named("check") { dependsOn(swiftApiSmokeTest) }

/**
 * Produces the archive referenced by the root `Package.swift` and prints its checksum.
 *
 * Swift Package Manager expects the SHA-256 of the archive, which is what
 * `swift package compute-checksum` returns.
 */
tasks.register<Zip>("packageSwiftArtifact") {
    group = "publishing"
    description = "Archives the $frameworkName XCFramework for Swift Package Manager."

    dependsOn("assemble${frameworkName}ReleaseXCFramework")

    from(layout.buildDirectory.dir("XCFrameworks/release"))
    archiveFileName = "$frameworkName.xcframework.zip"
    destinationDirectory = layout.buildDirectory.dir("swift")

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
