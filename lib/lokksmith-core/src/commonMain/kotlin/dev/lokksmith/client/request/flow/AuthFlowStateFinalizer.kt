package dev.lokksmith.client.request.flow

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.snapshot.Snapshot

/**
 * Finalizes an auth flow by removing temporary state data.
 */
public class AuthFlowStateFinalizer(
    private val client: InternalClient,
) {

    public suspend fun finalize(
        onBeforeUpdateSnapshot: Snapshot.() -> Snapshot = { this },
    ) {
        client.updateSnapshot {
            copy(
                ephemeralFlowState = null,
            ).onBeforeUpdateSnapshot()
        }
    }
}
