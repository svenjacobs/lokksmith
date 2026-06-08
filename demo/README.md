Kotlin Multiplatform demo project for Lokksmith targeting Android, iOS and Desktop (JVM).  
Open / import this directory as a separate project in your IDE.

## Running the Desktop (JVM) app

From this `demo` directory:

```shell
./gradlew :composeApp:run
```

## Testing the auth flow with a local OIDC server

On Desktop, Lokksmith implements the RFC 8252 loopback redirect: it starts a local HTTP server
bound to `127.0.0.1` on an **ephemeral port** and uses `http://127.0.0.1:<port>/callback` as the
redirect URI. Because the port changes on every run, you need an OIDC provider that accepts
arbitrary `http://127.0.0.1` redirect URIs without prior registration.

[navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) does exactly that — it
serves OIDC discovery + JWKS, supports PKCE, and accepts any `client_id` and any `redirect_uri`. A
ready-to-use Compose file is provided in [`local-oidc/`](local-oidc/docker-compose.yml).

1. Start the server (from this `demo` directory):

   ```shell
   docker compose -f local-oidc/docker-compose.yml up -d
   ```

   > Podman works too: `podman compose -f local-oidc/docker-compose.yml up -d`.

2. Verify it is up:

   ```shell
   curl http://localhost:8080/default/.well-known/openid-configuration
   ```

3. Run the desktop app:

   ```shell
   ./gradlew :composeApp:run
   ```

4. In the app, create a client:
   - **Client ID**: any value, e.g. `demo-client`
   - **Discovery URL**: `http://localhost:8080/default/.well-known/openid-configuration`

   then press *Get or create client*.

5. Start the *Authorization Code Flow*. The **redirect URI field is ignored on Desktop** — the
   loopback handler overrides it with `http://127.0.0.1:<port>/callback` — so you can leave the
   default or type anything (e.g. `http://127.0.0.1/callback`).

6. Your system browser opens the mock login page. Enter any subject (username) and submit. The
   browser is redirected to the loopback server, which captures the authorization code; the app
   exchanges it for tokens (PKCE) and displays them. The browser shows the success page.

7. The *End Session* flow can be tested the same way.

8. Tear down when finished:

   ```shell
   docker compose -f local-oidc/docker-compose.yml down
   ```

The mock server image is pinned in the Compose file; bump the tag there to update it.
