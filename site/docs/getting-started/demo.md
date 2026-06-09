# Demo

Please have a look at our Compose Multiplatform [demo](https://github.com/svenjacobs/lokksmith/tree/main/demo)
application.

## Desktop

The demo also runs as a desktop app (`./gradlew :composeApp:run`). Because the Desktop target uses
an RFC 8252 loopback redirect (`http://127.0.0.1:<ephemeral-port>/callback`), you need an OIDC
provider that accepts arbitrary loopback redirect URIs. The demo ships a ready-to-use local
provider ([navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)); see the
[demo README](https://github.com/svenjacobs/lokksmith/tree/main/demo#testing-the-desktop-auth-flow-with-a-local-oidc-server)
for step-by-step instructions.

## Web

The demo also runs in the browser as a Kotlin/Wasm app
(`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`, served on `http://localhost:8081`). It can use
the same local provider as Desktop — the browser reaches it directly because
[navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) returns CORS headers.
Unlike Desktop, the redirect URI must be the app's own origin (`http://localhost:8081/`). See the
[demo README](https://github.com/svenjacobs/lokksmith/tree/main/demo#testing-the-web-auth-flow-with-a-local-oidc-server)
for step-by-step instructions.
