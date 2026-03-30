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
package dev.lokksmith.client.request.flow.authorizationCode

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.TEST_INSTANT
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import dev.lokksmith.client.request.token.AbstractTokenResponseValidatorTest
import dev.lokksmith.client.request.token.TokenResponse
import dev.lokksmith.client.request.token.TokenTemporalValidationException
import dev.lokksmith.client.request.token.TokenValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@Suppress("unused")
class AuthorizationCodeFlowTokenResponseValidatorTest :
    AbstractTokenResponseValidatorTest<IdToken>() {

    override fun newValidator(client: InternalClient) =
        AuthorizationCodeFlowTokenResponseValidator(serializer = Json, client = client)

    private val jwtEncoder = JwtEncoder(Json)

    private fun buildTokenResponse(
        nonce: String = "0D1ck61",
        authTime: Long? = null,
    ): TokenResponse {
        val extraClaims = buildMap {
            put("nonce", JsonPrimitive(nonce))
            authTime?.let { put("auth_time", JsonPrimitive(it)) }
        }
        val payload =
            Jwt.Payload(
                iss = "issuer",
                sub = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                aud = listOf("clientId"),
                exp = TEST_INSTANT + 600,
                iat = TEST_INSTANT,
                extra = extraClaims,
            )
        val idToken = Jwt(header = Jwt.Header(alg = "none"), payload = payload)
        return TokenResponse(
            idToken = jwtEncoder.encode(idToken),
            accessToken = "YwV7xECTE0",
            tokenType = "Bearer",
            expiresIn = 600,
        )
    }

    // auth_time / max_age validation tests
    // Per OIDC Core 1.0, Section 3.1.3.7, item 12:
    // "If a max_age request parameter was provided, the Client MUST check the auth_time Claim
    // value and request re-authentication if it determines too much time has elapsed since the
    // last End-User authentication."

    @Test
    fun `validate should succeed when auth_time is within max_age`() = runTest {
        val validator =
            AuthorizationCodeFlowTokenResponseValidator(
                serializer = Json,
                client = createTestClient { copy(nonce = "0D1ck61") },
                maxAge = 600,
            )
        // auth_time = TEST_INSTANT, now = TEST_INSTANT, elapsed = 0 -> within max_age of 600
        validator.validate(buildTokenResponse(authTime = TEST_INSTANT))
    }

    @Test
    fun `validate should fail when auth_time exceeds max_age`() = runTest {
        val validator =
            AuthorizationCodeFlowTokenResponseValidator(
                serializer = Json,
                client = createTestClient { copy(nonce = "0D1ck61") },
                maxAge = 300,
            )
        // auth_time = TEST_INSTANT - 400, elapsed = 400s, max_age = 300s (with leeway 10s ->
        // allowed up to 310s) -> exceeds
        val e =
            assertFailsWith<TokenTemporalValidationException> {
                validator.validate(buildTokenResponse(authTime = TEST_INSTANT - 400))
            }
        assertEquals("auth_time exceeds max_age", e.message)
    }

    @Test
    fun `validate should fail when auth_time is missing but max_age was requested`() = runTest {
        val validator =
            AuthorizationCodeFlowTokenResponseValidator(
                serializer = Json,
                client = createTestClient { copy(nonce = "0D1ck61") },
                maxAge = 600,
            )
        // No auth_time in the token
        val e =
            assertFailsWith<TokenValidationException> {
                validator.validate(buildTokenResponse(authTime = null))
            }
        assertEquals("auth_time missing but max_age was requested", e.message)
    }

    @Test
    fun `validate should succeed without max_age even when auth_time is absent`() = runTest {
        val validator =
            AuthorizationCodeFlowTokenResponseValidator(
                serializer = Json,
                client = createTestClient { copy(nonce = "0D1ck61") },
                maxAge = null,
            )
        // No max_age -> no auth_time check required
        validator.validate(buildTokenResponse(authTime = null))
    }
}
