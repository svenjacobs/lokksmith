package dev.lokksmith.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.lokksmith.createLokksmith
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // createLokksmith() automatically completes a pending auth/end-session flow from the redirect
    // URL on startup, so no Web-specific handling is needed here — the rest is common code.
    setupApp(createLokksmith())

    ComposeViewport(document.body!!) {
        App(onCopyToClipboard = ::copyToClipboard)
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun copyToClipboard(text: String): Unit = js("navigator.clipboard.writeText(text)")
