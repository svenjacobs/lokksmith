# Installation

Lokksmith is distributed via Maven Central. We recommend using [Gradle Version Catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html)
for dependency management.

## Add Lokksmith to Version Catalog

Add the current version of Lokksmith to your `gradle/libs.versions.toml`:

```toml title="gradle/libs.versions.toml"
[versions]
lokksmith = "0.1.0" # (1)!

[libraries]
lokksmith-core = { module = "dev.lokksmith:lokksmith-core", version.ref = "lokksmith" }
```

1. Replace with the latest version if available

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

## Android

Lokksmith provides an additional artifact, `lokksmith-android`, for seamless integration with Android
and Jetpack Compose. It includes an `Activity` for handling OAuth responses and an `AuthFlowLauncher`
for launching authentication flows from Compose.

### Add the Android Artifact

```toml title="gradle/libs.versions.toml"
[libraries]
lokksmith-android = { module = "dev.lokksmith:lokksmith-android", version.ref = "lokksmith" }
```

### Add Android Dependency to Source Set

```title="build.gradle.kts"
androidMain.dependencies {
    implementation(libs.lokksmith.android)
}
```

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

#### Note

- Replace `example.com` and `/redirect` with the actual host and path used in your OIDC redirect URI.
- Ensure your website is properly configured for [App Links verification](https://developer.android.com/training/app-links/verify-android-applinks)
  by serving the Digital Asset Links JSON file at `https://example.com/.well-known/assetlinks.json`.
- This configuration ensures only your app can handle the redirect, improving security against malicious interception.

## iOS

The iOS integration is not yet available.  
Contributions are welcome!

*[OIDC]: OpenID Connect
