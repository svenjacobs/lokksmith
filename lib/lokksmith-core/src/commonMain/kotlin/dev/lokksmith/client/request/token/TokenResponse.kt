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

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
@Poko
@OptIn(ExperimentalSerializationApi::class)
public class TokenResponse(

    /**
     * ID Token encoded as a JWT.
     *
     * The token must be present for the Authorization Code Flow but may be absent for the refresh
     * response.
     */
    @SerialName("id_token") public val idToken: String? = null,
    @SerialName("access_token") public val accessToken: String,
    @SerialName("token_type") public val tokenType: String,

    /** Time in seconds when access token expires. */
    @SerialName("expires_in") public val expiresIn: Long? = null,
    @SerialName("refresh_token") public val refreshToken: String? = null,

    /**
     * Not part of the specification but returned by Keycloak for instance when refresh tokens can
     * expire.
     */
    @SerialName("refresh_expires_in") public val refreshExpiresIn: Long? = null,
    public val scope: String? = null,
)
