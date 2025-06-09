package dev.lokksmith.client.request.flow.authorization_code

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AbstractAuthFlowCancellation
import dev.lokksmith.client.snapshot.Snapshot

public class AuthorizationCodeFlowCancellation(
    client: InternalClient
) : AbstractAuthFlowCancellation(client) {

    override fun Snapshot.onUpdateSnapshot(): Snapshot =
        copy(nonce = null)
}
