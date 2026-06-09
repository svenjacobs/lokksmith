package dev.lokksmith.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.lokksmith.createLokksmith
import dev.lokksmith.web.completeAuthFlowFromRedirect
import kotlinx.browser.document
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val lokksmith = createLokksmith()
    setupApp(lokksmith)

    // After the full-page redirect reloads the app, complete a pending auth/end-session flow from
    // the current URL. The result is persisted to the client snapshot and surfaces once the client
    // is (re)loaded in the UI.
    lokksmith.container.coroutineScope.launch {
        runCatching { lokksmith.completeAuthFlowFromRedirect() }
    }

    ComposeViewport(document.body!!) {
        App(onCopyToClipboard = ::copyToClipboard)
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun copyToClipboard(text: String): Unit = js("navigator.clipboard.writeText(text)")
