package dev.lokksmith.client.request.flow

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.snapshot.Snapshot

public abstract class AbstractAuthFlow internal constructor(
    internal val client: InternalClient,
    internal val state: String,
    private val responseHandler: AuthFlowResponseHandler,
    private val cancellation: AuthFlowCancellation,
) : AuthFlow {

    internal abstract val ephemeralFlowState: Snapshot.EphemeralFlowState

    /**
     * Returns the URL to initiate the flow.
     */
    internal abstract suspend fun onPrepare(): String

    internal open fun onPrepareUpdateSnapshot(snapshot: Snapshot): Snapshot = snapshot

    override suspend fun prepare(): Initiation {
        client.updateSnapshot {
            onPrepareUpdateSnapshot(
                copy(
                    flowResult = null,
                    ephemeralFlowState = this@AbstractAuthFlow.ephemeralFlowState,
                )
            )
        }

        return Initiation(
            state = state,
            requestUrl = onPrepare(),
            clientKey = client.key.value,
        )
    }

    override suspend fun onResponse(redirectUri: String) {
        responseHandler.onResponse(redirectUri)
    }

    override suspend fun cancel() {
        cancellation.cancel(state)
    }
}
