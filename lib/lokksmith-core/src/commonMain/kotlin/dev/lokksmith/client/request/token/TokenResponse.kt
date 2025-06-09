package dev.lokksmith.client.request.token

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
public data class TokenResponse(

    /**
     * ID Token encoded as a JWT.
     *
     * The token must be present for the Authorization Code Flow but may be absent for the
     * refresh response.
     */
    @SerialName("id_token")
    val idToken: String? = null,

    @SerialName("access_token")
    val accessToken: String,

    @SerialName("token_type")
    val tokenType: String,

    /**
     * Time in seconds when access token expires.
     */
    @SerialName("expires_in")
    val expiresIn: Long? = null,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    /**
     * Not part of the specification but returned by Keycloak for instance when refresh tokens
     * can expire.
     */
    @SerialName("refresh_expires_in")
    val refreshExpiresIn: Long? = null,

    val scope: String? = null,
)
