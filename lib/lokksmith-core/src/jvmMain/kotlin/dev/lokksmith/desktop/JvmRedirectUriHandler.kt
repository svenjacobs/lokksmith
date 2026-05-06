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

    override suspend fun resolve(requestRedirectUri: String, state: String): String {
        val server =
            LoopbackRedirectServer.create(
                expectedState = state,
                path = options.redirectPath,
                responseHtml = options.responseHtml,
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

        watcher.invokeOnCompletion { server.runCatching { close() } }

        resources.put(state, Resources(server, watcher))?.watcher?.cancel()

        return server.redirectUri
    }

    override suspend fun release(state: String) {
        val resource = resources.remove(state) ?: return
        resource.watcher.cancel()
        resource.watcher.join()
    }
}
