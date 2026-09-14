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
package dev.lokksmith.client.request.flow

import dev.lokksmith.client.Client
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.createHttpClient
import dev.lokksmith.createTestLokksmith
import dev.lokksmith.mockMetadata
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.Parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class AuthFlowStateResponseHandlerTest {

    @Test
    fun `onResponse should exchange code with persisted client options`() = runTest {
        val options =
            Client.Options(additionalTokenRequestParameters = mapOf("httpStatusCodes" to "true"))
        val formData = exchangeCode(options)

        assertEquals("true", formData["httpStatusCodes"])
    }

    @Test
    fun `onResponse should exchange code with default options if none were persisted`() = runTest {
        val formData = exchangeCode(clientOptions = null)

        assertNull(formData["httpStatusCodes"])
    }

    private suspend fun TestScope.exchangeCode(clientOptions: Client.Options?): Parameters {
        var formData: Parameters? = null
        val (lokksmith, snapshotStore) =
            createTestLokksmith { container ->
                container.copy(
                    httpClient =
                        createHttpClient(
                            MockEngine { request ->
                                formData = (request.body as FormDataContent).formData
                                respondBadRequest()
                            }
                        )
                )
            }

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot =
                Snapshot(
                    key = key,
                    id = "clientId".asId(),
                    metadata = mockMetadata,
                    ephemeralFlowState =
                        Snapshot.EphemeralAuthorizationCodeFlowState(
                            state = "i0aMAY0V",
                            redirectUri = "https://example.com/redirect",
                            codeVerifier = "2XCBMopbO8",
                            responseUri = null,
                            clientOptions = clientOptions,
                        ),
                ),
        )

        // The token endpoint responds with an error, only the sent request matters here.
        runCatching {
            AuthFlowStateResponseHandler(lokksmith)
                .onResponse("https://example.com/redirect?code=B5ueWoUdeT&state=i0aMAY0V")
        }

        return assertNotNull(formData)
    }
}
