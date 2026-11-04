# Frequently Asked Questions

## Security

### ID token signature validation

> Why doesn't Lokksmith verify the signature of ID tokens?

Lokksmith receives ID tokens only from the token endpoint, after the authorization code exchange, a
token refresh or an [extension grant](extension-grants.md). These are direct requests from the
client to the provider over TLS. In this case
[OpenID Connect Core 1.0, Section 3.1.3.7](https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation),
step 6, allows TLS server validation to replace the signature check:

> If the ID Token is received via direct communication between the Client and the Token Endpoint
> (which it is in this flow), the TLS server validation MAY be used to validate the issuer in place
> of checking the token signature.

Lokksmith still validates the `iss`, `aud`, `azp`, `nonce`, `auth_time`, `iat`, `exp` and `nbf`
claims. Lokksmith never accepts ID tokens from the redirect (front channel), where the signature
would have to be checked.

Checking the signature in the client would add little. A compromised provider owns the signing keys,
so it can sign whatever claims it wants. An attacker who can break TLS to the token endpoint can
also replace the JSON Web Key Set, which is fetched from the same provider.

!!! warning "Backends must validate tokens themselves"
    Lokksmith runs in public clients (mobile, desktop and browser apps), and any code running on the
    user's device can be tampered with. A backend must never trust identity claims just because the
    app accepted them. Validate access tokens, or ID tokens sent to your backend, on the server.

## Android

### Temporal validation exceptions in emulator

> I often get temporal validation exceptions in an Android emulator

Android emulators are prone to [clock drift](https://en.wikipedia.org/wiki/Clock_drift), especially
after running for extended periods and when regularly suspended and resumed. To work around
this, there are several solutions:

- Perform a cold boot of the emulator
- Toggle the "Automatic date and time" setting off and on
- Increase the `leewaySeconds` option of the client

### Auth Tab on Motorola devices

> Authentication via [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab)
> does not work on Motorola devices

Newer Motorola devices ship with the Moto Secure app, which includes phishing protection and other
security features. Unfortunately, Moto Secure can interfere with authentication via Auth Tab. From
Lokksmith’s perspective, the authentication flow is cancelled when Moto Secure is involved. As a 
workaround, avoid using Auth Tab on Motorola devices. You can detect Motorola devices with the 
following code

```kotlin
val isMotorolaDevice = Build.MANUFACTURER.lowercase() == "motorola"
```
