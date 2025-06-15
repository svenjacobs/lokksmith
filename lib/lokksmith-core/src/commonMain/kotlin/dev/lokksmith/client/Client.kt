package dev.lokksmith.client

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.ClientImpl.Companion.create
import dev.lokksmith.client.InternalClient.Provider
import dev.lokksmith.client.InternalClient.SnapshotContract
import dev.lokksmith.client.request.flow.AuthFlow
import dev.lokksmith.client.request.flow.authorizationCode.AuthorizationCodeFlow
import dev.lokksmith.client.request.flow.authorizationCode.AuthorizationCodeFlowCancellation
import dev.lokksmith.client.request.flow.endSession.EndSessionFlow
import dev.lokksmith.client.request.flow.endSession.EndSessionFlowCancellation
import dev.lokksmith.client.request.refresh.RefreshTokenRequest
import dev.lokksmith.client.request.refresh.RefreshTokenRequestImpl
import dev.lokksmith.client.snapshot.InternalSnapshotStore
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.SnapshotStore
import dev.lokksmith.client.snapshot.contract
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Represents an OpenID Connect client instance, providing access to authentication flows, token
 * management, and client configuration.
 *
 * A [Client] encapsulates the state and operations for a single logical OIDC client, including
 * token storage, refresh, and flow initiation. Instances are managed by [Lokksmith].
 *
 * ## Lifecycle
 * Call [dispose] when the client is no longer needed to release resources.
 *
 * ## Token Management
 * - Use [tokens] to observe the current tokens.
 * - Use [runWithTokens] to execute code with fresh, valid tokens.
 * - Use [refresh] to manually refresh tokens.
 * - Use [resetTokens] to clear all tokens and log out locally.
 *
 * ## Flows
 * - Use [authorizationCodeFlow] to start the Authorization Code Flow.
 * - Use [endSessionFlow] to initiate the End Session Flow, if supported.
 *
 * @see Lokksmith
 * @see authorizationCodeFlow
 * @see endSessionFlow
 */
public interface Client {

    // TODO: Should a Client have an option to proactively and automatically refresh tokens in the background?

    /**
     * OAuth / OIDC metadata
     */
    @Serializable
    public data class Metadata(
        val issuer: String,
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val jwksUri: String? = null,
        val endSessionEndpoint: String? = null,
        val userInfoEndpoint: String? = null,
    )

    /**
     * Options to configure the behavior of this client.
     */
    @Serializable
    public data class Options(
        /**
         * The number of seconds of clock skew to allow when validating time-based claims in tokens,
         * such as `exp` (expiration), `nbf` (not before), and `iat` (issued at).
         *
         * This leeway is applied in both positive and negative directions to account for small
         * differences between the clocks of the authorization server and the client device.
         * It helps prevent valid tokens from being incorrectly rejected due to minor clock drift.
         *
         * All time-based validation is performed in UTC, so daylight saving time (DST) changes
         * do not affect this value.
         *
         * Commonly referred to as "clock skew" or "leeway" in OAuth 2.0 and OpenID Connect
         * specifications.
         *
         * Setting this to a very large value will effectively bypass token validity and expiration
         * checks, which is highly discouraged.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation">OpenID Connect Core: ID Token Validation</a>
         * @see preemptiveRefreshSeconds
         */
        val leewaySeconds: Int = 10,

        /**
         * The number of seconds before a token's actual expiration time at which the client should
         * proactively attempt to refresh the token.
         *
         * This buffer helps ensure that tokens are refreshed in advance, reducing the risk of using
         * expired tokens due to network latency, clock drift, or slow refresh operations. For
         * example, if set to 60, the client will try to refresh the token 60 seconds before it
         * expires.
         *
         * **Warning:** Setting this value higher than the token's lifetime may cause the client to
         * always attempt a refresh, potentially resulting in unnecessary network requests or failed
         * refresh attempts.
         *
         * This value is independent of [leewaySeconds], which is used for clock skew during token
         * validation.
         *
         * @see leewaySeconds
         */
        val preemptiveRefreshSeconds: Int = 60 + leewaySeconds,
    ) {
        init {
            require(leewaySeconds >= 0) { "leewaySeconds must be a positive value" }
            require(preemptiveRefreshSeconds >= 0) { "preemptiveRefreshSeconds must be a positive value" }
        }
    }

