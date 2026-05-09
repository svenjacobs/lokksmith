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
package dev.lokksmith.desktop

import dev.lokksmith.DesktopOptions
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.request.flow.RedirectUriHandler
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.FlowResult
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class JvmRedirectUriHandlerTest {

    @Test
    fun `resolve returns a 127_0_0_1 URL bound to the configured path`() = runTest {
        val client = createTestClient(initialSnapshot = withAuthorizationCodeEphemeralState())
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options = DesktopOptions(redirectPath = "/auth", redirectTimeout = 5.seconds),
            )

        val resolved =
            handler.resolve(
                requestRedirectUri = "ignored",
                state = STATE,
                purpose = RedirectUriHandler.Purpose.Authorization,
            )

        assertTrue(
            resolved.startsWith("http://127.0.0.1:"),
            "expected loopback host, got $resolved",
        )
        assertTrue(resolved.endsWith("/auth"), "expected configured path, got $resolved")

        handler.release(STATE)
    }

    @Test
    fun `successful redirect writes responseUri into ephemeral state`() = runTest {
        val client = createTestClient(initialSnapshot = withAuthorizationCodeEphemeralState())
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options = DesktopOptions(redirectTimeout = 5.seconds),
            )

        val resolved =
            handler.resolve(
                requestRedirectUri = "ignored",
                state = STATE,
                purpose = RedirectUriHandler.Purpose.Authorization,
            )

        val (status, _) = httpGet("$resolved?code=auth-xyz&state=$STATE")
        assertEquals(200, status)

        runCurrent()

        val ephemeral =
            assertIs<Snapshot.EphemeralAuthorizationCodeFlowState>(
                client.snapshots.value.ephemeralFlowState
            )
        val responseUri = assertNotNull(ephemeral.responseUri)
        assertTrue("code=auth-xyz" in responseUri, "missing code in $responseUri")
        assertTrue("state=$STATE" in responseUri, "missing state in $responseUri")
    }

    @Test
    fun `purpose Authorization renders the authorization response HTML`() = runTest {
        val authorizationHtml = "<html><body>authorization done</body></html>"
        val endSessionHtml = "<html><body>session ended</body></html>"
        val client = createTestClient(initialSnapshot = withAuthorizationCodeEphemeralState())
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options =
                    DesktopOptions(
                        authorizationResponseHtml = ResponseHtml { authorizationHtml },
                        endSessionResponseHtml = ResponseHtml { endSessionHtml },
                        redirectTimeout = 5.seconds,
                    ),
            )

        val resolved =
            handler.resolve(
                requestRedirectUri = "ignored",
                state = STATE,
                purpose = RedirectUriHandler.Purpose.Authorization,
            )

        val (status, body) = httpGet("$resolved?code=auth-xyz&state=$STATE")
        assertEquals(200, status)
        assertEquals(authorizationHtml, body)
    }

    @Test
    fun `purpose EndSession renders the end-session response HTML`() = runTest {
        val authorizationHtml = "<html><body>authorization done</body></html>"
        val endSessionHtml = "<html><body>session ended</body></html>"
        val client = createTestClient(initialSnapshot = withAuthorizationCodeEphemeralState())
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options =
                    DesktopOptions(
                        authorizationResponseHtml = ResponseHtml { authorizationHtml },
                        endSessionResponseHtml = ResponseHtml { endSessionHtml },
                        redirectTimeout = 5.seconds,
                    ),
            )

        val resolved =
            handler.resolve(
                requestRedirectUri = "ignored",
                state = STATE,
                purpose = RedirectUriHandler.Purpose.EndSession,
            )

        val (status, body) = httpGet("$resolved?code=auth-xyz&state=$STATE")
        assertEquals(200, status)
        assertEquals(endSessionHtml, body)
    }

    @Test
    fun `redirect timeout records a generic error on the snapshot`() = runTest {
        val client = createTestClient(initialSnapshot = withAuthorizationCodeEphemeralState())
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options = DesktopOptions(redirectTimeout = 100.milliseconds),
            )

        handler.resolve(
            requestRedirectUri = "ignored",
            state = STATE,
            purpose = RedirectUriHandler.Purpose.Authorization,
        )

        advanceTimeBy(500.milliseconds)
        runCurrent()

        val flowResult = client.snapshots.value.flowResult
        val error = assertIs<FlowResult.Error>(flowResult, "expected Error, got $flowResult")
        assertEquals(STATE, error.state)
        assertEquals(FlowResult.Error.Type.Generic, error.type)
    }

    @Test
    fun `release cancels watcher and tears down the server`() = runTest {
        val client = createTestClient(initialSnapshot = withAuthorizationCodeEphemeralState())
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options = DesktopOptions(redirectTimeout = 5.seconds),
            )

        val resolved =
            handler.resolve(
                requestRedirectUri = "ignored",
                state = STATE,
                purpose = RedirectUriHandler.Purpose.Authorization,
            )
        handler.release(STATE)

        // Server is gone — connection refused.
        assertFailsWith<ConnectException> { httpGet("$resolved?code=x&state=$STATE") }

        // No terminal result was recorded — release is for cooperative cancel; the snapshot
        // finalization happens via `AuthFlowCancellation` in `AbstractAuthFlow.cancel()`.
        assertNull(client.snapshots.value.flowResult)
    }

    @Test
    fun `release before resolve is a no-op`() = runTest {
        val client = createTestClient()
        val handler =
            JvmRedirectUriHandler(
                client = client,
                scope = backgroundScope,
                options = DesktopOptions(),
            )
        // Should not throw or hang.
        handler.release("never-resolved")
    }

    private fun withAuthorizationCodeEphemeralState(): Snapshot.() -> Snapshot = {
        copy(
            ephemeralFlowState =
                Snapshot.EphemeralAuthorizationCodeFlowState(
                    state = STATE,
                    redirectUri = "https://example.com/redirect",
                    codeVerifier = "code-verifier",
                    responseUri = null,
                )
        )
    }

    private fun httpGet(url: String): Pair<Int, String> {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 1_000
            conn.readTimeout = 1_000
            val status = conn.responseCode
            val body =
                (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() } ?: ""
            status to body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val STATE = "state-abc"
    }
}
