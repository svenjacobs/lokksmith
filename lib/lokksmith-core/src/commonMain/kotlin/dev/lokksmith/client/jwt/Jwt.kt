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

import dev.drewhamilton.poko.Poko
import dev.lokksmith.serialization.StringListSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * JSON Web Token
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc7519">JSON Web Token (JWT)</a>
 * @see <a href="https://datatracker.ietf.org/group/jose/documents/">Javascript Object Signing and
 *   Encryption (jose)</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc7520">Examples of Protecting Content Using JSON
 *   Object Signing and Encryption (JOSE)</a>
 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">JWT (iana)</a>
 */
@OptIn(ExperimentalSerializationApi::class)
@Poko
public class Jwt(public val header: Header, public val payload: Payload) {

    @Serializable
    @JsonIgnoreUnknownKeys
    @Poko
    public class Header(
        public val alg: String? = null,
        public val typ: String? = null,
        public val kid: String? = null,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    @Poko
    public class Payload(
        /** Issuer */
        public val iss: String? = null,

        /** Subject */
        public val sub: String? = null,

        /** Audience */
        @Serializable(with = StringListSerializer::class)
        public val aud: List<String> = emptyList(),

        /** Expiration Time */
        public val exp: Long? = null,

        /** Not Before */
        public val nbf: Long? = null,

        /** Issued At */
        public val iat: Long? = null,

        /** JWT ID */
        public val jti: String? = null,

        /**
         * Any fields that are not part of the specification are put into this map.
         *
         * @see JwtPayloadExtraTransformingSerializer
         */
        public val extra: Map<String, JsonElement> = emptyMap(),
    )
}
