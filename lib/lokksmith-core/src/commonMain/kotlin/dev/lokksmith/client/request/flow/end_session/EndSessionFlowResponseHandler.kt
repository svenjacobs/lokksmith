package dev.lokksmith.client.request.flow.end_session

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AbstractAuthFlowResponseHandler
import io.ktor.http.Url

public class EndSessionFlowResponseHandler(
    state: String,
    client: InternalClient,
) : AbstractAuthFlowResponseHandler(state, client) {

    override suspend fun onResponse(url: Url) {
        client.resetTokens()
    }
}
