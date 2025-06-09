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
