# Encryption

Lokksmith encrypts its persisted state at rest. Everything it stores for a client — access and
refresh tokens, nonces and in-flight auth-flow state — is encrypted before it is written to disk (or
`localStorage` on the Web), so tokens are never persisted in clear text.

This is on by default and requires no configuration:

```kotlin
val lokksmith = createLokksmith()
```

The state is encrypted with AES-256-GCM, and the key protecting it is kept in the platform's secure
storage: the Android Keystore, the iOS Keychain, an owner-only key file on Desktop, and
`localStorage` on the Web. How strong that protection is therefore differs per platform — on Android
it is hardware-backed where the device supports it, while on Desktop it comes down to the operating
system's file permissions.

!!! warning "Web"
    On the Web the key is stored in `localStorage`, which is readable by any script on the same
    origin. Encryption there therefore provides obfuscation rather than strong protection and does
    **not** defend against cross-site scripting (XSS): a script that can read the encrypted state can
    also read the key. Apply a strong Content Security Policy and the usual XSS defenses. Persisting
    a non-extractable [WebCrypto](https://developer.mozilla.org/docs/Web/API/Web_Crypto_API) key in
    IndexedDB is the intended future hardening.

!!! note "Upgrading from an older version"
    State previously written as plaintext is migrated automatically on first access. There is no
    manual step and users stay signed in.

## Disabling encryption

Set `encryptionEnabled = false` in the options passed to `createLokksmith(...)`. State is then stored
as plaintext JSON and no platform key material is created:

```kotlin
val lokksmith = createLokksmith(
    options = Lokksmith.Options(encryptionEnabled = false),
)
```

!!! warning "Turning encryption off discards existing state"
    Encrypted state cannot be read in plaintext mode. It is treated as absent, so the affected
    client is re-created and the user has to authenticate again. Turning encryption *on* is a
    migration and does not lose state; turning it off is not.

## Android: excluding the files from backups

On Android, Lokksmith keeps two files in the application's `filesDir`:

| File | Contents |
|------|----------|
| `lokksmith_clients.preferences_pb` | the encrypted client state |
| `lokksmith_clients.key.preferences_pb` | the encrypted data-encryption key |

Both are covered by [Auto Backup](https://developer.android.com/identity/data/autobackup) unless you
exclude them. The key that protects them lives in the Android Keystore and is non-exportable, so it
is never part of a backup: on a new device the restored files can never be decrypted. Lokksmith
handles that gracefully — unreadable state is treated as absent, the client is re-created and the
user authenticates again — but backing the files up in the first place serves no purpose.

To exclude them, add two resource files:

```xml title="res/xml/backup_rules.xml"
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="lokksmith_clients.preferences_pb" />
    <exclude domain="file" path="lokksmith_clients.key.preferences_pb" />
</full-backup-content>
```

```xml title="res/xml/data_extraction_rules.xml"
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="lokksmith_clients.preferences_pb" />
        <exclude domain="file" path="lokksmith_clients.key.preferences_pb" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="lokksmith_clients.preferences_pb" />
        <exclude domain="file" path="lokksmith_clients.key.preferences_pb" />
    </device-transfer>
</data-extraction-rules>
```

and reference them from your application's manifest:

```xml title="AndroidManifest.xml"
<application
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules">
```

Both attributes are needed: `fullBackupContent` applies up to Android 11 (API 30),
`dataExtractionRules` from Android 12 (API 31) on. Excluding `device-transfer` as well costs
nothing, since the Keystore key does not transfer to the new device either.

!!! note
    Lokksmith cannot ship these rules itself: both are single-valued `<application>` attributes that
    point at exactly one file for the whole application, so a library declaring them would either
    override your own backup rules or break the manifest merge.

    If you changed `Lokksmith.Options.persistenceFileBaseName`, adjust the paths accordingly — they
    are `<base-name>.preferences_pb` and `<base-name>.key.preferences_pb`.