    @Serializable
    public data class Tokens(
        val accessToken: AccessToken,
        val refreshToken: RefreshToken?,
        val idToken: IdToken,
    ) {
        public interface Token {
            public val token: String
            public val expiresAt: Long?
        }

        @Serializable
        public data class AccessToken(
            override val token: String,
            override val expiresAt: Long?,
        ) : Token

        @Serializable
        public data class RefreshToken(
            override val token: String,
            override val expiresAt: Long?,
        ) : Token

        /**
         * OpenID Connect ID Token
         *
         * @see <a href="https://www.rfc-editor.org/info/rfc7519">JSON Web Token (JWT)</a>
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">ID Token</a>
         */
        @Serializable
        public data class IdToken(
            // Required
            val issuer: String,
            val subject: String,
            val audiences: List<String>,
            val expiration: Long,
            val issuedAt: Long,

            // Optional
            val authTime: Long? = null,
            val notBefore: Long? = null,
            val nonce: String? = null,
            val authenticationContextClassReference: String? = null,
            val authenticationMethodsReferences: List<String> = emptyList(),
            val authorizedParty: String? = null,

            // All other parameters
            val extra: Map<String, JsonElement> = emptyMap(),

            // Raw string value of this token before it was decoded
            val raw: String,
        )
    }

    /**
     * Unique key as per [Lokksmith] instance.
     */
    public val key: Key

    /**
     * OAuth 2.0 client ID.
     */
    public val id: Id

    /**
     * OAuth / OIDC metadata which was provided manually or via discovery.
     *
     * Only update the metadata of an existing client if it still points to the same OpenID
     * provider. For example, this is appropriate when the provider's domain name changes but the
     * underlying service remains the same. Changing the metadata to reference a completely
     * different provider will almost certainly invalidate all existing tokens. In such cases,
     * use [Lokksmith.create] to register a new client instead.
     *
     * @see Metadata
     * @see [Lokksmith.getOrCreate]
     * @see [Lokksmith.create]
     */
    public var metadata: Metadata

    /**
     * Options for configuring the behavior of this client.
     *
     * @see Options
     * @see [Lokksmith.getOrCreate]
     * @see [Lokksmith.create]
     */
    public var options: Options

    /**
     * Exposes the current access, refresh and ID tokens.
     *
     * Note: Accessing the tokens via this [StateFlow] does not guarantee that they are fresh nor
     * valid. To ensure valid tokens, use [runWithTokens].
     */
    public val tokens: StateFlow<Tokens?>

    /**
     * Issues a refresh request regardless of current expiration timestamps and after successful
     * execution updates the access token and optionally the refresh and ID tokens as well when
     * provided by the OpenID provider.
     *
     * @return The updated [Tokens] instance containing the new tokens.
     *
     * @throws IllegalStateException if this client is unauthenticated and does not yet contain any tokens
     * @throws dev.lokksmith.client.request.RequestException if for example a network error occurred
     * @throws dev.lokksmith.client.request.ResponseException if the response is invalid
     * @throws dev.lokksmith.client.request.OAuthResponseException if the OAuth provider returned an error
     * @throws dev.lokksmith.client.request.token.TokenValidationException if the tokens could not be validated
     *
     * @see runWithTokens
     */
    public suspend fun refresh(): Tokens

    /**
     * Permanently removes all tokens (access, refresh, and ID tokens) from this client instance,
     * effectively logging out the user locally. After calling this method, the client becomes
     * unauthenticated, and a new [Authorization Code Flow][authorizationCodeFlow] must be started
     * to obtain new tokens.
     *
     * This operation only affects the local client state and does not notify or interact with the
     * OpenID provider. If you need to end the session with the provider, use [endSessionFlow].
     *
     * @return `true` if tokens were present and removed; `false` if the client did not contain any
     *         tokens.
     *
     * @see authorizationCodeFlow
     * @see endSessionFlow
     */
    public suspend fun resetTokens(): Boolean

    /**
     * Executes the given [body] lambda with fresh and valid [Tokens], refreshing them only when
     * necessary.
     *
     * This method ensures that the tokens provided to [body] are up-to-date and not expired.
     * If the current tokens are expired or about to expire, a refresh operation is performed
     * before invoking [body]. The lambda receives the valid [Tokens] instance for use in
     * authenticated requests.
     *
     * @param body Lambda to execute with valid [Tokens].
     *
     * @throws IllegalStateException if this client is unauthenticated and does not yet contain any tokens
     * @throws dev.lokksmith.client.request.RequestException if for example a network error occurred
     * @throws dev.lokksmith.client.request.ResponseException if the response is invalid
     * @throws dev.lokksmith.client.request.OAuthResponseException if the OAuth provider returned an error
     * @throws dev.lokksmith.client.request.token.TokenValidationException if the tokens could not be validated
     *
     * @see dev.lokksmith.client.usecase.RunWithTokensOrResetUseCase
     */
    public suspend fun runWithTokens(body: suspend (Tokens) -> Unit)

