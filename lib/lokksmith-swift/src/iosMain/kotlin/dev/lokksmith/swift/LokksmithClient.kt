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
import dev.lokksmith.client.request.flow.AuthFlowResultProvider
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result
import dev.lokksmith.ios.launchAuthFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
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
            val flow = client.authorizationCodeFlow(request.toCore())
            val initiation = flow.prepare()

            coroutineScope {
                // Collection starts before the browser is presented, so a result that arrives while
                // the session is still being torn down cannot be missed.
                val result = async { awaitResult(initiation.state) }

                @Suppress("UNCHECKED_CAST")
                lokksmith.launchAuthFlow(
                    initiation = initiation,
                    prefersEphemeralWebBrowserSession = request.prefersEphemeralWebBrowserSession,
                    additionalHeaderFields =
                        request.additionalHeaderFields.takeIf { it.isNotEmpty() } as Map<Any?, *>?,
                )

                when (val outcome = result.await()) {
                    is Result.Success -> {
                        AuthFlowResultProvider.confirmConsumed(client)
                        awaitTokens()
                    }
                    is Result.Cancelled -> {
                        AuthFlowResultProvider.confirmConsumed(client)
                        null
                    }
                    is Result.Error -> {
                        AuthFlowResultProvider.confirmConsumed(client)
                        throw LokksmithFailure(
                            kind = outcome.type.toFailureKind(),
                            message = outcome.message,
                            code = outcome.code,
                        )
                    }
                    else ->
                        throw LokksmithFailure(
                            kind = LokksmithFailureKind.Generic,
                            message = "Authorization flow produced no result",
                            code = null,
                        )
                }
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
        val initiation = flow.prepare()

        coroutineScope {
            val result = async { awaitResult(initiation.state) }

            lokksmith.launchAuthFlow(
                initiation = initiation,
                prefersEphemeralWebBrowserSession = request.prefersEphemeralWebBrowserSession,
                additionalHeaderFields = null,
            )

            val outcome = result.await()
            AuthFlowResultProvider.confirmConsumed(client)

            when (outcome) {
                is Result.Success -> true
                is Result.Cancelled -> false
                is Result.Error ->
                    throw LokksmithFailure(
                        kind = outcome.type.toFailureKind(),
                        message = outcome.message,
                        code = outcome.code,
                    )
                else -> false
            }
        }
    }

    /**
     * Returns the current tokens, refreshing them first if the access token is expired or about to
     * expire.
     *
     * Prefer this over [refresh] on the request path: it performs a network round trip only when
     * one is needed.
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

        if (client.isExpired(current.accessToken)) {
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
    }
}

private fun Result.Error.Type.toFailureKind(): LokksmithFailureKind =
    when (this) {
        Result.Error.Type.OAuth -> LokksmithFailureKind.OAuthRejection
        Result.Error.Type.Validation -> LokksmithFailureKind.TokenValidation
        Result.Error.Type.TemporalValidation -> LokksmithFailureKind.TokenTemporalValidation
        Result.Error.Type.Generic -> LokksmithFailureKind.Generic
    }
