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
package dev.lokksmith.client.jwt

import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/** TODO: Signature verification */
public class JwtDecoder(private val json: Json) {

    public fun decode(raw: String): Jwt {
        val parts = raw.split(".")
        require(parts.size == 3) { "Illegal JWT format" }

        val headerJson =
            Base64.UrlSafe.withPadding(PaddingOption.ABSENT).decode(parts[0]).decodeToString()
        val payloadJson =
            Base64.UrlSafe.withPadding(PaddingOption.ABSENT).decode(parts[1]).decodeToString()

        val header = json.decodeFromString<Jwt.Header>(headerJson)
        val payloadElement = json.decodeFromString<JsonElement>(payloadJson)

        /**
         * Transform payload into an intermediate JSON element where unknown fields are put into an
         * "extra" property.
         *
         * @see JwtPayloadExtraTransformingSerializer
         */
        val intermediatePayload =
            json.decodeFromJsonElement(JwtPayloadExtraTransformingSerializer(), payloadElement)

        val payload = json.decodeFromJsonElement<Jwt.Payload>(intermediatePayload)

        return Jwt(header = header, payload = payload)
    }
}
