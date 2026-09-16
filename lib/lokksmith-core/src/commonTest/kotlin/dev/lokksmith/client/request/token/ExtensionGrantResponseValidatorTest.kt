/*
 * Copyright 2026 Sven Jacobs
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
package dev.lokksmith.client.request.token

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.TEST_INSTANT
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@Suppress("unused")
class ExtensionGrantResponseValidatorTest : AbstractTokenResponseValidatorTest<IdToken>() {

    override fun newValidator(client: InternalClient) =
        ExtensionGrantResponseValidator(serializer = Json, client = client)

    private val jwtEncoder = JwtEncoder(Json)

    // ExtensionGrantResponseValidator.validateIdTokenNonce is a no-op: an extension grant has no
    // authorization request, so there is nothing to compare the ID Token's nonce against. This
    // asserts the opposite of the base class case: a mismatched nonce must be accepted.
    @Test
    override fun `validate should apply the nonce rule`() = runTest {
        val idToken =
            Jwt(
                header = Jwt.Header(alg = "none"),
                payload =
                    Jwt.Payload(
                        iss = "issuer",
                        sub = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                        aud = listOf("clientId"),
                        exp = TEST_INSTANT + 600,
                        nbf = TEST_INSTANT,
                        iat = TEST_INSTANT,
                        extra = mapOf("nonce" to JsonPrimitive("7lOdSRX")),
                    ),
            )
        val response =
            TokenResponse(
                idToken = jwtEncoder.encode(idToken),
                accessToken = "YwV7xECTE0",
                tokenType = "Bearer",
                expiresIn = 600,
                refreshToken = "LMv1IT0",
                refreshExpiresIn = 1_209_600,
            )

        newValidator(createTestClient { copy(nonce = "0D1ck61") }).validate(response)
    }

    @Test
    fun `validate should fail when ID Token is missing`() = runTest {
        val response =
            TokenResponse(
                idToken = null,
                accessToken = "YwV7xECTE0",
                tokenType = "Bearer",
                expiresIn = 600,
            )

        val e =
            assertFailsWith<IllegalArgumentException> {
                newValidator(createTestClient { copy(nonce = "0D1ck61") }).validate(response)
            }

        assertEquals("ID Token is null", e.message)
    }
}
