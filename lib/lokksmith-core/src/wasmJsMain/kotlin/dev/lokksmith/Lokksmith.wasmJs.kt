/*
 * Copyright 2026 Sven Jacobs
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
package dev.lokksmith

import dev.lokksmith.Lokksmith.Options
import dev.lokksmith.web.completeAuthFlowFromRedirect
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.launch

/**
 * Creates a new [Lokksmith] instance for the Web (Kotlin/Wasm, browser) target.
 *
 * ## Automatic redirect handling
 *
 * On the Web an auth flow is completed via a **full-page redirect**: the browser navigates to the
 * OpenID provider and is redirected back to a same-origin URI, which reloads and restarts the whole
 * application. This differs from Android and iOS, where the response is delivered back into the
 * still-running app. There is therefore no in-memory state to resume from on the Web — the response
 * must be read from the current URL on startup.
 *
 * To keep application code identical across platforms, this is handled for you: when
 * [handleRedirectOnStartup] is `true` (the default), [createLokksmith] launches
 * [completeAuthFlowFromRedirect] on the instance's coroutine scope. If the current URL is a pending
 * redirect response it is completed and the result is persisted to the client; otherwise nothing
 * happens. Either way the outcome is observed through common code via [dev.lokksmith.client.Client]
 * `authFlowResult` / `tokens`, exactly as on the other platforms.
 *
 * Set [handleRedirectOnStartup] to `false` to take manual control and call
 * [completeAuthFlowFromRedirect] yourself:
 * ```kotlin
 * // Automatic (default): the redirect is completed for you on startup.
 * val lokksmith = createLokksmith()
 *
 * // Manual: handle the redirect when and how you choose.
 * val lokksmith = createLokksmith(handleRedirectOnStartup = false)
 * // ... later, e.g. before showing the UI or after your own checks:
 * lokksmith.completeAuthFlowFromRedirect()
 * ```
 *
 * @param options Cross-platform configuration options for the [Lokksmith] instance.
 * @param handleRedirectOnStartup Whether to automatically complete a pending auth/end-session flow
 *   from the current browser URL on startup. Defaults to `true`.
 * @see completeAuthFlowFromRedirect
 */
public fun createLokksmith(
    options: Options = Options(),
    handleRedirectOnStartup: Boolean = true,
): Lokksmith {
    val lokksmith = Lokksmith(platformContext = PlatformContext, options = options)

    if (handleRedirectOnStartup) {
        lokksmith.container.coroutineScope.launch {
            // The error case is intentionally swallowed here: the response handlers persist any
            // OAuth/validation error into the client snapshot before throwing, so it remains
            // observable via Client.authFlowResult.
            runCatching { lokksmith.completeAuthFlowFromRedirect() }
                .onFailure {
                    println("Lokksmith: redirect handling failed\n${it.stackTraceToString()}")
                }
        }
    }

    return lokksmith
}

internal actual val platformHttpClientEngine: HttpClientEngine
    get() = Js.create()
