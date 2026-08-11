# Encryption

Lokksmith encrypts its persisted state at rest. The snapshot that Lokksmith stores for each
client — including access and refresh tokens, nonces and in-flight auth-flow state — is
encrypted before it is written to disk (or `localStorage` on the Web), so by default tokens are
never persisted in clear text.

This is on by default and requires no configuration. It can be turned off — see
[Disabling encryption](#disabling-encryption).

## How it works

Lokksmith uses **envelope encryption**, backed by AES-256-GCM from the
[cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin) library:

- The full serialized snapshot is encrypted with a random, store-level **data-encryption key
  (DEK)**.
- The DEK is itself encrypted ("wrapped") with a **key-encryption key (KEK)** that is held in
  platform secure storage. Only the *wrapped* DEK is persisted, alongside the encrypted
  snapshot; the DEK only ever exists in memory after being unwrapped.

AES-GCM is authenticated encryption, so any tampering with the stored ciphertext — or an
attempt to decrypt it with the wrong key — is detected and rejected.

## Platform guarantees

Where and how strongly the KEK is protected depends on the platform:

| Platform | KEK storage | Guarantee |
|----------|-------------|-----------|
| Android | [Android Keystore](https://developer.android.com/privacy-and-security/keystore), non-exportable AES key | Hardware-backed where the device supports it; the raw KEK never enters the app process |
| iOS | [Keychain](https://developer.apple.com/documentation/security/keychain-services), device-scoped (available after first unlock) | KEK confined to the Keychain on this device |
| Desktop (JVM) | Key file in the user-private data directory (owner-only permissions) | No hardware isolation; protection equals the operating system's file permissions |
| Web (Wasm) | Browser `localStorage` | **Reduced** — obfuscation only (see below) |

!!! warning "Web"
    On the Web the KEK is stored in `localStorage`, which is readable by any script on the same
    origin. Encryption there therefore provides obfuscation rather than strong protection and
    does **not** defend against cross-site scripting (XSS): a script that can read the encrypted
    snapshot can also read the KEK. Apply a strong Content Security Policy and the usual XSS
    defenses. Persisting a non-extractable [WebCrypto](https://developer.mozilla.org/docs/Web/API/Web_Crypto_API)
    key in IndexedDB is the intended future hardening.

## Disabling encryption

Set `encryptionEnabled = false` in the options passed to `createLokksmith(...)`. Snapshots are then
stored as plaintext JSON and no platform key material is created:

```kotlin
val lokksmith = createLokksmith(
    options = Lokksmith.Options(encryptionEnabled = false),
)
```

The `options` argument is common to every platform's `createLokksmith(...)` — see
[Creating a Lokksmith instance](getting-started/usage.md). Depending on the platform you also pass
the other required arguments there (for example `dataDirectory` on Desktop, the `Context` on
Android).

!!! warning "Changing the setting is not a migration"
    `encryptionEnabled` is meant to be set once, before any state is persisted. Flipping it on an
    existing installation does not convert stored data: state written in the other mode is treated
    as absent, so the affected client is re-created and the user re-authenticates.

## Upgrading existing installations

!!! info "Transparent migration"
    Installations that persisted state before encryption was introduced are upgraded
    transparently. A plaintext snapshot is read as-is and re-encrypted the next time it is
    written. No manual migration or data reset is required.

## Key loss

!!! note
    If the KEK becomes unavailable — for example the platform secure store is cleared, the app's
    data is wiped, or `localStorage` / the Keychain is reset — snapshots encrypted under it can no
    longer be decrypted. Lokksmith treats such a snapshot as absent rather than failing: the
    affected client is simply re-created and the user re-authenticates. The application does not
    crash.
