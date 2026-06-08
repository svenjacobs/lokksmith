# Demo

Please have a look at our Compose Multiplatform [demo](https://github.com/svenjacobs/lokksmith/tree/main/demo)
application.

## Desktop

The demo also runs as a desktop app (`./gradlew :composeApp:run`). Because the Desktop target uses
an RFC 8252 loopback redirect (`http://127.0.0.1:<ephemeral-port>/callback`), you need an OIDC
provider that accepts arbitrary loopback redirect URIs. The demo ships a ready-to-use local
provider ([navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)); see the
[demo README](https://github.com/svenjacobs/lokksmith/tree/main/demo#testing-the-auth-flow-with-a-local-oidc-server)
for step-by-step instructions.
