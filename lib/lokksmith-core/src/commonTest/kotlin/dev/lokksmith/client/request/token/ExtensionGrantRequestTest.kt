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

import dev.lokksmith.client.Client
import dev.lokksmith.client.TestProvider
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import dev.lokksmith.client.request.OAuthError
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.ResponseException
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionGrantRequestTest {

    private val httpJson = Json { prettyPrint = true }
    private val jwtEncoder = JwtEncoder(Json)

    private fun tokenResponseJson(nonce: String = "n0nc3", noIdToken: Boolean = false) =
        httpJson.encodeToString(
            TokenResponse(
                tokenType = "Bearer",
                accessToken = "ext-access-token",
                expiresIn = 600,
                refreshToken = "ext-refresh-token",
                idToken =
                    if (noIdToken) null
                    else
                        jwtEncoder.encode(
                            Jwt(
                                header = Jwt.Header(alg = "none"),
                                payload =
                                    Jwt.Payload(
                                        iss = "issuer",
                                        sub = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                                        aud = listOf("clientId"),
                                        exp = 1748706999 + 600,
                                        iat = 1748706999,
                                        extra = mapOf("nonce" to JsonPrimitive(nonce)),
                                    ),
                            )
                        ),
            )
        )

    private suspend fun TestScope.newExtensionGrantRequest(
        engine: MockEngine,
        options: Client.Options = Client.Options(),
    ): ExtensionGrantRequest {
        val httpClient = createHttpClient(engine)
        val client =
            createTestClient(
                provider =
                    TestProvider(
                        httpClient = httpClient,
                        timeProvider = { Instant.fromEpochSeconds(1748706999, 0) },
                    ),
                options = options,
            )
        return ExtensionGrantRequestImpl(
            client = client,
            httpClient = httpClient,
            serializer = Json,
        )
    }

    @Test
    fun `invoke should submit grant_type and client_id and caller parameters`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/tokenEndpoint" -> {
                    val body = assertIs<FormDataContent>(request.body)

                    assertEquals("custom_grant", body.formData[Parameter.GRANT_TYPE])
                    assertEquals("clientId", body.formData[Parameter.CLIENT_ID])
                    assertEquals("bar", body.formData["foo"])

                    respond(
                        content = tokenResponseJson(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respondBadRequest()
            }
        }

        newExtensionGrantRequest(engine)("custom_grant", mapOf("foo" to "bar"))
    }

    @Test
    fun `invoke should append additional token request parameters alongside caller parameters`() =
        runTest {
            val engine = MockEngine { request ->
                when (request.url.toString()) {
                    "https://example.com/tokenEndpoint" -> {
                        val body = assertIs<FormDataContent>(request.body)

                        assertEquals("custom_grant", body.formData[Parameter.GRANT_TYPE])
                        assertEquals("clientId", body.formData[Parameter.CLIENT_ID])
                        assertEquals("bar", body.formData["foo"])
                        assertEquals("true", body.formData["httpStatusCodes"])

                        respond(
                            content = tokenResponseJson(),
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }

                    else -> respondBadRequest()
                }
            }

            newExtensionGrantRequest(
                engine = engine,
                options =
                    Client.Options(
                        additionalTokenRequestParameters = mapOf("httpStatusCodes" to "true")
                    ),
            )("custom_grant", mapOf("foo" to "bar"))
        }

    @Test
    fun `invoke should throw when a parameter is named grant_type`() = runTest {
        val engine = MockEngine { respondBadRequest() }

        assertFailsWith<IllegalArgumentException> {
            newExtensionGrantRequest(engine)("custom_grant", mapOf(Parameter.GRANT_TYPE to "other"))
        }
    }

    @Test
    fun `invoke should throw when a parameter is named client_id`() = runTest {
        val engine = MockEngine { respondBadRequest() }

        assertFailsWith<IllegalArgumentException> {
            newExtensionGrantRequest(engine)("custom_grant", mapOf(Parameter.CLIENT_ID to "other"))
        }
    }

    @Test
    fun `invoke should throw when a parameter collides with an additional token request parameter`() =
        runTest {
            val engine = MockEngine { respondBadRequest() }

            assertFailsWith<IllegalArgumentException> {
                newExtensionGrantRequest(
                    engine = engine,
                    options =
                        Client.Options(
                            additionalTokenRequestParameters = mapOf("httpStatusCodes" to "true")
                        ),
                )("custom_grant", mapOf("httpStatusCodes" to "false"))
            }
        }

    @Test
    fun `invoke should throw when grantType is blank`() = runTest {
        val engine = MockEngine { respondBadRequest() }

        assertFailsWith<IllegalArgumentException> {
            newExtensionGrantRequest(engine)("   ", emptyMap())
        }
    }

    @Test
    fun `invoke should throw OAuthResponseException on OAuth error response`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/tokenEndpoint" -> {
                    val response =
                        TokenErrorResponse(
                            error = OAuthError.InvalidGrant.code,
                            errorDescription = "error description",
                            errorUri = "error URI",
                        )

                    respond(
                        content = httpJson.encodeToString(response),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respondBadRequest()
            }
        }

        val exception =
            assertFailsWith<OAuthResponseException> {
                newExtensionGrantRequest(engine)("custom_grant", emptyMap())
            }

        assertEquals(OAuthError.InvalidGrant, exception.error)
        assertEquals("error description", exception.errorDescription)
        assertEquals("error URI", exception.errorUri)
    }

    @Test
    fun `invoke should throw ResponseException when token type is not Bearer`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/tokenEndpoint" -> {
                    val response =
                        TokenResponse(tokenType = "MAC", accessToken = "ext-access-token")

                    respond(
                        content = httpJson.encodeToString(response),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respondBadRequest()
            }
        }

        assertFailsWith<ResponseException> {
            newExtensionGrantRequest(engine)("custom_grant", emptyMap())
        }
    }

    @Test
    fun `invoke should throw TokenValidationException when ID Token is missing`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/tokenEndpoint" -> {
                    respond(
                        content = tokenResponseJson(noIdToken = true),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respondBadRequest()
            }
        }

        assertFailsWith<TokenValidationException> {
            newExtensionGrantRequest(engine)("custom_grant", emptyMap())
        }
    }
}
