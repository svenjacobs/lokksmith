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
    On Android the factory function also requires the current `Context`.

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