    /**
     * Returns an [AuthorizationCodeFlow] to initiate the Authorization Code Flow.
     *
     * @see AuthorizationCodeFlow
     */
    public fun authorizationCodeFlow(request: AuthorizationCodeFlow.Request): AuthFlow

    /**
     * Returns an [EndSessionFlow] to initiate the End Session Flow.
     * Returns `null` if this client doesn't support this flow.
     *
     * @see EndSessionFlow
     */
    public fun endSessionFlow(request: EndSessionFlow.Request): AuthFlow?

    /**
     * Checks whether the provided token is expired, using the client's current expiration and
     * refresh policy.
     *
     * @see runWithTokens
     * @see refresh
     * @return `true` if the token is expired or should be refreshed; `false` otherwise.
     */
    public fun isExpired(token: Tokens.Token): Boolean

    /**
     * Checks whether the provided token is expired, using the client's current expiration and
     * refresh policy.
     *
     * @see runWithTokens
     * @see refresh
     * @return `true` if the token is expired or should be refreshed; `false` otherwise.
     */
    public fun isExpired(token: Tokens.IdToken): Boolean

    /**
     * Releases all resources held by this client instance and performs necessary cleanup.
     *
     * After calling this method, the client instance becomes nonfunctional and must not be used.
     * Any ongoing operations may be cancelled, and further method calls may result in undefined
     * behavior.
     *
     * This method should be called when the client is no longer needed to avoid resource leaks.
     *
     * Note: This does not unregister or reset the client with the OpenID provider, nor does it
     * perform any OIDC requests.
     */
    public fun dispose()
}

/**
 * Internal client
 *
 * This interface is intended for internal use and is only public to allow access from other
 * Lokksmith modules. Application code must not interact with this interface directly.
 */
public interface InternalClient : Client {

    public interface SnapshotContract {

        public val snapshots: StateFlow<Snapshot>

        public suspend fun updateSnapshot(body: Snapshot.() -> Snapshot): Snapshot
    }

    /**
     * Provides various internal dependencies.
     * Particularly useful in unit tests when replacing dependencies with fakes.
     */
    public interface Provider {

        public val instantProvider: InstantProvider

        public val refreshTokenRequest: (client: InternalClient) -> RefreshTokenRequest

        public val authorizationCodeFlow: (
            client: InternalClient,
            request: AuthorizationCodeFlow.Request,
        ) -> AuthFlow

        public val endSessionFlow: (
            client: InternalClient,
            request: EndSessionFlow.Request,
        ) -> AuthFlow?
    }

    public val provider: Provider

    public val snapshots: StateFlow<Snapshot>

    public suspend fun updateSnapshot(body: Snapshot.() -> Snapshot): Snapshot

    public suspend fun updatePendingFlowResponse(responseUri: String)

    public suspend fun cancelPendingFlow()
}

