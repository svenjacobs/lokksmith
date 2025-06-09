package dev.lokksmith.client.jwt

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * A [JsonTransformingSerializer] for JWT payloads that collects all fields not defined in the JWT
 * specification (i.e., not in "iss", "sub", "aud", "exp", "nbf", "iat", "jti") and places them into
 * an "extra" property for deserialization. For serialization it takes all elements from the "extra"
 * property and puts them into the JSON object as first-class properties.
 *
 * This allows the [Jwt.Payload] data class to access both standard and custom claims.
 * Intended for use during (de)serialization of [Jwt.Payload].
 *
 * @see Jwt.Payload
 */
internal class JwtPayloadExtraTransformingSerializer : JsonTransformingSerializer<JsonObject>(
    JsonObject.serializer()
) {

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element

        val extra = element["extra"] as? JsonObject ?: return element

        return JsonObject(
            element.toMutableMap().apply { remove("extra") } + extra
        )
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element

        val (known, extra) = element.entries.partition { (key, _) -> KNOWN_KEYS.contains(key) }
            .let { (known, extra) ->
                Pair(
                    known.associate { it.toPair() },
                    extra.associate { it.toPair() })
            }

        return JsonObject(known + ("extra" to JsonObject(extra)))
    }

    private companion object {
        private val KNOWN_KEYS = setOf("iss", "sub", "aud", "exp", "nbf", "iat", "jti")
    }
}
