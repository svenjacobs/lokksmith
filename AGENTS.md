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
- **Commit messages must additionally satisfy the commitlint rules** configured in
  `commitlint.config.js`, which extends `@commitlint/config-conventional`. A commit violating a rule
  at error level is rejected by the commit hook and by CI. The rules that trip people up most:
    - `footer-max-line-length` (error, 100): **every single line of the footer must be at most 100
      characters.** This includes trailers such as `Co-authored-by:`, `Closes #42` or
      `BREAKING CHANGE:` descriptions — wrap long footer text across multiple lines instead of
      letting one line grow past 100 characters.
    - `header-max-length` (warning, 100): keep the subject line (`type(scope): summary`) at 100
      characters or less.
    - `body-max-line-length` is disabled in this repository, so the body may contain long lines
      (e.g. URLs) — the footer may not.
- **Apply appropriate labels to pull requests**: `enhancement` (new features/improvements), `bug`
  (bug fixes), `documentation` (docs changes), `maintenance` (dependency updates, refactoring, chores).
- Explicit API mode is enabled: every declaration is explicitly `public` or `internal`. Types meant
  only for cross-module use are marked `public` but documented as "internal, do not use from
  application code" (e.g. `InternalClient`, `Container`).
- 4-space indentation, Kotlin coding conventions, enforced by Spotless.
- All new/changed code must have unit tests.
- If an LLM generated code, state so in the PR.

## Pull Request Description

Keep the description **concise**. It tells a reviewer what changed and why — it does not restate
the diff.

- Start with a **TL;DR** section of at most three sentences. If the whole description is that short
  anyway, drop the heading and just write those sentences.
- Add further sections only when they carry information the TL;DR cannot, for example notable
  implementation decisions, trade-offs, follow-ups or screenshots for UI changes.
- If the pull request contains testable functionality, add a **Testing** section that explains what
  needs to be tested and how: which demo screen to open, which Gradle task to run.
- Omit the Testing section for changes that cannot be verified by hand, such as documentation-only
  or build configuration changes.

Example:

````markdown
## TL;DR

Adds `preemptiveRefreshSeconds` handling to `runWithTokens` so access tokens are refreshed shortly
before they expire instead of after the first failed request.

## Testing

1. Run the demo app (`./gradlew :composeApp:run` in `demo`) and sign in against the local OIDC setup.
2. Wait until the access token is within the preemptive refresh window and trigger an API call —
   the token must be refreshed before the request goes out.
3. Run `./gradlew :lokksmith-core:jvmTest --tests "*ClientImplTest*"`.
````

### Closing Keywords

If a pull request implements a feature request or fixes a bug that originates from a GitHub issue,
include
a [closing keyword](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/using-keywords-in-issues-and-pull-requests)
in the PR description so the issue is automatically closed when the PR is merged.

Supported keywords: `Closes`, `Fixes`, `Resolves` (case-insensitive).

Example PR description:

```
Closes #42
```

or inline:

```
This PR adds support for refreshing tokens preemptively.

Closes #42
```

## Code Review

When reviewing a pull request, every **review comment**, **summary comment** and **reply** to a
remark **must** end with a note stating that it was written by an AI agent, naming the harness and
the model used.

Put the note on its own last line, in italics:

````markdown
`refresh()` is called outside the mutex here, so two concurrent callers can each start a token
refresh. Consider moving the call inside `updateSnapshot { }`.

_Written by Claude Code (Claude Opus 5)_
````

Always name the harness and model actually in use — the line above is only an example.

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
