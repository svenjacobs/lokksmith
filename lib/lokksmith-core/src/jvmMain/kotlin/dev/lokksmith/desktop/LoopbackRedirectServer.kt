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

import dev.lokksmith.desktop.LoopbackRedirectServer.Companion.create
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.host
import io.ktor.server.routing.routing
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language

/**
 * Listens on a loopback HTTP port for the OAuth redirect callback as required by RFC 8252 §7.3 and
 * §8.3 ("Loopback Interface Redirection"). Backed by Ktor's embedded CIO server.
 *
 * Behavior:
 * - Bound exclusively to `127.0.0.1` — non-loopback connections cannot reach the server.
 * - Ephemeral port assignment; the actual port is exposed via [redirectUri] after [create].
 * - HTTP `GET` on `path` when the `Host` header matches `127.0.0.1`. Anything else (wrong host,
 *   wrong path, wrong method) falls through to Ktor's default `404`. The Host filter is the
 *   DNS-rebinding defence: a malicious page that DNS-rebinds its hostname to `127.0.0.1` would send
 *   `Host: evil.example.com:PORT`, which doesn't match.
 * - Defence-in-depth `state` query parameter check; mismatched `state` returns `400 Bad Request`
 *   and the awaiting coroutine is **not** resumed.
 * - One-shot: once a valid response has been delivered, subsequent requests receive `404`.
 *
 * Local processes can also bind to the loopback interface — the security of the desktop loopback
 * flow rests on PKCE (RFC 7636), which the common code enforces. This server's job is to receive
 * the redirect, not to authenticate the OP.
 */
internal class LoopbackRedirectServer
private constructor(
    val redirectUri: String,
    private val responseUri: CompletableDeferred<String>,
    private val onClose: () -> Unit,
) : AutoCloseable {

    /**
     * Suspends until a valid redirect arrives, the [timeout] elapses, or the calling coroutine is
     * cancelled. On timeout or cancellation the underlying server is shut down. Use [close]
     * (typically via Kotlin's `use { }`) to release the server in success cases too.
     */
    suspend fun awaitResponseUri(timeout: Duration): String =
        try {
            withTimeout(timeout) { responseUri.await() }
        } catch (e: CancellationException) {
            // Catch only to release the bound port; rethrow preserves cancellation semantics.
            close()
            throw e
        }

    /** Releases the bound port and shuts down the embedded server. Idempotent. */
    override fun close() {
        onClose()
    }

    companion object {
        /**
         * Creates and starts a [LoopbackRedirectServer]. Pre-renders the success page in the
         * caller's coroutine context (so [ResponseHtml] implementations can do IO), binds an
         * ephemeral loopback port, and returns a server with [redirectUri] available.
         *
         * The caller is responsible for [close]-ing the server — typically via Kotlin's `use { }`.
         * On cancellation/timeout from [awaitResponseUri] the server closes itself.
         */
        suspend fun create(
            expectedState: String,
            path: String = DEFAULT_REDIRECT_PATH,
            responseHtml: ResponseHtml = ResponseHtml.Default,
        ): LoopbackRedirectServer {
            require(expectedState.isNotBlank()) { "expectedState must not be blank" }
            require(path.startsWith("/")) { "path must start with '/'" }
            require('?' !in path && '#' !in path) { "path must not contain '?' or '#'" }

            val successHtml = responseHtml.render()
            val responseUri = CompletableDeferred<String>()

            val server =
                embeddedServer(CIO, port = EPHEMERAL_PORT, host = LOOPBACK_HOST) {
                    routing {
                        host(LOOPBACK_HOST) {
                            get(path) { handleCallback(expectedState, successHtml, responseUri) }
                        }
                    }
                }
            server.start(wait = false)
            val stop = { server.stop(STOP_GRACE_MILLIS, STOP_TIMEOUT_MILLIS) }
            val port =
                try {
                    server.engine.resolvedConnectors().first().port
                } catch (t: Throwable) {
                    stop()
                    throw t
                }
            return LoopbackRedirectServer(
                redirectUri = createLoopbackUrl(port, path),
                responseUri = responseUri,
                onClose = stop,
            )
        }
    }
}

internal const val DEFAULT_REDIRECT_PATH = "/callback"

private suspend fun RoutingContext.handleCallback(
    expectedState: String,
    successHtml: String,
    responseUri: CompletableDeferred<String>,
) {
    val state = call.request.queryParameters[STATE_PARAM]
    if (state != expectedState) {
        call.respondHtmlResult(HttpStatusCode.BadRequest, BAD_REQUEST_HTML)
        return
    }

    val uri = createLoopbackUrl(call.request.local.localPort, call.request.uri)
    if (!responseUri.complete(uri)) {
        call.respondHtmlResult(HttpStatusCode.NotFound, NOT_FOUND_HTML)
        return
    }

    call.respondHtmlResult(HttpStatusCode.OK, successHtml)
}

private fun createLoopbackUrl(port: Int, pathAndQuery: String): String =
    "$HTTP_SCHEME://$LOOPBACK_HOST:$port$pathAndQuery"

private suspend fun ApplicationCall.respondHtmlResult(status: HttpStatusCode, html: String) {
    // The redirect URL contains the auth code; defence-in-depth against caches/Referer leaks.
    response.header(HttpHeaders.CacheControl, CACHE_CONTROL_NO_STORE)
    response.header(REFERRER_POLICY_HEADER, REFERRER_POLICY_NO_REFERRER)
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}

private const val HTTP_SCHEME = "http"
private const val LOOPBACK_HOST = "127.0.0.1"
private const val EPHEMERAL_PORT = 0
private const val STOP_GRACE_MILLIS = 0L
private const val STOP_TIMEOUT_MILLIS = 0L
private const val STATE_PARAM = "state"
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
