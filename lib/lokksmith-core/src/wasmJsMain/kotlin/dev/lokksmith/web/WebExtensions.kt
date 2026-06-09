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
package dev.lokksmith.web

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowStateResponseHandler
import dev.lokksmith.client.request.parameter.Parameter
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window

/**
 * Launches the authentication flow by navigating the current browser document to the request URL.
 *
 * On the Web the redirect URI is a same-origin URL that is known up-front, so the flow follows the
 * "full-page redirect" pattern: the OpenID provider redirects back to the application, which is
 * reloaded from scratch. Everything required to complete the flow (PKCE code verifier, `state`,
 * `nonce`) has already been persisted to the snapshot store by [Initiation], so navigating away is
 * safe.
 *
 * After the application reloads on the redirect URI, call [completeAuthFlowFromRedirect] during
 * startup to finish the flow.
 *
 * Note: this function does not return — it replaces the current document.
 *
 * @param initiation The initiation parameters produced by preparing an auth flow.
 */
// Declared as a Lokksmith extension for parity with the other platforms (e.g. iOS
// Lokksmith.launchAuthFlow) and a uniform call site, even though the Web implementation needs no
// instance state.
@Suppress("UnusedReceiverParameter")
public fun Lokksmith.launchAuthFlow(initiation: Initiation) {
    window.location.assign(initiation.requestUrl)
}

/**
 * Completes an authentication or end-session flow from the current browser URL after a redirect.
 *
 * Reads the `state` parameter from `window.location` and, if it matches a pending flow in the
 * snapshot store, hands the full URL to [AuthFlowStateResponseHandler] to validate the response and
 * update the client state.
 *
 * **You normally do not need to call this directly:** [dev.lokksmith.createLokksmith] invokes it
 * for you on startup unless created with `handleRedirectOnStartup = false`. Call it manually only
 * when opting out of the automatic handling.
 *
 * The completed outcome (success or error) is observed through common code via
 * [dev.lokksmith.client.Client] `authFlowResult` / `tokens`, the same as on every other platform —
 * not via `rememberAuthFlowLauncher().result`, which is not restored after the full-page reload.
 *
 * @param cleanUrl When `true` (default), removes the OAuth response parameters from the address bar
 *   via `history.replaceState` — preserving any unrelated query parameters and the fragment — so a
 *   page reload does not reprocess the response.
 * @return `true` if the current URL was a recognized redirect response and was handled; `false`
 *   otherwise (e.g. a normal page load without a matching pending flow).
 * @see dev.lokksmith.createLokksmith
 */
@OptIn(ExperimentalWasmJsInterop::class)
public suspend fun Lokksmith.completeAuthFlowFromRedirect(cleanUrl: Boolean = true): Boolean {
    val href = window.location.href
    val state = Url(href).parameters[Parameter.STATE] ?: return false
    if (container.snapshotStore.getForState(state) == null) return false

    try {
        AuthFlowStateResponseHandler(this).onResponse(href)
    } finally {
        // Remove only the OAuth response parameters (preserving any unrelated query parameters and
        // the fragment) so a reload does not reprocess the response. Runs on both success and
        // failure — on failure the error has already been recorded to the client snapshot.
        if (cleanUrl) {
            window.history.replaceState(null, "", hrefWithoutRedirectParameters())
        }
    }

    return true
}

private fun hrefWithoutRedirectParameters(): String =
    URLBuilder(window.location.href)
        .apply { redirectResponseParameters.forEach { parameters.remove(it) } }
        .buildString()

private val redirectResponseParameters =
    listOf(
        Parameter.STATE,
        Parameter.CODE,
        Parameter.ERROR,
        Parameter.ERROR_DESCRIPTION,
        Parameter.ERROR_URI,
        "iss",
        "session_state",
    )
