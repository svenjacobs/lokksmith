package dev.lokksmith.client.jwt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlin.io.encoding.ExperimentalEncodingApi

internal class JwtEncoder(
    private val json: Json,
) {

    /**
     * Note: currently can only encode unsecured tokens
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encode(jwt: Jwt): String {
        val headerJson = json.encodeToString(jwt.header)
        val header = Base64.UrlSafe.withPadding(PaddingOption.ABSENT).encode(
            headerJson.encodeToByteArray()
        )

        /**
         * Transform payload into an intermediate JSON element where all "extra" fields are added to
         * the object itself als first-class properties.
         *
         * @see JwtPayloadExtraTransformingSerializer
         */
        val intermediatePayload = json.encodeToJsonElement(
            JwtPayloadExtraTransformingSerializer(),
            json.encodeToJsonElement(jwt.payload) as JsonObject
        )
        val payloadJson = json.encodeToString(intermediatePayload)
        val payload = Base64.UrlSafe.withPadding(PaddingOption.ABSENT).encode(
            payloadJson.encodeToByteArray()
        )

        return "$header.$payload."
    }
}
