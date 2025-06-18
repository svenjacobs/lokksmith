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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlin.io.encoding.ExperimentalEncodingApi

internal class JwtEncoder(
    private val serializer: Json,
) {

    /**
     * Note: currently can only encode unsecured tokens
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encode(jwt: Jwt): String {
        val headerJson = serializer.encodeToString(jwt.header)
        val header = Base64.UrlSafe.withPadding(PaddingOption.ABSENT).encode(
            headerJson.encodeToByteArray()
        )

        /**
         * Transform payload into an intermediate JSON element where all "extra" fields are added to
         * the object itself als first-class properties.
         *
         * @see JwtPayloadExtraTransformingSerializer
         */
        val intermediatePayload = serializer.encodeToJsonElement(
            JwtPayloadExtraTransformingSerializer(),
            serializer.encodeToJsonElement(jwt.payload) as JsonObject
        )
        val payloadJson = serializer.encodeToString(intermediatePayload)
        val payload = Base64.UrlSafe.withPadding(PaddingOption.ABSENT).encode(
            payloadJson.encodeToByteArray()
        )

        return "$header.$payload."
    }
}
