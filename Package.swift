// swift-tools-version:5.9
import PackageDescription

// Lokksmith for native iOS apps.
//
// This package distributes the `Lokksmith` XCFramework produced by the `lokksmith-swift` Gradle
// module. Kotlin Multiplatform and Compose Multiplatform applications should depend on
// `dev.lokksmith:lokksmith-core` through Gradle instead.
//
// The `url` and `checksum` below are updated per release. Run
// `./gradlew :lokksmith-swift:packageSwiftArtifact` inside `lib` to produce the archive and print
// the checksum for a release.
let package = Package(
    name: "Lokksmith",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "Lokksmith",
            targets: ["Lokksmith"]
        )
    ],
    targets: [
        .binaryTarget(
            name: "Lokksmith",
            url: "https://github.com/svenjacobs/lokksmith/releases/download/v1.1.5/Lokksmith.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        )
    ]
)
