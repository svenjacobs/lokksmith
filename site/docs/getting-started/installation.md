# Installation

Lokksmith is distributed via Maven Central. We recommend using [Gradle Version Catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html)
for dependency management.

!!! info
    Building a **native iOS app** in Swift, without Kotlin? Skip to
    [Native iOS (Swift Package Manager)](#native-ios-swift-package-manager). The rest of this page
    covers the Gradle setup for Kotlin Multiplatform projects.

## Add Lokksmith to Version Catalog

Add the current version of Lokksmith to your `gradle/libs.versions.toml`:

```toml title="gradle/libs.versions.toml"
[versions]
lokksmith = "{{ lokksmith_version }}"

[libraries]
lokksmith-core = { module = "dev.lokksmith:lokksmith-core", version.ref = "lokksmith" }
```

### Snapshot version

If you want to use a [snapshot version](https://maven.apache.org/guides/getting-started/#What_is_a_SNAPSHOT_version.3F)
of Lokksmith, add the following configuration to the `dependencyResolutionManagement.repositories`
node in your root `settings.gradle.kts` or `repositories` in the module's `build.gradle.kts`:

```kotlin
maven {
    setUrl("https://central.sonatype.com/repository/maven-snapshots/")
    content { includeGroup("dev.lokksmith") }
}
```

!!! tip
    You'll find the newest snapshot version [here](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/dev/lokksmith/).

## Add Lokksmith to Project Dependencies

In your `build.gradle.kts`, add Lokksmith to the appropriate source set:

```title="build.gradle.kts"
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lokksmith.core)
        }
    }
}
```

## Compose

Lokksmith provides an additional artifact, `lokksmith-compose`, for seamless integration with Jetpack
Compose and Compose Multiplatform for Android, iOS, Desktop and Web.

### Add the Compose Artifact

```toml title="gradle/libs.versions.toml"
[libraries]
lokksmith-compose = { module = "dev.lokksmith:lokksmith-compose", version.ref = "lokksmith" }
```

### Add Compose Dependency to Source Set

```title="build.gradle.kts"
commonMain.dependencies {
    implementation(libs.lokksmith.compose)
}
```

## Android

### Specify Redirect Scheme

To allow Lokksmith's `Activity` to receive OAuth responses, specify your app's redirect scheme in
`build.gradle.kts`. Use only the scheme part (e.g., `my-app` for `my-app://openid-response`):

```kotlin title="build.gradle.kts"
android {
    defaultConfig {
        addManifestPlaceholders(
            mapOf("lokksmithRedirectScheme" to "my-app") // (1)!
        )
    }
}
```

1. Replace `my-app` with your own scheme

### Optional: Using App Links for Redirection

To enhance security, it is recommended to use verified [App Links](https://developer.android.com/training/app-links/)
for handling OIDC redirects into your app. Lokksmith cannot automatically add the required manifest
entry for App Links, so you must manually update your `AndroidManifest.xml` as follows:

```xml title="AndroidManifest.xml"
<activity
        android:name="dev.lokksmith.android.LokksmithRedirectActivity"
        android:exported="true">
    <intent-filter tools:node="removeAll" /> <!-- (1)! -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        
        <data 
            android:scheme="https"
            android:host="example.com"
            android:path="/redirect" /> <!-- (2)! -->
    </intent-filter>
</activity>
```

1. Optional: Removes any existing intent filters added by Lokksmith
2. Update host and path to match your redirect URI

<h4>Note</h4>

- Replace `example.com` and `/redirect` with the actual host and path used in your OIDC redirect URI.
- Ensure your website is properly configured for [App Links verification](https://developer.android.com/training/app-links/verify-android-applinks)
  by serving the Digital Asset Links JSON file at `https://example.com/.well-known/assetlinks.json`.
- This configuration ensures only your app can handle the redirect, improving security against malicious interception.

### R8 / ProGuard

Lokksmith uses Kotlin Serialization internally and depends on the ProGuard configuration
[provided by the library](https://github.com/Kotlin/kotlinx.serialization?tab=readme-ov-file#android).
Usually this configuration is applied automatically. However, if you manually configure ProGuard
you must ensure to apply the Kotlin Serialization rules or else Lokksmith will fail at
(de)serialization.

## Native iOS (Swift Package Manager)

Native iOS apps consume Lokksmith as a binary Swift package. The package ships an XCFramework built
from the `lokksmith-swift` module, which exposes a Swift-facing API over `lokksmith-core`. No Kotlin
toolchain or Gradle build is involved.

In Xcode, choose **File → Add Package Dependencies…** and enter:

```
https://github.com/svenjacobs/lokksmith
```

Or add it to your own `Package.swift`:

```swift title="Package.swift"
dependencies: [
    .package(url: "https://github.com/svenjacobs/lokksmith", from: "{{ lokksmith_version }}")
],
targets: [
    .target(
        name: "MyApp",
        dependencies: [.product(name: "Lokksmith", package: "lokksmith")]
    )
]
```

Requires iOS 15 or later. The framework is static, so nothing needs to be embedded or signed.

### Register the Redirect Scheme

The redirect URI's scheme must be registered by your app, so that
[`ASWebAuthenticationSession`](https://developer.apple.com/documentation/authenticationservices/aswebauthenticationsession)
can hand the response back. Add it to your `Info.plist`:

```xml title="Info.plist"
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>my-app</string>
        </array>
    </dict>
</array>
```

Lokksmith derives the callback scheme from the `redirectUri` you pass to the request, so no further
configuration is needed. You do not need to handle the redirect in `onOpenURL` or
`application(_:open:options:)` — `ASWebAuthenticationSession` delivers it directly.

Continue with [Usage → iOS](usage.md#ios).

*[OIDC]: OpenID Connect
