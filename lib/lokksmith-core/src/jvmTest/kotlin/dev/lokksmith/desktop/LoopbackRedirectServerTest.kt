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

import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class LoopbackRedirectServerTest {

    @Test
    fun `redirectUri exposes loopback host and assigned port`() = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val uri = server.redirectUri
            assertTrue(
                uri.startsWith("http://127.0.0.1:") || uri.startsWith("http://[::1]:"),
                "expected loopback host, got $uri",
            )
            assertTrue(uri.endsWith("/lokksmith/redirect"), "expected default path, got $uri")
            val port = uri.substringAfterLast(":").substringBefore("/").toInt()
            assertTrue(port > 0, "expected ephemeral port to be assigned, got $port")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `awaitResponseUri returns full response URI on valid GET with matching state`() = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
            val (status, _) = httpGet("${server.redirectUri}?code=auth-xyz&state=state-abc")
            assertEquals(200, status)
            val responseUri = deferred.await()
            assertTrue("code=auth-xyz" in responseUri, "missing code in $responseUri")
            assertTrue("state=state-abc" in responseUri, "missing state in $responseUri")
            assertTrue(responseUri.startsWith(server.redirectUri))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `state mismatch returns 400 and does not resume`(): Unit = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    server.awaitResponseUri(300.milliseconds)
                }
            val (status, _) = httpGet("${server.redirectUri}?code=x&state=wrong")
            assertEquals(400, status)
            assertFailsWith<TimeoutCancellationException> { deferred.await() }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `wrong path returns 404`() = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val baseUrl = server.redirectUri.substringBefore("/lokksmith")
            val (status, _) = httpGet("$baseUrl/foo")
            assertEquals(404, status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `wrong method returns 405`() = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val (status, _) = httpRequest(server.redirectUri, method = "POST")
            assertEquals(405, status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `second valid request after success returns 404`() = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
            val (firstStatus, _) = httpGet("${server.redirectUri}?code=x&state=state-abc")
            assertEquals(200, firstStatus)
            deferred.await()

            val (secondStatus, _) = httpGet("${server.redirectUri}?code=y&state=state-abc")
            assertEquals(404, secondStatus)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `awaitResponseUri throws TimeoutCancellationException on timeout`(): Unit = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            assertFailsWith<TimeoutCancellationException> {
                server.awaitResponseUri(50.milliseconds)
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `custom ResponseHtml is rendered for the success response`(): Unit = runTest {
        val customHtml = "<!DOCTYPE html><html><body><p>Custom branding</p></body></html>"
        val server =
            LoopbackRedirectServer.create(
                expectedState = "state-abc",
                responseHtml = ResponseHtml { customHtml },
            )
        server.start()
        try {
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
            val (status, body) = httpGet("${server.redirectUri}?code=x&state=state-abc")
            assertEquals(200, status)
            assertEquals(customHtml, body)
            deferred.await()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `localhost Host header is rejected end-to-end`() = runTest {
        // `localhost` DNS-resolves to 127.0.0.1, but accepting it would defeat the rebinding
        // defence — the whole point is that a literal IP can't be DNS-rebound.
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val port = server.redirectUri.substringAfterLast(":").substringBefore("/").toInt()
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                val out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                out.write("GET /lokksmith/redirect?code=x&state=state-abc HTTP/1.1\r\n")
                out.write("Host: localhost:$port\r\n")
                out.write("Connection: close\r\n")
                out.write("\r\n")
                out.flush()
                val statusLine =
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                assertTrue(
                    statusLine.startsWith("HTTP/1.1 400"),
                    "expected 400 status line, got: $statusLine",
                )
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `request with mismatched Host header returns 400`() = runTest {
        // HttpURLConnection silently ignores setRequestProperty("Host", ...), so we use a raw
        // socket to simulate a DNS-rebinding attacker who controls the Host header.
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val port = server.redirectUri.substringAfterLast(":").substringBefore("/").toInt()
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                val out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                out.write("GET /lokksmith/redirect?code=x&state=state-abc HTTP/1.1\r\n")
                out.write("Host: evil.example.com:$port\r\n")
                out.write("Connection: close\r\n")
                out.write("\r\n")
                out.flush()
                val statusLine =
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                assertTrue(
                    statusLine.startsWith("HTTP/1.1 400"),
                    "expected 400 status line, got: $statusLine",
                )
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `success response includes no-store and no-referrer headers`(): Unit = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
            val (status, cacheControl, referrerPolicy) =
                httpGetWithSecurityHeaders("${server.redirectUri}?code=x&state=state-abc")
            assertEquals(200, status)
            assertEquals("no-store", cacheControl)
            assertEquals("no-referrer", referrerPolicy)
            deferred.await()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `malformed percent-encoded state returns 400 without resuming`(): Unit = runTest {
        // %ZZ is invalid percent-encoding; URI's strict parser would reject it before sending,
        // so use a raw socket to push the malformed bytes through.
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        try {
            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    server.awaitResponseUri(300.milliseconds)
                }
            val port = server.redirectUri.substringAfterLast(":").substringBefore("/").toInt()
            val statusLine =
                Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                    val out = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                    out.write("GET /lokksmith/redirect?code=x&state=%ZZ HTTP/1.1\r\n")
                    out.write("Host: 127.0.0.1:$port\r\n")
                    out.write("Connection: close\r\n")
                    out.write("\r\n")
                    out.flush()
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                }
            assertTrue(
                statusLine.startsWith("HTTP/1.1 400"),
                "expected 400 status line, got: $statusLine",
            )
            assertFailsWith<TimeoutCancellationException> { deferred.await() }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `awaitResponseUri propagates coroutine cancellation and stops server`() = runTest {
        val server = LoopbackRedirectServer.create(expectedState = "state-abc")
        server.start()
        val redirectUri = server.redirectUri

        val job = launch(start = CoroutineStart.UNDISPATCHED) { server.awaitResponseUri(5.seconds) }
        delay(50)
        job.cancel()
        job.join()

        // Server should be stopped: subsequent connection attempts refused.
        try {
            httpGet("$redirectUri?code=x&state=state-abc")
            fail("expected ConnectException after server cancellation")
        } catch (_: ConnectException) {
            // Expected.
        }
    }

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
            (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
            Triple(status, cacheControl, referrerPolicy)
        } finally {
            conn.disconnect()
        }
    }
}
