package dev.lokksmith.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.lokksmith.createLokksmith
import kotlinx.browser.document
import kotlinx.browser.localStorage

private const val KEY_CLIENT_ID = "demo.clientId"
private const val KEY_DISCOVERY_URL = "demo.discoveryUrl"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // createLokksmith() automatically completes a pending auth/end-session flow from the redirect
    // URL on startup, so no Web-specific handling is needed here — the rest is common code.
    setupApp(createLokksmith())

    ComposeViewport(document.body!!) {
        App(
            onCopyToClipboard = ::copyToClipboard,
            // The full-page redirect restarts the app and clears in-memory UI state, so restore the
            // last selected client (persisted in localStorage) to display the freshly issued tokens.
            initialClientId = localStorage.getItem(KEY_CLIENT_ID),
            initialDiscoveryUrl = localStorage.getItem(KEY_DISCOVERY_URL),
            onClientPersist = { clientId, discoveryUrl ->
                localStorage.setItem(KEY_CLIENT_ID, clientId)
                localStorage.setItem(KEY_DISCOVERY_URL, discoveryUrl)
            },
        )
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun copyToClipboard(text: String): Unit = js("navigator.clipboard.writeText(text)")
