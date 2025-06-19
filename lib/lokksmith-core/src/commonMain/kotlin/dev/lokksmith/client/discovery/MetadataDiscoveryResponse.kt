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

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
@Poko
@OptIn(ExperimentalSerializationApi::class)
internal class MetadataDiscoveryResponse(

    // The following four fields are required by both OIDC and OAuth 2.0
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("response_types_supported") val responseTypesSupported: List<String>,

    // Required for OIDC but optional for OAuth 2.0
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),

    // Extension of "Connect RP-Initiated Logout 1.0"
    // Optional when not supported
    @SerialName("end_session_endpoint") val endSessionEndpoint: String? = null,

    // Optional
    @SerialName("userinfo_endpoint") val userInfoEndpoint: String? = null,
)
