package dev.lokksmith.client.jwt

import dev.lokksmith.serialization.StringListSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * JSON Web Token
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc7519">JSON Web Token (JWT)</a>
 * @see <a href="https://datatracker.ietf.org/group/jose/documents/">Javascript Object Signing and Encryption (jose)</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc7520">Examples of Protecting Content Using JSON Object Signing and Encryption (JOSE)</a>
 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">JWT (iana)</a>
 */
@OptIn(ExperimentalSerializationApi::class)
public data class Jwt(
    val header: Header,
    val payload: Payload,
) {

    @Serializable
    @JsonIgnoreUnknownKeys
    public data class Header(
        val alg: String? = null,
        val typ: String? = null,
        val kid: String? = null,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    public data class Payload(
        /**
         * Issuer
         */
        val iss: String? = null,

        /**
         * Subject
         */
        val sub: String? = null,

        /**
         * Audience
         */
        @Serializable(with = StringListSerializer::class)
        val aud: List<String> = emptyList(),

        /**
         * Expiration Time
         */
        val exp: Long? = null,

        /**
         * Not Before
         */
        val nbf: Long? = null,

        /**
         * Issued At
         */
        val iat: Long? = null,

        /**
         * JWT ID
         */
        val jti: String? = null,

        /**
         * Any fields that are not part of the specification are put into this map.
         *
         * @see JwtPayloadExtraTransformingSerializer
         */
        val extra: Map<String, JsonElement> = emptyMap(),
    )
}
