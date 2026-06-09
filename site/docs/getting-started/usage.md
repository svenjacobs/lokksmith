# Usage

The main entry point is the `Lokksmith` class, which manages and persists authentication clients and
their state across your application. To obtain an instance, use the platform-specific
`createLokksmith()` function. The function has an optional `options` argument for tweaking the
behaviour of Lokksmith. Please read the source code documentation for details.

!!! note
    It is recommended to create a single shared `Lokksmith` instance, ideally provided via
    dependency injection.

```kotlin
val lokksmith = createLokksmith()
```

!!! info
    The factory function differs slightly per platform:

    - On **Android** it also requires the current `Context`.
    - On **Desktop** it requires a `dataDirectory` specifying where Lokksmith stores its data, for
      example `createLokksmith(dataDirectory = DataDirectory.Default("my-app"))`.
    - On **Web** it optionally accepts `handleRedirectOnStartup` (default `true`) to automatically
      complete an auth flow after the redirect; see [Web](#web).

Use `getOrCreate()` to retrieve an existing client by its unique key, or create a new one if it does
not exist. This key is independent of the OAuth client ID, but you may use the same value if it
suits your use case. Defining distinct keys allows you to manage multiple authentication clients
within your application.

```kotlin
val client = lokksmith.getOrCreate("my-key") {
    id = "my-client-id"
    discoveryUrl = "https://example.com/.well-known/openid-configuration"
}
```

!!! note
    Many functions of Lokksmith are suspending and must be run in a [Coroutine](https://kotlinlang.org/docs/coroutines-overview.html),
    like `getOrCreate()` in this case.

Once you have a client instance, it is time to call the Authorization Code Flow to obtain the tokens.

```kotlin
val authFlow = client.authorizationCodeFlow(
    AuthorizationCodeFlow.Request( // (1)!
        redirectUri = "my-app://openid-response"
    )
)

val initiation = authFlow.prepare()
```

1. See code documentation of `AuthorizationCodeFlow.Request` for more details.

The next step is to call the request URL from the `Initiation` object and pass the returned response
to the auth flow.

```kotlin
// Open system browser with initiation.requestUrl, pass response to auth flow

authFlow.onResponse(response)
```

!!! note
    See platform-specific implementation details below.

In a best case scenario the client is now authenticated and received the tokens.

You can now access the tokens through `client.tokens`, which is a Coroutine [Flow](https://kotlinlang.org/docs/flow.html),
or `client.runWithTokens()`.

!!! note
    The `tokens` Flow does not automatically refresh tokens when they expire.
    Use `runWithTokens()` to ensure fresh tokens when required.

## Singleton

A singleton `Lokksmith` instance must be provided during application startup as soon as possible via 
`SingletonLokksmithProvider`. This provider ensures that platform-specific response handling, which
is decoupled from the initiation of an auth flow and is launched by the system implicitly, is able 
to retrieve the `Lokksmith` instance. On Android this should be executed in the `Application`
class, for example.

```kotlin
SingletonLokksmithProvider.set(
    lokksmith = createLokksmith(),
    coroutineScope = MainScope(),
)
```

## Platform implementations

Calling the system browser and handling the authentication response on mobile platforms involves
multiple steps, including managing process death and app recreation. To ensure a seamless user
experience, it is essential to persist and restore authentication state as needed. Lokksmith
provides platform-specific implementations that abstract these complexities, making it easier to
integrate secure authentication flows in your application.

### Compose Multiplatform

The `AuthFlowLauncher` shown here works on Android, iOS, Desktop and Web.

Once you receive the `Initiation` object, use `AuthFlowLauncher` to start the authentication flow
from your Composable. For example:

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle() // (1)!
val authFlowLauncher = rememberAuthFlowLauncher()

LaunchedEffect(uiState.initiation) {
    uiState.initiation?.let { initiation ->
        authFlowLauncher.launch(initiation)    
    }
}
```

1. `data class UiState(val initiation: Initiation? = null)`

!!! tip
    `launch` accepts an optional `options` argument that allows you to customize Lokksmith's
    behavior. For example, on Android, you can choose between authentication using a Custom Tab or
    an [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab).

You can either use `authFlowLauncher.result` to observe the current state of the process and update
the user interface accordingly or use `Client.authFlowResult`(1) from your business logic
(e.g. `ViewModel`) to pass the same result state to your UI state.
{ .annotate }

1. See `AuthFlowResultProvider`

### iOS

The iOS integration is currently usable from a Kotlin Multiplatform or Compose Multiplatform
application. A dedicated Swift package for use in a native iOS app is still pending.

To launch an authentication flow from the iOS platform code of a Kotlin Multiplatform application,
use `launchAuthFlow()`:

```kotlin
lokksmith.launchAuthFlow(initiation)
```

### Desktop

On Desktop, Lokksmith implements the loopback redirect described in [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252)
("OAuth 2.0 for Native Apps"). When you prepare an auth flow, Lokksmith starts a temporary HTTP
server bound to `127.0.0.1` on an ephemeral port and uses `http://127.0.0.1:<port>/callback` as the
redirect URI. The `redirectUri` you pass to `AuthorizationCodeFlow.Request` (or
`EndSessionFlow.Request`) is therefore **ignored and replaced** by this loopback URL.

!!! warning
    Because the port is chosen at runtime, your OpenID provider must allow loopback redirect URIs
    (`http://127.0.0.1` / `http://localhost`) with an arbitrary port, as recommended by RFC 8252. No
    custom URI scheme or manifest configuration is required on Desktop.

Use the Compose `rememberAuthFlowLauncher()` exactly as described under
[Compose Multiplatform](#compose-multiplatform). It opens the system browser, waits for the redirect
on the loopback server, and completes the flow.

The Desktop behaviour can be customized via `DesktopOptions` when creating the instance:

```kotlin
val lokksmith = createLokksmith(
    dataDirectory = DataDirectory.Default("my-app"),
    desktop = DesktopOptions(
        redirectPath = "/callback",  // (1)!
        redirectTimeout = 5.minutes, // (2)!
        // browserLauncher, authorizationResponseHtml, endSessionResponseHtml, ...
    ),
)
```

1. Path of the loopback redirect URI.
2. How long to wait for the redirect before the flow times out.

!!! tip
    See the [Demo](demo.md) for a complete, runnable Desktop example, including how to test the flow
    against a local OpenID provider.

### Web

On the Web (Kotlin/Wasm in the browser) an auth flow is completed via a **full-page redirect**: the
browser navigates to the OpenID provider and is redirected back to a **same-origin** URL, which
reloads and restarts the whole application. The `redirectUri` you pass to
`AuthorizationCodeFlow.Request` (or `EndSessionFlow.Request`) must therefore be a URL on your app's
own origin, for example `https://myapp.example/`. Custom URI schemes do not work in the browser.

Because the redirect restarts the app, there is no in-memory state to resume from. Lokksmith reads
the response from the current URL on startup for you: by default `createLokksmith()` completes a
pending flow automatically (controlled by the `handleRedirectOnStartup` argument). The result is then
observed through common code via `Client.authFlowResult` or `client.tokens`, exactly as on the other
platforms.

Use the Compose `rememberAuthFlowLauncher()` exactly as described under
[Compose Multiplatform](#compose-multiplatform) to start the flow. From non-Compose code you can
launch it directly, which navigates the current document to the request URL:

```kotlin
lokksmith.launchAuthFlow(initiation)
```

!!! info "Manual redirect handling"
    To control when the redirect is processed, create the instance with
    `createLokksmith(handleRedirectOnStartup = false)` and call
    `lokksmith.completeAuthFlowFromRedirect()` yourself, for example during application startup.

!!! warning "Security"
    On the Web, Lokksmith persists its state — including tokens — in the browser's `localStorage`,
    which is **not encrypted** and is readable by any script on the same origin. A cross-site
    scripting (XSS) vulnerability can therefore expose tokens. Apply a strong Content Security Policy
    and the usual XSS defenses.

!!! note
    `rememberAuthFlowLauncher().result` is not restored after the full-page reload. Observe
    `Client.authFlowResult` (for example from your `ViewModel`) to react to the completed result.
