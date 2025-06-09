package dev.lokksmith.client.discovery

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
internal data class MetadataDiscoveryResponse(

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