# AGENTS.md

This file provides guidance to AI agents and coding assistants when working with code in this repository.

## Overview

Lokksmith is an opinionated Kotlin Multiplatform library implementing the OpenID Connect (OIDC)
Authorization Code Flow with PKCE for **Android, iOS, Desktop (JVM) and Web (Wasm)**. Published to
Maven Central under group `dev.lokksmith`. Documentation lives at [lokksmith.dev](https://lokksmith.dev).

The repository contains three independent Gradle/build roots, each opened as a separate project:

- `lib/` — the published library (run all library Gradle commands from here).
- `demo/` — a standalone KMP demo app that consumes `lib` via `includeBuild` + dependency
  substitution (so local library changes are picked up without publishing).
- `site/` — Zensical documentation (Python/`zensical.toml`), published to the docs site.

## Common Commands

All library commands run **inside the `lib` folder**:

```bash
./gradlew spotlessApply        # Format code — REQUIRED after any code change
./gradlew updateKotlinAbi      # Update ABI dump files — REQUIRED after any public API change
./gradlew check                # Run all checks (tests + spotlessCheck + ABI validation)
./gradlew test                 # Run all unit tests across targets
./gradlew :lokksmith-core:jvmTest                       # Tests for one target
./gradlew :lokksmith-core:jvmTest --tests "*ClientImplTest*"   # Run a single test class
```

CI verifies both code style (`spotlessCheck`) and the committed ABI dumps, so run `spotlessApply`
and `updateKotlinAbi` before committing — otherwise the build will fail.

Git commit hooks (commitlint + spotless via husky) are installed by running `npm install` in the
repository root.

Demo app (from the `demo` folder): `./gradlew :composeApp:run` (Desktop),
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (Web). See `demo/README.md` for the local OIDC
test setup.

## Conventions

- **Commit messages and PR titles must follow [Conventional Commits](https://www.conventionalcommits.org/)**
  (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`). PR titles double as the squash-merge
  commit message. Releases are automated via release-please from these commits.
- **Apply appropriate labels to pull requests**: `enhancement` (new features/improvements), `bug`
  (bug fixes), `documentation` (docs changes), `maintenance` (dependency updates, refactoring, chores).
- Explicit API mode is enabled: every declaration is explicitly `public` or `internal`. Types meant
  only for cross-module use are marked `public` but documented as "internal, do not use from
  application code" (e.g. `InternalClient`, `Container`).
- 4-space indentation, Kotlin coding conventions, enforced by Spotless.
- All new/changed code must have unit tests.
- If an LLM generated code, state so in the PR.

## Architecture

The core module (`lokksmith-core`) is structured around a few central abstractions in package
`dev.lokksmith`:

- **`Lokksmith`** (`Lokksmith.kt`) — the top-level manager. Strongly intended to be a **singleton**.
  Creates, retrieves, and deletes persistable `Client` instances keyed by a unique internal `Key`
  (distinct from the OAuth client `Id`, so multiple environment configs can share one client ID).
  Created via the platform-specific `createLokksmith()` factory.
- **`Client` / `ClientImpl`** (`client/Client.kt`) — a single logical OIDC client. Exposes token
  state (`tokens: StateFlow`), `refresh()`, `resetTokens()`, `runWithTokens { }` (refreshes only
  when expired per `preemptiveRefreshSeconds`), and entry points to flows.
- **`Container`** (`Container.kt`) — a hand-rolled IoC container holding shared dependencies
  (Ktor `HttpClient`, `SnapshotStore`, serializer, coroutine scope, `clientProviderFactory`).
- **`InternalClient.Provider`** — per-client factory for `RefreshTokenRequest`, `AuthorizationCodeFlow`,
  `EndSessionFlow`, and `RedirectUriHandler`. This is the primary seam for **swapping in fakes in
  unit tests**.

### Persistence & state (`client/snapshot/`)

State is persisted as a `Snapshot` (the serializable state of a client: id, metadata, tokens, nonce,
in-flight `ephemeralFlowState`, last `flowResult`) via AndroidX **DataStore** (Preferences),
serialized with kotlinx.serialization JSON. The `SnapshotStore` is the **single source of truth**:
`ClientImpl` derives its reactive `StateFlow`s by `map`ping over the snapshot flow, and all mutations
go through `updateSnapshot { copy(...) }`. `CURRENT_SCHEMA_VERSION` + `SnapshotMigration` handle
schema evolution; `Migration.kt` handles migrating from other libraries.

### Auth flows (`client/request/flow/`)

`AuthFlow` is the central flow interface with a deliberately **decoupled** lifecycle — `prepare()`
returns an `Initiation` (request URL + state), then later `onResponse(redirectUri)` or `cancel()` is
called (at most once each). This decoupling supports mobile/UI scenarios where flow initiation and
the redirect response happen across separate process lifecycles; the in-flight state is persisted in
the snapshot's `ephemeralFlowState`. Concrete flows: `authorizationCode/` (Authorization Code Flow
with PKCE — see `CodeChallengeFactory`, `VerifierStrategy`) and `endSession/`. The `state` parameter
is mandatory (CSRF protection + flow restoration), minimum 16 chars.

### Multiplatform layout (expect/actual)

Source sets per module: `commonMain` (most logic) plus `androidMain`, `iosMain`, `jvmMain`,
`wasmJsMain`; tests in `commonTest`, `jvmTest`. Platform-specific pieces use `expect`/`actual`:
`PlatformContext`, `createLokksmith()`, `createDataStore`, `platformHttpClientEngine`
(OkHttp on Android/JVM, Darwin on iOS, JS on Wasm). Notably, JVM/Desktop overrides `redirectUriHandler`
to run an RFC 8252 §7.3 loopback HTTP server on an ephemeral port (uses `ktor-server`); other
platforms use the identity handler.

The `lokksmith-compose` module adds a thin `AuthFlowLauncher` Composable layer with per-platform
actuals for launching the browser/Custom Tab and receiving the redirect.

### Build

Gradle build with version catalog `lib/gradle/libs.versions.toml` and convention plugins in
`lib/build-logic/` (`multiplatform-conventions`, `spotless-conventions`, `testlogger-conventions`).
The library version is injected at build time via `VERSION_NAME` into a generated `BuildConfig.VERSION`
(`SNAPSHOT` locally). Configuration cache, parallel and build caching are enabled.
