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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language

/**
 * Listens on a loopback HTTP port for the OAuth redirect callback as required by RFC 8252 §7.3 and
 * §8.3 ("Loopback Interface Redirection").
 *
 * Behavior:
 * - Bound exclusively to [InetAddress.getLoopbackAddress] — non-loopback connections cannot reach
 *   the server.
 * - Ephemeral port assignment (`port = 0`); the actual port is exposed via [redirectUri] after
 *   [start] completes.
 * - HTTP `GET` only. Other methods receive `405 Method Not Allowed`.
 * - Requests on any path other than [path] receive `404 Not Found`.
 * - Defence-in-depth state check: the `state` query parameter must equal [expectedState], otherwise
 *   `400 Bad Request` is returned and the awaiting coroutine is **not** resumed.
 * - One-shot: once a valid response has been delivered, subsequent requests receive `404`.
 *
 * Local processes can also bind to the loopback interface — the security of the desktop loopback
 * flow rests on PKCE (RFC 7636), which the common code enforces. This server's job is to receive
 * the redirect, not to authenticate the OP.
 */
internal class LoopbackRedirectServer
private constructor(
    private val expectedState: String,
    private val path: String,
    private val successHtml: String,
) {

    init {
        require(expectedState.isNotBlank()) { "expectedState must not be blank" }
        require(path.startsWith("/")) { "path must start with '/'" }
        require('?' !in path && '#' !in path) { "path must not contain '?' or '#'" }
    }

    // Created unbound; binding happens in `start()` so the bound port isn't held before the caller
    // is ready for it.
    private val server: HttpServer = HttpServer.create()

    private val hostValidator by lazy { HostHeaderValidator(server.address) }

    private val continuation = atomic<CancellableContinuation<String>?>(null)

    /**
     * The full HTTP redirect URI to register as `redirect_uri` on the OAuth request, e.g.
     * `http://127.0.0.1:54321/lokksmith/redirect`.
     *
     * Only valid after [start] has been called.
     */
    val redirectUri: String
        get() = "$baseUrl$path"

    private val baseUrl: String
        get() {
            val address = server.address
            val host = address.address.hostAddress
            val hostInUrl = if (':' in host) "[$host]" else host
            return "$HTTP_SCHEME://$hostInUrl:${address.port}"
        }

    /**
     * Binds and starts the loopback HTTP server. Must be called before [redirectUri] is read or
     * [awaitResponseUri] is invoked.
     */
    fun start() {
        server.bind(
            InetSocketAddress(InetAddress.getLoopbackAddress(), EPHEMERAL_PORT),
            DEFAULT_BACKLOG,
        )
        server.createContext(ROOT_CONTEXT, ::handle)
        server.start()
    }

    fun stop() {
        server.stop(STOP_DELAY_SECONDS)
    }

    companion object {
        /**
         * Creates a [LoopbackRedirectServer]. Pre-renders the success page in the caller's
         * coroutine context (so implementations of [ResponseHtml] can do IO) and returns a server
         * ready to be [started][start].
         */
        suspend fun create(
            expectedState: String,
            path: String = DEFAULT_REDIRECT_PATH,
            responseHtml: ResponseHtml = ResponseHtml.Default,
        ): LoopbackRedirectServer =
            LoopbackRedirectServer(
                expectedState = expectedState,
                path = path,
                successHtml = responseHtml.render(),
            )
    }

    /**
     * Suspends until a valid redirect arrives, the [timeout] elapses, or the calling coroutine is
     * cancelled. On timeout or cancellation the underlying server is shut down.
     */
    suspend fun awaitResponseUri(timeout: Duration): String =
        withTimeout(timeout) {
            suspendCancellableCoroutine { cont ->
                continuation.value = cont
                cont.invokeOnCancellation {
                    continuation.value = null
                    stop()
                }
            }
        }

    private fun handle(exchange: HttpExchange) {
        when {
            /*
            TODO:
             I am still not 100% sure if this is something that we need to do
             Host header validation — defence against DNS rebinding. A malicious page could
             DNS-rebind its hostname to 127.0.0.1; the TCP layer would accept because the dest IP
             is loopback, but the Host header would not match our PropertyInfo.Name.bound address.
            */
            !isHostValid(exchange) -> respond(exchange, HTTP_STATUS_BAD_REQUEST, BAD_REQUEST_HTML)
            exchange.requestMethod != HTTP_GET ->
                respond(exchange, HTTP_STATUS_METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_HTML)
            exchange.requestURI.path != path ->
                respond(exchange, HTTP_STATUS_NOT_FOUND, NOT_FOUND_HTML)
            else -> handleCallback(exchange)
        }
    }

    private fun isHostValid(exchange: HttpExchange): Boolean =
        hostValidator.isValid(exchange.requestHeaders.getFirst(HOST_HEADER))

    private fun handleCallback(exchange: HttpExchange) {
        val state = parseStateParameter(exchange.requestURI.rawQuery)
        if (state != expectedState) {
            respond(exchange, HTTP_STATUS_BAD_REQUEST, BAD_REQUEST_HTML)
            return
        }
        val cont = continuation.getAndSet(null)
        if (cont == null) {
            respond(exchange, HTTP_STATUS_NOT_FOUND, NOT_FOUND_HTML)
            return
        }
        val responseUri = "$baseUrl${exchange.requestURI}"
        try {
            respond(exchange, HTTP_STATUS_OK, successHtml)
        } finally {
            cont.resume(responseUri)
        }
    }

    private fun parseStateParameter(rawQuery: String?): String? {
        if (rawQuery == null) return null
        return rawQuery
            .split('&')
            .asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.firstOrNull() == STATE_PARAM }
            ?.takeIf { it.size > 1 }
            ?.let { runCatching { URLDecoder.decode(it[1], StandardCharsets.UTF_8) }.getOrNull() }
    }

    private fun respond(exchange: HttpExchange, code: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add(CONTENT_TYPE_HEADER, CONTENT_TYPE_HTML)
        // The redirect URL contains the auth code; defence-in-depth against caches/Referer leaks.
        exchange.responseHeaders.add(CACHE_CONTROL_HEADER, CACHE_CONTROL_NO_STORE)
        exchange.responseHeaders.add(REFERRER_POLICY_HEADER, REFERRER_POLICY_NO_REFERRER)
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

internal const val DEFAULT_REDIRECT_PATH = "/lokksmith/redirect"

private const val HTTP_SCHEME = "http"
private const val HTTP_GET = "GET"
private const val ROOT_CONTEXT = "/"
private const val EPHEMERAL_PORT = 0
private const val DEFAULT_BACKLOG = 0
private const val STOP_DELAY_SECONDS = 0
private const val HTTP_STATUS_OK = 200
private const val HTTP_STATUS_BAD_REQUEST = 400
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_METHOD_NOT_ALLOWED = 405
private const val STATE_PARAM = "state"
private const val HOST_HEADER = "Host"
private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val CONTENT_TYPE_HTML = "text/html; charset=utf-8"
private const val CACHE_CONTROL_HEADER = "Cache-Control"
private const val CACHE_CONTROL_NO_STORE = "no-store"
private const val REFERRER_POLICY_HEADER = "Referrer-Policy"
private const val REFERRER_POLICY_NO_REFERRER = "no-referrer"

@Language("HTML")
private val NOT_FOUND_HTML =
    """
    <!DOCTYPE html>
    <html lang="en">
      <body>
        <p>Not found.</p>
      </body>
    </html>
    """
        .trimIndent()

@Language("HTML")
private val METHOD_NOT_ALLOWED_HTML =
    """
    <!DOCTYPE html>
    <html lang="en">
      <body>
        <p>Method not allowed.</p>
      </body>
    </html>
    """
        .trimIndent()

@Language("HTML")
private val BAD_REQUEST_HTML =
    """
    <!DOCTYPE html>
    <html lang="en">
      <body>
        <p>Bad request.</p>
      </body>
    </html>
    """
        .trimIndent()
