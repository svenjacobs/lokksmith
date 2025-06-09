package dev.lokksmith.discovery

import dev.lokksmith.client.Client
import dev.lokksmith.client.discovery.MetadataDiscoveryRequest

class MetadataDiscoveryRequestFake(
    private val onRequest: suspend (String) -> Client.Metadata,
) : MetadataDiscoveryRequest {

    override suspend fun invoke(url: String) = onRequest(url)
}
