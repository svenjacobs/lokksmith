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
package dev.lokksmith.client.request.token

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
internal data class TokenErrorResponse(

    /**
     * The error code returned by the OAuth 2.0 token endpoint.
     *
     * This property is required by the OAuth 2.0 specification for error responses.
     * However, since the presence of the attribute cannot be guaranteed before deserialization,
     * it is declared as nullable.
     */
    val error: String? = null,

    @SerialName("error_description")
    val errorDescription: String? = null,

    @SerialName("error_uri")
    val errorUri: String? = null,
)
