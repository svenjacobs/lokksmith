package dev.lokksmith.client.snapshot

import dev.lokksmith.client.Client
import dev.lokksmith.client.Id
import dev.lokksmith.client.Key
import kotlinx.serialization.Serializable

/**
 * A [Snapshot] represents the persistable state of a [Client].
 */
@Serializable
public data class Snapshot(
    val schemaVersion: Int = 1,
    val key: Key,
    val id: Id,
    val metadata: Client.Metadata,
    val options: Client.Options,
    val tokens: Client.Tokens? = null,

    /**
     * nonce needs to be remembered beyond authorization code flow because it may be also required
     * for every refresh response validation.
     */
    val nonce: String? = null,

    /**
     * Holds the result of the most recently executed auth flow.
     *
     * This property is particularly important when flow initiation and response handling are decoupled,
     * as is common on mobile devices and with certain UI frameworks. By persisting the flow result,
     * the client can reliably track whether the last flow completed successfully or encountered an error,
     * even if the app process is restarted or the UI is recreated.
     *
     * @see dev.lokksmith.client.request.flow.AuthFlowResultProvider
     */
    val flowResult: FlowResult? = null,

    /**
     * Holds temporary data that is required to fulfill an auth request.
     */
    val ephemeralFlowState: EphemeralFlowState? = null,
) {

    @Serializable
    public sealed interface FlowResult {

        @Serializable
        public data object Success : FlowResult

        @Serializable
        public data object Cancelled : FlowResult

        @Serializable
        public data class Error(
            val message: String?,
            val code: String? = null,
        ) : FlowResult
    }

    @Serializable
    public sealed interface EphemeralFlowState {
        public val state: String
        public val responseUri: String?
    }

    @Serializable
    public data class EphemeralAuthorizationCodeFlowState(
        override val state: String,
        override val responseUri: String?,
        val redirectUri: String,
        val codeVerifier: String?,
    ) : EphemeralFlowState

    @Serializable
    public data class EphemeralEndSessionFlowState(
        override val state: String,
        override val responseUri: String?,
    ) : EphemeralFlowState
}
