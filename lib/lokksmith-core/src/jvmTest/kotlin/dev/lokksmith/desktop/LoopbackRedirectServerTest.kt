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

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * These tests deliberately use `runBlocking` instead of `runTest`.
 *
 * They drive a real Ktor server over real sockets, and issue the requests with blocking
 * `HttpURLConnection` calls on the test thread. `runTest`'s virtual clock can't see any of that:
 * the timeouts inside [LoopbackRedirectServer.awaitResponseUri] would be measured in virtual time,
 * and as soon as the test coroutine suspends — awaiting the response URI, say — the scheduler finds
 * nothing runnable and fast-forwards the clock past the timeout, even though the server thread is
 * microseconds away from completing the deferred. That made `second valid request after success
 * returns 404` fail intermittently on CI with "Timed out after 5s of _virtual_ time". Real time is
 * the only clock that means anything here, and the handful of sub-second waits it costs are worth
 * the determinism.
 */
class LoopbackRedirectServerTest {

    @Test
    fun `redirectUri exposes loopback host and assigned port`() = runBlocking {
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val uri = server.redirectUri
            assertTrue(uri.startsWith("http://127.0.0.1:"), "expected loopback host, got $uri")
            assertTrue(uri.endsWith("/callback"), "expected default path, got $uri")
            assertTrue(
                server.port > 0,
                "expected ephemeral port to be assigned, got ${server.port}",
            )
        }
    }

    @Test
    fun `awaitResponseUri returns full response URI on valid GET with matching state`() =
        runBlocking {
            LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
                val deferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        server.awaitResponseUri(5.seconds)
                    }
                val (status, _) = httpGet("${server.redirectUri}?code=auth-xyz&state=state-abc")
                assertEquals(200, status)
                val responseUri = deferred.await()
                assertTrue("code=auth-xyz" in responseUri, "missing code in $responseUri")
                assertTrue("state=state-abc" in responseUri, "missing state in $responseUri")
                assertTrue(responseUri.startsWith(server.redirectUri))
            }
        }

    @Test
    fun `state mismatch returns 400 and does not resume`(): Unit = runBlocking {
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    server.awaitResponseUri(300.milliseconds)
                }
            val (status, _) = httpGet("${server.redirectUri}?code=x&state=wrong")
            assertEquals(400, status)
            assertFailsWith<TimeoutCancellationException> { deferred.await() }
        }
    }

    @Test
    fun `wrong path returns 404`() = runBlocking {
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val baseUrl = server.redirectUri.substringBefore("/callback")
            val (status, _) = httpGet("$baseUrl/foo")
            assertEquals(404, status)
        }
    }

    @Test
    fun `wrong method is rejected`() = runBlocking {
        // Ktor's routing returns 404 for unhandled methods; spec-wise either 404 or 405 is fine for
        // our case since the only thing that matters is rejection.
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val (status, _) = httpRequest(server.redirectUri, method = "POST")
            assertTrue(status in 400..499, "expected 4xx rejection, got $status")
            assertNotEquals(200, status)
        }
    }

    @Test
    fun `second valid request after success returns 404`() = runBlocking {
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
            val (firstStatus, _) = httpGet("${server.redirectUri}?code=x&state=state-abc")
            assertEquals(200, firstStatus)
            deferred.await()

            val (secondStatus, _) = httpGet("${server.redirectUri}?code=y&state=state-abc")
            assertEquals(404, secondStatus)
        }
    }

    @Test
    fun `awaitResponseUri throws TimeoutCancellationException on timeout`(): Unit = runBlocking {
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            assertFailsWith<TimeoutCancellationException> {
                server.awaitResponseUri(50.milliseconds)
            }
        }
    }

    @Test
    fun `custom ResponseHtml is rendered for the success response`(): Unit = runBlocking {
        val customHtml = "<!DOCTYPE html><html><body><p>Custom branding</p></body></html>"
        LoopbackRedirectServer.create(
                expectedState = "state-abc",
                responseHtml = ResponseHtml { customHtml },
            )
            .use { server ->
                val deferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        server.awaitResponseUri(5.seconds)
                    }
                val (status, body) = httpGet("${server.redirectUri}?code=x&state=state-abc")
                assertEquals(200, status)
                assertEquals(customHtml, body)
                deferred.await()
            }
    }

    @Test
    fun `localhost Host header is rejected`() = runBlocking {
        // `localhost` DNS-resolves to 127.0.0.1, but accepting it would defeat the rebinding
        // defence — the whole point is that a literal IP can't be DNS-rebound. Ktor's host()
        // routing filter falls through to default 404 on host mismatch.
        assertHostHeaderRejected("localhost")
    }

    @Test
    fun `arbitrary hostname is rejected`() = runBlocking {
        // Simulates a DNS-rebinding attacker who DNS-resolves their hostname to 127.0.0.1.
        assertHostHeaderRejected("evil.example.com")
    }

    private suspend fun assertHostHeaderRejected(hostHeader: String) {
        // HttpURLConnection silently ignores setRequestProperty("Host", ...), so we use a raw
        // socket to control the Host header.
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val port = server.port
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                val out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                out.write("GET /callback?code=x&state=state-abc HTTP/1.1\r\n")
                out.write("Host: $hostHeader:$port\r\n")
                out.write("Connection: close\r\n")
                out.write("\r\n")
                out.flush()
                val statusLine =
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                assertTrue(
                    statusLine.startsWith("HTTP/1.1 4"),
                    "expected 4xx rejection, got: $statusLine",
                )
            }
        }
    }

    @Test
    fun `success response includes no-store and no-referrer headers`(): Unit = runBlocking {
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
            val (status, cacheControl, referrerPolicy) =
                httpGetWithSecurityHeaders("${server.redirectUri}?code=x&state=state-abc")
            assertEquals(200, status)
            assertEquals("no-store", cacheControl)
            assertEquals("no-referrer", referrerPolicy)
            deferred.await()
        }
    }

    @Test
    fun `malformed percent-encoded state is rejected without resuming`(): Unit = runBlocking {
        // %ZZ is invalid percent-encoding; URI's strict parser would reject it before sending,
        // so use a raw socket to push the malformed bytes through. Ktor returns 4xx or 5xx
        // depending on where the parse fails — either way the continuation must NOT resume.
        LoopbackRedirectServer.create(expectedState = "state-abc").use { server ->
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    server.awaitResponseUri(300.milliseconds)
                }
            val port = server.port
            val statusLine =
                Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                    val out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                    out.write("GET /callback?code=x&state=%ZZ HTTP/1.1\r\n")
                    out.write("Host: 127.0.0.1:$port\r\n")
                    out.write("Connection: close\r\n")
                    out.write("\r\n")
                    out.flush()
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                }
            assertTrue(
                statusLine.startsWith("HTTP/1.1 4") || statusLine.startsWith("HTTP/1.1 5"),
                "expected 4xx or 5xx error response, got: $statusLine",
            )
            assertFailsWith<TimeoutCancellationException> { deferred.await() }
        }
    }

    @Test
    fun `awaitResponseUri propagates coroutine cancellation and closes server`() = runBlocking {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        val redirectUri = server.redirectUri

        val job = launch(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
        delay(50)
        job.cancel()
        job.join()

        // Server should be stopped (await closed it on cancellation): subsequent connection
        // attempts fail.
        assertServesNothing("$redirectUri?code=x&state=state-abc")
    }

    /**
     * Asserts that [url] can no longer be served, retrying until the port is released or [timeout]
     * elapses.
     *
     * `stop()` returning doesn't mean the listening socket is closed at the OS level yet, so which
     * failure surfaces depends on timing: once the socket is gone the connect is refused
     * (`ConnectException`), but a connection already accepted into the backlog is reset instead
     * (`SocketException: Connection reset by peer`) — which is what made this flaky on CI. Any
     * connection-level [IOException] proves the point; what matters is that no response is served.
     */
    private suspend fun assertServesNothing(url: String, timeout: Duration = 5.seconds) {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            val (status, _) =
                try {
                    httpGet(url)
                } catch (_: IOException) {
                    return
                }
            if (deadline.hasPassedNow()) {
                fail("expected connection to $url to fail after cancellation, got status $status")
            }
            delay(50)
        }
    }

    /** Parses the bound port out of [LoopbackRedirectServer.redirectUri]. */
    private val LoopbackRedirectServer.port: Int
        get() = redirectUri.substringAfterLast(":").substringBefore("/").toInt()

    private fun httpGet(url: String): Pair<Int, String> = httpRequest(url, method = "GET")

    private fun httpRequest(url: String, method: String): Pair<Int, String> {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = method
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

    private fun httpGetWithSecurityHeaders(url: String): Triple<Int, String?, String?> {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 1_000
            conn.readTimeout = 1_000
            val status = conn.responseCode
            // getHeaderField is case-insensitive, unlike headerFields which is case-preserved.
            val cacheControl = conn.getHeaderField("Cache-Control")
            val referrerPolicy = conn.getHeaderField("Referrer-Policy")
            // Drain the body so the connection is fully consumed before disconnect.
            (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
            Triple(status, cacheControl, referrerPolicy)
        } finally {
            conn.disconnect()
        }
    }
}
