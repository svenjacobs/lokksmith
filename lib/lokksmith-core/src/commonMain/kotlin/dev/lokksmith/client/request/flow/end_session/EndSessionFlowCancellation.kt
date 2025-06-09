package dev.lokksmith.client.request.flow.end_session

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AbstractAuthFlowCancellation

public class EndSessionFlowCancellation(
    client: InternalClient
) : AbstractAuthFlowCancellation(client)