internal class ClientImpl private constructor(
    override val key: Key,
    override val tokens: StateFlow<Tokens?>,
    override val provider: Provider,
    private val snapshotContract: SnapshotContract,
    internal val coroutineScope: CoroutineScope,
) : InternalClient {

    internal class DefaultProvider(
        private val httpClient: HttpClient,
        private val serializer: Json,
        override val instantProvider: InstantProvider = DefaultInstantProvider,
        override val refreshTokenRequest: (InternalClient) -> RefreshTokenRequest = { client ->
            RefreshTokenRequestImpl(
                client = client,
                httpClient = httpClient,
                serializer = serializer,
            )
        },
        override val authorizationCodeFlow: (InternalClient, AuthorizationCodeFlow.Request) -> AuthFlow = { client, request ->
            AuthorizationCodeFlow.create(
                request = request,
                client = client,
                httpClient = httpClient,
                serializer = serializer,
            )
        },
        override val endSessionFlow: (InternalClient, EndSessionFlow.Request) -> AuthFlow? = { client, request ->
            EndSessionFlow.createOrNull(
                client = client,
                request = request,
            )
        },
    ) : Provider

    override val snapshots: StateFlow<Snapshot>
        get() = snapshotContract.snapshots

    override val id: Id
        get() = snapshots.value.id

    override var metadata: Client.Metadata
        get() = snapshots.value.metadata
        set(value) {
            coroutineScope.launch {
                updateSnapshot {
                    copy(metadata = value)
                }
            }
        }

    override var options: Client.Options
        get() = snapshots.value.options
        set(value) {
            coroutineScope.launch {
                updateSnapshot {
                    copy(options = value)
                }
            }
        }

    override suspend fun refresh(): Tokens {
        val tokens =
            checkNotNull(snapshots.value.tokens) { "Client not authenticated (tokens are null)" }
        check(tokens.accessToken.expiresAt == null || tokens.refreshToken != null) { "access token has expiration but refresh token is missing" }

        val refreshTokenRequest = provider.refreshTokenRequest(this)
        val response = refreshTokenRequest()

        val refreshedTokens = Tokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            idToken = response.idToken ?: tokens.idToken,
        )

        updateSnapshot {
            copy(tokens = refreshedTokens)
        }

        return refreshedTokens
    }

    override suspend fun runWithTokens(body: suspend (Tokens) -> Unit) {
        val currentTokens =
            checkNotNull(snapshots.value.tokens) { "Client not authenticated (tokens are null)" }
        val validTokens = when {
            currentTokens.areExpired(
                preemptiveRefreshSeconds = options.preemptiveRefreshSeconds,
                leewaySeconds = options.leewaySeconds,
                instantProvider = provider.instantProvider,
            ) -> refresh()

            else -> currentTokens
        }

        body(validTokens)
    }

    override suspend fun resetTokens(): Boolean {
        if (snapshots.value.tokens == null) return false

        updateSnapshot {
            copy(
                tokens = null,
                nonce = null,
                flowResult = null,
                ephemeralFlowState = null,
            )
        }

        return true
    }

    override fun authorizationCodeFlow(request: AuthorizationCodeFlow.Request) =
        provider.authorizationCodeFlow(this, request)

    override fun endSessionFlow(request: EndSessionFlow.Request) =
        provider.endSessionFlow(this, request)

    override fun isExpired(token: Tokens.Token) = token.isExpired(
        preemptiveRefreshSeconds = options.preemptiveRefreshSeconds,
        leewaySeconds = options.leewaySeconds,
        instantProvider = provider.instantProvider,
    )

    override fun isExpired(token: Tokens.IdToken) = token.isExpired(
        preemptiveRefreshSeconds = options.preemptiveRefreshSeconds,
        leewaySeconds = options.leewaySeconds,
        instantProvider = provider.instantProvider,
    )

    override suspend fun updateSnapshot(body: Snapshot.() -> Snapshot) =
        snapshotContract.updateSnapshot(body)

    override suspend fun updatePendingFlowResponse(responseUri: String) {
        updateSnapshot {
            val updatedFlowState = when (val state = ephemeralFlowState) {
                is Snapshot.EphemeralAuthorizationCodeFlowState -> state.copy(responseUri = responseUri)
                is Snapshot.EphemeralEndSessionFlowState -> state.copy(responseUri = responseUri)
                null -> throw IllegalStateException("ephemeralFlowState is null")
            }

            copy(
                ephemeralFlowState = updatedFlowState,
            )
        }
    }

    override suspend fun cancelPendingFlow() {
        val cancellation = when (snapshots.value.ephemeralFlowState) {
            is Snapshot.EphemeralAuthorizationCodeFlowState -> ::AuthorizationCodeFlowCancellation
            is Snapshot.EphemeralEndSessionFlowState -> ::EndSessionFlowCancellation
            null -> throw IllegalStateException("ephemeral flow state is null")
        }(this)

        cancellation.cancel()
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    internal companion object {

        /**
         * Produces an instance of [ClientImpl].
         *
         * This functions creates a [StateFlow] and suspends until the first value is received.
         * Therefor [create] must only be called after the initial snapshot has been stored!
         */
        internal suspend fun create(
            key: Key,
            coroutineScope: CoroutineScope,
            snapshotStore: SnapshotStore,
            provider: Provider,
        ): InternalClient {
            // Create a "child" CoroutineScope that can be cancelled individually per client
            val clientCoroutineScope = CoroutineScope(
                coroutineScope.coroutineContext
                    + Job(coroutineScope.coroutineContext.job)
                    + CoroutineName("LokksmithClient(${key.value})")
            )

            val snapshotContract = (snapshotStore as InternalSnapshotStore).contract(
                key = key,
                coroutineScope = clientCoroutineScope,
            )

            val tokens = snapshotContract.snapshots.map { it.tokens }.stateIn(clientCoroutineScope)

            return ClientImpl(
                key = key,
                tokens = tokens,
                provider = provider,
                snapshotContract = snapshotContract,
                coroutineScope = clientCoroutineScope,
            )
        }
    }
}
