# Migration from other libraries

Lokksmith provides the `Migration` utility class for migrating tokens from another OIDC library,
enabling seamless user migration without requiring re-authentication.

!!! warning
    The migration process should only be performed **once** for every client. It should not be used 
    for regular token management or refresh operations.

```kotlin
val lokksmith = createLokksmith()
val client = lokksmith.get("client-key")

// Replace values with actual tokens and expiration timestamps
// from other library.

lokksmith.migration.setTokens(
    client = client,
    accessToken = "ACCESS TOKEN",
    accessTokenExpiresAt = 1749471081,
    refreshToken = "REFRESH TOKEN",
    refreshTokenExpiresAt = 1752063081,
    idToken = "ID TOKEN",
)
```
