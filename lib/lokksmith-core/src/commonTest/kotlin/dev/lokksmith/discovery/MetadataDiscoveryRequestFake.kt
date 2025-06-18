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
package dev.lokksmith.discovery

import dev.lokksmith.client.Client
import dev.lokksmith.client.discovery.MetadataDiscoveryRequest

class MetadataDiscoveryRequestFake(
    private val onRequest: suspend (String) -> Client.Metadata,
) : MetadataDiscoveryRequest {

    override suspend fun invoke(url: String) = onRequest(url)
}
