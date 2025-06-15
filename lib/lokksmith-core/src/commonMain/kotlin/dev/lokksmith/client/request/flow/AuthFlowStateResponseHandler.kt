package dev.lokksmith.client.request.flow

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.authorizationCode.AuthorizationCodeFlowResponseHandler
import dev.lokksmith.client.request.flow.endSession.EndSessionFlowResponseHandler
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.snapshot.Snapshot
import io.ktor.http.Url

/**
 * Handles OAuth flow responses by recovering the client using the `state` parameter.
 *
 * This handler is designed for scenarios where the initiation of an OAuth flow and the handling
 * of its response are decoupled, such as on mobile platforms.
 */
public class AuthFlowStateResponseHandler(
    private val lokksmith: Lokksmith,
) {

    public suspend fun onResponse(responseUri: String) {
        val url = Url(responseUri)
        val state = checkNotNull(url.parameters[Parameter.STATE]) {
            "Parameter \"${Parameter.STATE}\" missing from response"
        }

        val snapshot = checkNotNull(lokksmith.container.snapshotStore.getForState(state)) {
            "No client snapshot found for state"
        }

        val client = checkNotNull(lokksmith.get(snapshot.key.value)) {
            "No client found for state"
        } as InternalClient

        val handler = when (val ephemeralState = snapshot.ephemeralFlowState) {
            is Snapshot.EphemeralAuthorizationCodeFlowState -> AuthorizationCodeFlowResponseHandler(
                serializer = lokksmith.container.serializer,
                state = ephemeralState.state,
                client = client,
                httpClient = lokksmith.container.httpClient,
                redirectUri = ephemeralState.redirectUri,
                codeVerifier = ephemeralState.codeVerifier,
            )

            is Snapshot.EphemeralEndSessionFlowState -> EndSessionFlowResponseHandler(
                state = ephemeralState.state,
                client = client,
            )

            null -> throw IllegalStateException("ephemeralFlowState is null")
        }

        handler.onResponse(responseUri)
    }
}
