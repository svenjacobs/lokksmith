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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtDecoderTest {

    private val decoder = JwtDecoder(Json)

    @Test
    fun `decode should decode encoded raw string into JWT`() {
        val jwt =
            decoder.decode("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")

        // Header
        assertEquals("HS256", jwt.header.alg)
        assertEquals("JWT", jwt.header.typ)

        // Payload
        assertEquals("1234567890", jwt.payload.sub)
        assertEquals("John Doe", jwt.payload.extra["name"]?.jsonPrimitive?.content)
        assertEquals(1516239022, jwt.payload.iat)
    }

    @Test
    fun `decode should decode an unsecured JWT`() {
        val jwt =
            decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTczNjI5MjEyNH0.")

        // Header
        assertEquals("none", jwt.header.alg)

        // Payload
        assertEquals("1234567890", jwt.payload.sub)
        assertEquals("John Doe", jwt.payload.extra["name"]?.jsonPrimitive?.content)
        assertEquals(true, jwt.payload.extra["admin"]?.jsonPrimitive?.boolean)
        assertEquals(1736292124, jwt.payload.iat)
    }

    @Test
    fun `decode should decode a JWT with a single 'aud' value`() {
        val jwt =
            decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTc0NzczNDUyNywiYXVkIjoiYXVkMSJ9.")

        assertEquals(listOf("aud1"), jwt.payload.aud)
    }

    @Test
    fun `decode should decode a JWT with multiple 'aud' values`() {
        val jwt =
            decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTc0NzczNDUyNywiYXVkIjpbImF1ZDEiLCJhdWQyIl19.")

        assertEquals(listOf("aud1", "aud2"), jwt.payload.aud)
    }

    @Test
    fun `decode should throw exception for invalid JWT format`() {
        assertFailsWith<IllegalArgumentException> { decoder.decode("a.b") }
    }
}
