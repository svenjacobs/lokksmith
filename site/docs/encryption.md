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

AES-GCM is authenticated encryption, so tampering with a stored snapshot — or an attempt to decrypt
it with the wrong key — is detected and rejected. Each snapshot is additionally bound to the client
it belongs to, so a stored value cannot be moved from one client to another.

!!! note "What integrity does and does not cover"
    Detection is per snapshot, not across time: replacing a snapshot with an *earlier* ciphertext of
    that same client is indistinguishable from the current one and is accepted. Guarding against
    that would require a monotonic counter in secure storage, which Lokksmith does not keep. Expired
    tokens are still rejected by the authorization server, so the practical effect is limited to
    restoring superseded state.

## Platform guarantees

Where and how strongly the KEK is protected depends on the platform:

| Platform | KEK storage | Guarantee |
|----------|-------------|-----------|
| Android | [Android Keystore](https://developer.android.com/privacy-and-security/keystore), non-exportable AES key | Hardware-backed where the device supports it; the raw KEK never enters the app process |
| iOS | [Keychain](https://developer.apple.com/documentation/security/keychain-services), device-scoped (available after first unlock) | KEK confined to the Keychain on this device |
| Desktop (JVM) | Key file in the user-private data directory | No hardware isolation; protection equals the operating system's file permissions. Restricted to the owner where POSIX permissions apply; on Windows the key file inherits the data directory's access control |
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

!!! warning "Turning encryption off discards existing state"
    Encrypted state cannot be read in plaintext mode. It is treated as absent, so the affected
    client is re-created and the user re-authenticates. See
    [Migration](#migration) for the cases that *are* handled automatically.

## Migration

**Upgrading from a version without encryption.** Existing plaintext state is migrated
automatically. It is readable immediately after the upgrade and is encrypted on first access,
without any manual step or data reset.

**Turning encryption on** (`false` → `true`) behaves the same way: state previously written as
plaintext is picked up and encrypted.

**Turning encryption off** (`true` → `false`) is not a migration. Existing encrypted state cannot be
read in plaintext mode and is treated as absent, so the affected client is re-created and the user
re-authenticates.

The conversion is a single pass over the store the first time Lokksmith reads or writes it, covering
every client rather than only the one being accessed. It is recorded once it succeeds; afterwards a
value that cannot be decrypted is treated as absent rather than read as plaintext. If the platform
secure store happens to be unavailable, the pass is retried on a later access instead of being
recorded, so nothing is lost in the meantime.

!!! note "What migration does not do"
    Converting the store rewrites the storage file; it does not scrub the previous plaintext from the
    underlying medium. Copies may persist in filesystem slack space, journals, or backups taken
    before the upgrade. Encryption at rest protects state written from this version onwards.

## Transient failures

!!! note
    When the platform secure store is temporarily unavailable — the Android Keystore during
    direct-boot, the iOS Keychain before first unlock — reading a snapshot fails with an exception
    rather than reporting that no snapshot exists. `SnapshotStore.exists` and `SnapshotStore.observe`
    propagate it.

    This is deliberate. Reporting "not signed in" would lead an application to start a fresh
    authorization flow and overwrite a snapshot that is perfectly valid. Treat such an error as
    "unknown, try again", not as "signed out". Only genuine key loss makes a snapshot absent — see
    [Key loss](#key-loss).

!!! warning "Passing your own coroutine scope"
    Such a failure can also occur while a snapshot is being observed, where there is no caller to
    return it to. The default `Lokksmith.Options.coroutineScope` installs a
    `CoroutineExceptionHandler` for exactly this case. **If you supply your own scope, install a
    handler on it** — otherwise the error reaches the platform's default handler, which on Android
    terminates the application.

## Key loss

!!! note
    If the KEK becomes unavailable — for example the platform secure store is cleared, the app's
    data is wiped, or `localStorage` / the Keychain is reset — snapshots encrypted under it can no
    longer be decrypted. Lokksmith treats such a snapshot as absent rather than failing: the
    affected client is simply re-created and the user re-authenticates. The application does not
    crash.
