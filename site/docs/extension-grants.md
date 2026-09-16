# Extension Grants

Some providers offer custom OAuth 2.0 grant types beyond the Authorization Code Flow, for example to
exchange a credential issued by another system for tokens. Lokksmith supports these through
`Client.requestTokens()`, which implements an
[OAuth 2.0 extension grant](https://www.rfc-editor.org/rfc/rfc6749#section-4.5): a form POST to the
client's token endpoint with a caller-supplied `grant_type` and caller-supplied parameters.

```kotlin
val tokens = client.requestTokens(
    grantType = "urn:example:params:oauth:grant-type:restore-credential",
    parameters = mapOf(
        "credential" to storedCredential,
        "scope" to "openid",
    ),
)
```

Lokksmith adds `grant_type` and `client_id` to the request itself and appends the client's configured
`Options.additionalTokenRequestParameters`. On success, the returned tokens **replace** any tokens
the client currently holds and are persisted exactly as after a regular login, so `client.tokens`,
`client.refresh()` and `client.runWithTokens { }` all keep working afterwards without any special
handling for how the client was authenticated.

!!! note "The provider must return an ID token"
    `Client.Tokens.idToken` is non-nullable, and an extension grant has no previous ID token to fall
    back on, so a response without one fails with `TokenValidationException`. In practice this means
    you usually need to pass `scope=openid` in `parameters`, since most providers only issue an ID
    token when the `openid` scope is requested.

The ID token is validated the same way as after the Authorization Code Flow: `iss`, `aud`, `azp` and
the `iat`/`exp`/`nbf` claims are checked against the client's configured leeway. The `nonce` claim is
not compared against anything, since an extension grant has no preceding authorization request that
could have carried one, but it is remembered so that a later refresh response echoing it still
validates. `expires_in` is converted to an absolute expiry using the client's configured time
provider, so `isExpired` and `runWithTokens` stay consistent with tokens obtained any other way.

## Parameters

Unlike `additionalTokenRequestParameters`, `parameters` is not screened against known OAuth/OIDC
parameter names, since extension grants legitimately need to send things like `scope`. However, form
parameters are a multimap, so a `parameters` entry that collides with `grant_type`, `client_id`, or a
key already present in `additionalTokenRequestParameters` throws `IllegalArgumentException` rather
than silently sending both values.

## Errors

`requestTokens()` can throw the same exceptions as the rest of the token endpoint API:

- `OAuthResponseException` — the provider returned an OAuth error response, for example
  `invalid_grant`.
- `ResponseException` — the response was malformed or the token type was not `Bearer`.
- `RequestException` — the request failed at the transport level.
- `TokenValidationException` — the response passed transport and OAuth checks but the tokens (most
  commonly the ID token) failed validation.
- `IllegalArgumentException` — a parameter in `parameters` collides with `grant_type`, `client_id`,
  or an entry in `additionalTokenRequestParameters`.
