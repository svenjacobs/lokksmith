// swift-tools-version:5.9
import PackageDescription

// Lokksmith for native iOS apps.
//
// Kotlin Multiplatform and Compose Multiplatform applications depend on `dev.lokksmith:lokksmith-core`
// through Gradle instead.
//
// Both values below are maintained by automation and should not be edited by hand:
//   - the version in `url` is updated by release-please,
//   - `checksum` is stamped by .github/workflows/swift-package.yml while the release PR is open,
//     so that it is already correct at the tag SPM resolves.
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
            url: "https://github.com/svenjacobs/lokksmith/releases/download/v2.6.1/Lokksmith.xcframework.zip", // x-release-please-version
            checksum: "9679276c7bea98ab09746ba7c62abaaa88eb08f7d2e5e178c331c1d322d94d3f"
        )
    ]
)
