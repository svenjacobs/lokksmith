package dev.lokksmith.client.request.flow

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.FlowResult

public abstract class AbstractAuthFlowCancellation(
    client: InternalClient,
    private val stateFinalizer: AuthFlowStateFinalizer = AuthFlowStateFinalizer(client),
) : AuthFlowCancellation {

    internal open fun Snapshot.onUpdateSnapshot(): Snapshot = this

    override suspend fun cancel() {
        stateFinalizer.finalize {
            copy(
                flowResult = FlowResult.Cancelled,
            ).onUpdateSnapshot()
        }
    }
}
