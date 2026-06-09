Kotlin Multiplatform demo project for Lokksmith targeting Android, iOS, Desktop (JVM) and Web
(Wasm).  
Open / import this directory as a separate project in your IDE.

## Running the Desktop (JVM) app

From this `demo` directory:

```shell
./gradlew :composeApp:run
```

## Running the Web (Wasm) app

From this `demo` directory:

```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

This serves the app at <http://localhost:8081> (the dev-server port is set in
`composeApp/build.gradle.kts` to avoid clashing with the local OIDC mock server on `8080`).

## Testing the Desktop auth flow with a local OIDC server

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

## Testing the Web auth flow with a local OIDC server

**Yes — the Web app uses the exact same mock server** as Desktop
([navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server), see
[`local-oidc/`](local-oidc/docker-compose.yml)). No changes to the Compose file are needed.

How the Web differs from Desktop:

- There is no loopback server. The Web uses a **full-page redirect**: the browser navigates to the
  provider and is redirected back to a **same-origin** URL, which reloads the app. So unlike
  Desktop, the **redirect URI matters** and must be the app's own origin (`http://localhost:8081/`).
  The mock server accepts any `redirect_uri`, so no registration is required.
- The discovery and token endpoints are called from the browser (cross-origin to `:8080`). This
  works because mock-oauth2-server automatically returns CORS headers — no proxy needed.
- The redirect response is completed automatically on startup by `createLokksmith()`, so there is
  no Web-specific code in the app.

Steps:

1. Start the mock server (if not already running):

   ```shell
   docker compose -f local-oidc/docker-compose.yml up -d
   ```

2. Run the Web app (served on `8081`, so it coexists with the mock server on `8080`):

   ```shell
   ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
   ```

   Open <http://localhost:8081> if it does not open automatically.

3. In the app, create a client:
   - **Client ID**: any value, e.g. `demo-client`
   - **Discovery URL**: `http://localhost:8080/default/.well-known/openid-configuration`

   then press *Get or create client*.

4. Start the *Authorization Code Flow*. Set the **Redirect URI** to the app's own origin:

   ```
   http://localhost:8081/
   ```

   (The default `my-app://openid-response` is for the mobile demos and will **not** work on the
   Web; the "adjust `lokksmithRedirectScheme`" reminder is Android-only and does not apply here.)

5. The page navigates to the mock login. Enter any subject (username) and submit. The provider
   redirects back to `http://localhost:8081/?code=…&state=…`; the app reloads and `createLokksmith()`
   completes the flow (PKCE) and persists the tokens.

6. Because the full-page redirect restarts the app, the UI starts fresh. Re-enter the **same**
   Client ID and Discovery URL and press *Get or create client* again — the persisted client is
   loaded and its tokens are displayed. (App state is stored in the browser's `localStorage`.)

7. The *End Session* flow works the same way; use `http://localhost:8081/` as its redirect URI too.

8. Tear down the mock server when finished:

   ```shell
   docker compose -f local-oidc/docker-compose.yml down
   ```
