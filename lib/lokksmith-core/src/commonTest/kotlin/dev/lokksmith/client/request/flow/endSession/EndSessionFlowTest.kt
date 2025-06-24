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
package dev.lokksmith.client.request.flow.endSession

import dev.lokksmith.client.Id
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.Key
import dev.lokksmith.client.SAMPLE_TOKENS
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.request.OAuthError
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.EphemeralEndSessionFlowState
import dev.lokksmith.client.snapshot.Snapshot.FlowResult
import io.ktor.http.Url
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EndSessionFlowTest {

    @Test
    fun `prepare should prepare End Session Flow`() = runTest {
        val (flow, client) = createFlow { copy(tokens = SAMPLE_TOKENS) }

        val initiation = flow.prepare()

        runCurrent()
        val requestUrl = Url(initiation.requestUrl)

        assertEquals("key", initiation.clientKey)

        assertEquals("https", requestUrl.protocol.name)
        assertEquals("example.com", requestUrl.host)
        assertEquals("/endSessionEndpoint", requestUrl.encodedPath)
        assertEquals("clientId", requestUrl.parameters[Parameter.CLIENT_ID])
        assertEquals(
            "https://example.com/app/redirect",
            requestUrl.parameters[Parameter.POST_LOGOUT_REDIRECT_URI],
        )
        assertEquals("rawIdToken", requestUrl.parameters[Parameter.ID_TOKEN_HINT])
        assertEquals(flow.state, requestUrl.parameters[Parameter.STATE])

        val flowState =
            assertIs<EphemeralEndSessionFlowState>(client.snapshots.value.ephemeralFlowState)
        assertEquals(flow.state, flowState.state)
    }

    @Test
    fun `prepare should append ui_locales parameter`() = runTest {
        val (flow, _) =
            createFlow(
                request =
                    EndSessionFlow.Request(
                        redirectUri = "https://example.com/app/redirect",
                        uiLocales = listOf("de-DE", "de", "en-GB"),
                    )
            )

        val initiation = flow.prepare()
        val requestUrl = Url(initiation.requestUrl)

        assertEquals("de-DE de en-GB", requestUrl.parameters["ui_locales"])
    }

    @Test
    fun `prepare should append logout_hint parameter`() = runTest {
        val (flow, _) =
            createFlow(
                request =
                    EndSessionFlow.Request(
                        redirectUri = "https://example.com/app/redirect",
                        logoutHint = "logoutHint",
                    )
            )

        val initiation = flow.prepare()
        val requestUrl = Url(initiation.requestUrl)

        assertEquals("logoutHint", requestUrl.parameters["logout_hint"])
    }

    @Test
    fun `prepare should append additional parameters`() = runTest {
        val (flow, _) =
            createFlow(
                request =
                    EndSessionFlow.Request(
                        redirectUri = "https://example.com/app/redirect",
                        additionalParameters = mapOf("param1" to "value 1", "param2" to "value 2"),
                    )
            )

        val initiation = flow.prepare()
        val requestUrl = Url(initiation.requestUrl)

        assertTrue(requestUrl.encodedQuery.contains("value+1"), requestUrl.encodedQuery)
        assertEquals("value 1", requestUrl.parameters["param1"])
        assertEquals("value 2", requestUrl.parameters["param2"])
    }

    @Test
    fun `prepare should throw exception when additional parameters contain known OAuth parameters`() =
        runTest {
            val (flow, _) =
                createFlow(
                    request =
                        EndSessionFlow.Request(
                            redirectUri = "https://example.com/app/redirect",
                            additionalParameters =
                                mapOf(Parameter.POST_LOGOUT_REDIRECT_URI to "postLogoutRedirectUri"),
                        )
                )

            val e = assertFailsWith<IllegalArgumentException> { flow.prepare() }

            assertEquals(
                "Parameter \"post_logout_redirect_uri\" is a known OAuth/OIDC parameter",
                e.message,
            )
        }

    @Test
    fun `onResponse should handle successful response`() = runTest {
        val (flow, client) = createFlow { copy(tokens = SAMPLE_TOKENS) }

        assertNotNull(client.tokens.value)

        flow.prepare()
        runCurrent()

        val responseUrl =
            buildUrl {
                    takeFrom("https://example.com/app/redirect")
                    parameters[Parameter.STATE] = flow.state
                }
                .toString()

        flow.onResponse(responseUrl)
        runCurrent()

        assertEquals(FlowResult.Success(state = flow.state), client.snapshots.value.flowResult)
        assertNull(client.tokens.value)
    }

    @Test
    fun `onResponse should handle error in response`() = runTest {
        val (flow, client) = createFlow()

        flow.prepare()
        runCurrent()

        val responseUrl =
            buildUrl {
                    takeFrom("https://example.com/app/redirect")
                    parameters[Parameter.STATE] = flow.state
                    parameters[Parameter.ERROR] = OAuthError.InvalidClient.code
                    parameters[Parameter.ERROR_DESCRIPTION] = "error description"
                    parameters[Parameter.ERROR_URI] = "error URI"
                }
                .toString()

        val exception = assertFailsWith<OAuthResponseException> { flow.onResponse(responseUrl) }

        runCurrent()

        assertEquals(exception.error, OAuthError.InvalidClient)
        assertEquals(exception.errorDescription, "error description")
        assertEquals(exception.errorUri, "error URI")

        assertEquals(
            FlowResult.Error(
                state = flow.state,
                type = FlowResult.Error.Type.OAuth,
                message =
                    """OAuthResponseException(error="invalid_client, errorDescription="error description", errorUri="error URI")""",
                code = OAuthError.InvalidClient.code,
            ),
            client.snapshots.value.flowResult,
        )

        assertNull(client.snapshots.value.ephemeralFlowState)
    }

    @Test
    fun `cancel should cancel flow`() = runTest {
        val (flow, client) = createFlow()

        flow.prepare()
        runCurrent()

        assertNotNull(
            client.snapshots.value.ephemeralFlowState,
            "ephemeralFlowState must not be null",
        )
        assertNull(client.snapshots.value.flowResult, "flowResult must be null")

        flow.cancel()
        runCurrent()

        assertNull(client.snapshots.value.ephemeralFlowState)
        assertEquals(FlowResult.Cancelled(state = flow.state), client.snapshots.value.flowResult)
    }
}

private suspend fun TestScope.createFlow(
    key: Key = "key".asKey(),
    id: Id = "clientId".asId(),
    request: EndSessionFlow.Request =
        EndSessionFlow.Request(redirectUri = "https://example.com/app/redirect"),
    initialSnapshot: Snapshot.() -> Snapshot = { this },
): Pair<EndSessionFlow, InternalClient> {
    val client = createTestClient(key = key, id = id, initialSnapshot = initialSnapshot)

    return Pair(EndSessionFlow.createOrNull(request = request, client = client)!!, client)
}
