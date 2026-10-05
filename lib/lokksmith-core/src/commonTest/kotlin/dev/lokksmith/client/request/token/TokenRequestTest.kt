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
package dev.lokksmith.client.request.token

import dev.lokksmith.client.TestProvider
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.ResponseException
import dev.lokksmith.client.request.parameter.GrantType
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class TokenRequestTest {

    @Test
    fun `invoke should expose the status code when the error response has no OAuth error`() =
        runTest {
            val exception =
                assertFailsWith<ResponseException> {
                    requestTokens(
                        status = HttpStatusCode.Forbidden,
                        content = """{"errorCode":403005,"errorMessage":"Unauthorized user"}""",
                    )
                }

            assertEquals(403, exception.statusCode)
            assertEquals(ResponseException.Reason.HttpError, exception.reason)
        }

    @Test
    fun `invoke should expose the status code when the error response body cannot be parsed`() =
        runTest {
            val exception =
                assertFailsWith<ResponseException> {
                    requestTokens(
                        status = HttpStatusCode.InternalServerError,
                        content = "window.location = '/error';",
                        contentType = "text/javascript; charset=utf-8",
                    )
                }

            assertEquals(500, exception.statusCode)
            assertEquals(ResponseException.Reason.HttpError, exception.reason)
        }

    @Test
    fun `invoke should expose the status code when the token type is not Bearer`() = runTest {
        val exception =
            assertFailsWith<ResponseException> {
                requestTokens(
                    status = HttpStatusCode.OK,
                    content = """{"token_type":"MAC","access_token":"Lh0rP8vrtQH"}""",
                )
            }

        assertEquals(200, exception.statusCode)
        assertEquals(ResponseException.Reason.InvalidResponse, exception.reason)
    }

    @Test
    fun `invoke should keep throwing OAuthResponseException for an OAuth error`() = runTest {
        val exception =
            assertFailsWith<OAuthResponseException> {
                requestTokens(
                    status = HttpStatusCode.BadRequest,
                    content = """{"error":"invalid_grant"}""",
                )
            }

        assertEquals(400, exception.statusCode)
    }

    private suspend fun TestScope.requestTokens(
        status: HttpStatusCode,
        content: String,
        contentType: String = "application/json",
    ): TokenResponse {
        val httpClient =
            createHttpClient(
                MockEngine {
                    respond(
                        content = content,
                        status = status,
                        headers = headersOf("Content-Type", contentType),
                    )
                }
            )
        val client = createTestClient(provider = TestProvider(httpClient = httpClient))

        return TokenRequest(client, httpClient).invoke {
            append(Parameter.GRANT_TYPE, GrantType.RefreshToken.value)
        }
    }
}
