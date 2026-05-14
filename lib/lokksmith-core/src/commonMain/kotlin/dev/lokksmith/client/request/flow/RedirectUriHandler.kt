/*
 * Copyright 2025 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.lokksmith.client.request.flow

/**
 * Per-flow strategy for resolving the redirect URI used in an OAuth/OIDC auth flow.
 *
 * Mobile platforms supply the URI directly via the request — the implementation is the identity
 * function. Desktop (JVM) ignores the requested URI and binds a loopback HTTP server on `127.0.0.1`
 * (RFC 8252 §7.3), returning the bound URL and arranging for the eventual response URI to be
 * written into the client's snapshot.
 *
 * All ownership of platform resources (loopback server, watcher coroutines) lives behind this
 * interface so [AbstractAuthFlow.prepare] / [AbstractAuthFlow.cancel] stay platform-agnostic.
 *
 * This interface is intended for internal use across Lokksmith modules and is only public to
 * support cross-module access from `lokksmith-compose` and JVM/Desktop integration. Application
 * code must not implement it.
 */
public interface RedirectUriHandler {

    /**
     * Returns the URI to embed in the auth request. Called once per [AbstractAuthFlow.prepare].
     *
     * [purpose] lets platform implementations branch on which flow this is — e.g. on JVM the
     * loopback server can render a different success page for [Purpose.Authorization] versus
     * [Purpose.EndSession].
     */
    public suspend fun resolve(requestRedirectUri: String, state: String, purpose: Purpose): String

    /** Releases per-state resources (e.g. closes a loopback server). Idempotent. */
    public suspend fun release(state: String)

    /**
     * What the redirect is for, so platform implementations can present an appropriate response.
     */
    public enum class Purpose {
        /** Authorization Code Flow — redirect carries the auth code back to the app. */
        Authorization,

        /** RP-Initiated Logout (End Session) — redirect signals end-of-session back to the app. */
        EndSession,
    }
}

/** Pass-through handler used on platforms where the redirect URI is known up-front (mobile). */
public object IdentityRedirectUriHandler : RedirectUriHandler {
    override suspend fun resolve(
        requestRedirectUri: String,
        state: String,
        purpose: RedirectUriHandler.Purpose,
    ): String = requestRedirectUri

    override suspend fun release(state: String): Unit = Unit
}
