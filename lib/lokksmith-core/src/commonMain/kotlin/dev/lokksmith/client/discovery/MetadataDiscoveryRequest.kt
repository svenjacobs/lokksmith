/*
 * Copyright 2025 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.lokksmith.client.discovery

import dev.lokksmith.client.Client
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * OIDC metadata discovery
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery 1.0</a>
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">OpenID Connect RP-Initiated Logout 1.0</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8414">OAuth 2.0 Authorization Server Metadata (RFC 8414)</a>
 */
public interface MetadataDiscoveryRequest {

    /**
     * Reads configuration from a ".well-known" URI like `/.well-known/openid-configuration`
     * or `/.well-known/oauth-authorization-server`
     */
    public suspend operator fun invoke(url: String): Client.Metadata
}

internal class MetadataDiscoveryRequestImpl(
    private val httpClient: HttpClient,
) : MetadataDiscoveryRequest {

    override suspend operator fun invoke(url: String): Client.Metadata =
        withContext(Dispatchers.IO) {
            val response: MetadataDiscoveryResponse = httpClient.get(url).body()
            Client.Metadata(
                issuer = response.issuer,
                authorizationEndpoint = response.authorizationEndpoint,
                tokenEndpoint = response.tokenEndpoint,
                jwksUri = response.jwksUri,
                endSessionEndpoint = response.endSessionEndpoint,
                userInfoEndpoint = response.userInfoEndpoint,
            )
        }
}
