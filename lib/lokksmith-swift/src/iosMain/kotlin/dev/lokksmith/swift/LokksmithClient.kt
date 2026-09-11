/*
 * Copyright 2026 Sven Jacobs
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
package dev.lokksmith.swift

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.Client
import dev.lokksmith.client.request.flow.AuthFlow
import dev.lokksmith.client.request.flow.AuthFlowResultProvider
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result
import dev.lokksmith.ios.launchAuthFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A persisted OpenID Connect client.
 *
 * Obtain instances through [LokksmithManager]. Instances for the same key share state, but a single
 * instance per key is recommended.
 */
public class LokksmithClient
internal constructor(
    private val lokksmith: Lokksmith,
    private val client: Client,
    private val coroutineScope: CoroutineScope,
) {

    /** The internal key this client is stored under, distinct from [clientId]. */
    public val key: String
        get() = client.key.value

    /** The OAuth 2.0 client identifier. */
    public val clientId: String
        get() = client.id.value

    /**
     * The current tokens, or `null` when the client is not authenticated.
     *
     * This is a snapshot and does not refresh anything. It is safe to read synchronously — use it
     * to answer "is the user signed in?" without suspending. Use [freshTokens] when a usable access
     * token is required.
     */
    public val tokens: LokksmithTokens?
        get() = client.tokens.value?.toSwift()

    /** `true` when the client holds tokens. Does not consider whether they are expired. */
    public val isAuthenticated: Boolean
        get() = client.tokens.value != null

    /**
     * Observes [tokens], calling [onChange] on the main dispatcher whenever they change, starting
     * with the current value.
     *
     * Cancel the returned handle to stop observing. Not cancelling it leaks the observation for the
     * lifetime of the [LokksmithManager].
     */
    public fun observeTokens(onChange: (LokksmithTokens?) -> Unit): LokksmithCancellable {
        val job =
            coroutineScope.launch(mainDispatcher()) {
                client.tokens.collect { onChange(it?.toSwift()) }
            }
        return LokksmithCancellable(job)
    }

    /**
     * Runs the Authorization Code Flow with PKCE end to end: presents an
     * `ASWebAuthenticationSession`, exchanges the authorization code, validates and persists the
     * tokens.
     *
     * @return The resulting tokens, or `null` if the user dismissed the browser.
     * @throws LokksmithFailure if the flow failed.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun authorize(request: LokksmithAuthorizationRequest): LokksmithTokens? =
        mapFailures {
            val outcome =
                runAuthFlow(
                    flow = client.authorizationCodeFlow(request.toCore()),
                    prefersEphemeralWebBrowserSession = request.prefersEphemeralWebBrowserSession,
                    additionalHeaderFields = request.additionalHeaderFields,
                )

            when (outcome) {
                is Result.Success -> awaitTokens()
                is Result.Cancelled -> null
                else -> throw outcome.toFailure()
            }
        }

    /**
     * Runs the RP-initiated logout flow, presenting an `ASWebAuthenticationSession` against the
     * provider's `end_session_endpoint`.
     *
     * This does not clear local tokens — call [resetTokens] for that.
     *
     * @return `false` if the provider does not advertise an `end_session_endpoint`, or if the user
     *   dismissed the browser.
     * @throws LokksmithFailure if the flow failed.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun endSession(request: LokksmithEndSessionRequest): Boolean = mapFailures {
        val flow = client.endSessionFlow(request.toCore()) ?: return@mapFailures false

        val outcome =
            runAuthFlow(
                flow = flow,
                prefersEphemeralWebBrowserSession = request.prefersEphemeralWebBrowserSession,
                additionalHeaderFields = emptyMap(),
            )

        when (outcome) {
            is Result.Success -> true
            is Result.Cancelled -> false
            else -> throw outcome.toFailure()
        }
    }

    /**
     * Returns the current tokens, refreshing them first if the access token or the ID token is
     * expired or about to expire.
     *
     * Prefer this over [refresh] on the request path: it performs a network round trip only when
     * one is needed.
     *
     * Both tokens are checked, matching `Client.runWithTokens`. Checking the access token alone
     * would never refresh for a provider that omits `expires_in`, because an access token without
     * an expiry is never considered expired, whereas an ID token always carries `exp`.
     *
     * @throws LokksmithFailure if no tokens are present, or the refresh failed.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun freshTokens(): LokksmithTokens = mapFailures {
        val current =
            client.tokens.value
                ?: throw LokksmithFailure(
                    kind = LokksmithFailureKind.Generic,
                    message = "Client is not authenticated",
                    code = null,
                )

        if (client.isExpired(current.accessToken) || client.isExpired(current.idToken)) {
            client.refresh().toSwift()
        } else {
            current.toSwift()
        }
    }

    /**
     * Refreshes the tokens unconditionally.
     *
     * @throws LokksmithFailure if no refresh token is present, or the refresh failed. A
     *   [LokksmithFailureKind.OAuthRejection] means the refresh token is dead and the user must
     *   re-authenticate; a [LokksmithFailureKind.Transport] is transient and the session should be
     *   kept.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun refresh(): LokksmithTokens = mapFailures { client.refresh().toSwift() }

    /**
     * Discards the persisted tokens, keeping the client and its provider configuration.
     *
     * @return `false` if there were no tokens to discard.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun resetTokens(): Boolean = mapFailures { client.resetTokens() }

    /** `true` if [token] is expired, taking the client's configured leeway into account. */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun isExpired(token: LokksmithToken): Boolean = mapFailures {
        client.isExpired(
            Client.Tokens.AccessToken(token = token.token, expiresAt = token.expiresAt)
        )
    }

    /**
     * Releases resources held by this instance.
     *
     * The persisted client is unaffected; use [LokksmithManager.deleteClient] to remove it.
     */
    public fun dispose() {
        client.dispose()
    }

    internal fun coreClient(): Client = client

    /**
     * Prepares [flow], presents the system browser and returns the flow's terminal result, which is
     * marked consumed before returning.
     */
    private suspend fun runAuthFlow(
        flow: AuthFlow,
        prefersEphemeralWebBrowserSession: Boolean,
        additionalHeaderFields: Map<String, String>,
    ): Result {
        val initiation = flow.prepare()

        val outcome =
            try {
                coroutineScope {
                    // Collection starts before the browser is presented, so a result that arrives
                    // while the session is still being torn down cannot be missed.
                    val result = async { awaitResult(initiation.state) }

                    // `launchAuthFlow` reads UIApplication.sharedApplication.windows and starts an
                    // ASWebAuthenticationSession, both of which require the main thread. The
                    // exported suspend function carries no dispatcher of its own, and `prepare()`
                    // above resumes on the persistence layer's IO dispatcher.
                    @Suppress("UNCHECKED_CAST")
                    withContext(mainDispatcher()) {
                        lokksmith.launchAuthFlow(
                            initiation = initiation,
                            prefersEphemeralWebBrowserSession = prefersEphemeralWebBrowserSession,
                            additionalHeaderFields =
                                additionalHeaderFields.takeIf { it.isNotEmpty() } as Map<Any?, *>?,
                        )
                    }

                    // The timeout starts only here, after the browser has closed: the user paces
                    // the flow itself, so timing out around `launchAuthFlow` would abort a login
                    // that is still being typed. Past this point the response has been handled and
                    // the result is either recorded or about to be.
                    withTimeoutOrNull(FLOW_RESULT_TIMEOUT_MS) { result.await() }
                        .also {
                            // Releases `coroutineScope`, which would otherwise await the collector.
                            if (it == null) result.cancel()
                        }
                }
            } catch (e: CancellationException) {
                // Without this the flow stays pending: `ephemeralFlowState` remains set and
                // `authFlowResult` reports `Processing` until the next `prepare()`.
                withContext(NonCancellable) { flow.cancel() }
                throw e
            }

        if (outcome == null) {
            withContext(NonCancellable) { flow.cancel() }
            throw LokksmithFailure(
                kind = LokksmithFailureKind.Generic,
                message = "Authorization flow produced no result for its own state",
                code = null,
            )
        }

        AuthFlowResultProvider.confirmConsumed(client)
        return outcome
    }

    private suspend fun awaitResult(state: String): Result =
        AuthFlowResultProvider.forClient(client)
            .mapNotNull { result ->
                when (result) {
                    is Result.Success -> result.takeIf { it.state == state }
                    is Result.Cancelled -> result.takeIf { it.state == state }
                    is Result.Error -> result.takeIf { it.state == state }
                    // Processing and Undefined are not terminal.
                    else -> null
                }
            }
            .first()

    /**
     * Reads the tokens written by a successful flow.
     *
     * The token state is fed asynchronously from the snapshot store, so a successful result can be
     * observed marginally before the tokens are readable. Waiting briefly here avoids reporting a
     * successful authorization as a failure.
     */
    private suspend fun awaitTokens(): LokksmithTokens =
        client.tokens.value?.toSwift()
            ?: withTimeoutOrNull(TOKEN_PROPAGATION_TIMEOUT_MS) {
                client.tokens.mapNotNull { it }.firstOrNull()?.toSwift()
            }
            ?: throw LokksmithFailure(
                kind = LokksmithFailureKind.Generic,
                message = "Authorization succeeded but no tokens were persisted",
                code = null,
            )

    private companion object {
        const val TOKEN_PROPAGATION_TIMEOUT_MS = 5_000L
        const val FLOW_RESULT_TIMEOUT_MS = 5_000L
    }
}

/**
 * Maps a non-terminal or failed [Result] to the failure reported to Swift.
 *
 * [Result.Success] and [Result.Cancelled] are outcomes rather than failures and are handled by the
 * callers.
 */
private fun Result.toFailure(): LokksmithFailure =
    when (this) {
        is Result.Error ->
            LokksmithFailure(kind = type.toFailureKind(), message = message, code = code)
        else ->
            LokksmithFailure(
                kind = LokksmithFailureKind.Generic,
                message = "Authorization flow produced no result",
                code = null,
            )
    }

private fun Result.Error.Type.toFailureKind(): LokksmithFailureKind =
    when (this) {
        Result.Error.Type.OAuth -> LokksmithFailureKind.OAuthRejection
        Result.Error.Type.Validation -> LokksmithFailureKind.TokenValidation
        Result.Error.Type.TemporalValidation -> LokksmithFailureKind.TokenTemporalValidation
        Result.Error.Type.Generic -> LokksmithFailureKind.Generic
    }
