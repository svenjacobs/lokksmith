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
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.RedirectUriHandler
import dev.lokksmith.client.request.flow.recordError
import dev.lokksmith.client.request.flow.recordResponseUri
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * JVM/Desktop [RedirectUriHandler] that binds an ephemeral loopback HTTP server (RFC 8252 §7.3) per
 * auth flow and forwards the eventual response URI into the client's snapshot via
 * [recordResponseUri] — the same channel mobile platforms use.
 */
internal class JvmRedirectUriHandler(
    private val client: InternalClient,
    private val scope: CoroutineScope,
    private val options: DesktopOptions,
) : RedirectUriHandler {

    private class Resources(val server: LoopbackRedirectServer, val watcher: Job)

    private val resources = ConcurrentHashMap<String, Resources>()

    override suspend fun resolve(
        requestRedirectUri: String,
        state: String,
        purpose: RedirectUriHandler.Purpose,
    ): String {
        val responseHtml =
            when (purpose) {
                RedirectUriHandler.Purpose.Authorization -> options.authorizationResponseHtml
                RedirectUriHandler.Purpose.EndSession -> options.endSessionResponseHtml
            }
        val server =
            LoopbackRedirectServer.create(
                expectedState = state,
                path = options.redirectPath,
                responseHtml = responseHtml,
            )

        val watcher =
            scope.launch {
                try {
                    val responseUri = server.awaitResponseUri(options.redirectTimeout)
                    client.recordResponseUri(responseUri)
                } catch (e: TimeoutCancellationException) {
                    // TimeoutCancellationException is a CancellationException — must precede the
                    // CancellationException catch below, otherwise the timeout is silently
                    // swallowed.
                    withContext(NonCancellable) {
                        runCatching {
                            client.recordError(
                                state = state,
                                message = e.message ?: "Redirect timed out",
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    withContext(NonCancellable) {
                        runCatching { client.recordError(state = state, message = e.message) }
                    }
                }
            }

        // invokeOnCompletion fires even when the watcher is cancelled before it starts (e.g. when
        // release() runs before the dispatcher gets to it), so it's the right hook for cleanup.
        watcher.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                // Cancel path: there's no response we want to flush, so close immediately.
                // close(0, 0) returns quickly, which is acceptable to run on whatever thread
                // completes the watcher.
                runCatching { server.close(gracePeriodMillis = 0L, timeoutMillis = 0L) }
            } else {
                // Normal completion: close gracefully so the success/error HTML flushes to the
                // browser. stop() with a non-zero grace blocks, so dispatch onto IO to avoid
                // pinning the thread that completed the job (often Main on a Compose UI scope).
                scope.launch(NonCancellable + Dispatchers.IO) { runCatching { server.close() } }
            }
        }

        resources.put(state, Resources(server, watcher))?.watcher?.cancel()

        return server.redirectUri
    }

    override suspend fun release(state: String) {
        val resource = resources.remove(state) ?: return
        resource.watcher.cancel()
        resource.watcher.join()
    }
}
