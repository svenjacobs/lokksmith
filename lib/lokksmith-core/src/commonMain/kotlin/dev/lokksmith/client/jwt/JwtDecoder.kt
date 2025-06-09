package dev.lokksmith.client.jwt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * TODO: Signature verification
 */
public class JwtDecoder(
    private val json: Json,
) {

    @OptIn(ExperimentalEncodingApi::class)
    public fun decode(raw: String): Jwt {
        val parts = raw.split(".")
        require(parts.size == 3) { "Illegal JWT format" }

        val headerJson =
            Base64.UrlSafe.withPadding(PaddingOption.ABSENT).decode(parts[0])
                .decodeToString()
        val payloadJson =
            Base64.UrlSafe.withPadding(PaddingOption.ABSENT).decode(parts[1])
                .decodeToString()

        val header = json.decodeFromString<Jwt.Header>(headerJson)
        val payloadElement = json.decodeFromString<JsonElement>(payloadJson)

        /**
         * Transform payload into an intermediate JSON element where unknown fields are put into
         * an "extra" property.
         *
         * @see JwtPayloadExtraTransformingSerializer
         */
        val intermediatePayload = json.decodeFromJsonElement(
            JwtPayloadExtraTransformingSerializer(),
            payloadElement
        )

        val payload = json.decodeFromJsonElement<Jwt.Payload>(intermediatePayload)

        return Jwt(
            header = header,
            payload = payload,
        )
    }
}
