package dev.lokksmith.client.token

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.jwt.Jwt
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Maps a [Jwt] to an [IdToken] according to the OpenID Connect specification.
 *
 * This mapper extracts all required and optional ID Token claims from a generic JWT payload,
 * ensuring that all mandatory fields are present and correctly typed. Any additional claims
 * not explicitly defined in the ID Token specification are collected into the [IdToken.extra]
 * property.
 *
 * Throws [IllegalArgumentException] if any required claim (iss, sub, aud, exp, iat) is missing.
 */
public class JwtToIdTokenMapper {

    public operator fun invoke(jwt: Jwt, raw: String): IdToken {
        val p = jwt.payload
        require(p.aud.isNotEmpty()) { "aud is empty" }

        return IdToken(
            issuer = requireNotNull(p.iss) { "iss is null" },
            subject = requireNotNull(p.sub) { "sub is null" },
            expiration = requireNotNull(p.exp) { "exp is null" },
            issuedAt = requireNotNull(p.iat) { "iat is null" },
            audiences = p.aud,
            authTime = p.extra[KEY_AUTH_TIME]?.jsonPrimitive?.longOrNull,
            notBefore = p.nbf,
            nonce = p.extra[KEY_NONCE]?.jsonPrimitive?.contentOrNull,
            authenticationContextClassReference = p.extra[KEY_ACR]?.jsonPrimitive?.contentOrNull,
            authenticationMethodsReferences = p.extra[KEY_AMR]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
            authorizedParty = p.extra[KEY_AZP]?.jsonPrimitive?.contentOrNull,
            extra = p.extra.filterKeys { key -> !KNOWN_EXTRA_KEYS.contains(key) },
            raw = raw,
        )
    }

    private companion object {
        private const val KEY_AUTH_TIME = "auth_time"
        private const val KEY_NONCE = "nonce"
        private const val KEY_ACR = "acr"
        private const val KEY_AMR = "amr"
        private const val KEY_AZP = "azp"

        private val KNOWN_EXTRA_KEYS = listOf(
            KEY_AUTH_TIME,
            KEY_NONCE,
            KEY_ACR,
            KEY_AMR,
            KEY_AZP,
        )
    }
}
