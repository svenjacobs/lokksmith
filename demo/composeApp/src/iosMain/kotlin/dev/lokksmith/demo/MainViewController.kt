package dev.lokksmith.demo

import androidx.compose.ui.window.ComposeUIViewController
import dev.lokksmith.createLokksmith
import dev.lokksmith.demo.Container.lokksmith
import dev.lokksmith.demo.Container.mainScope
import dev.lokksmith.ios.launchAuthFlow
import kotlinx.coroutines.launch

@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController(
    configure = { lokksmith = createLokksmith() }
) {
    App(
        onStartAuthFlow = { authFlow ->
            mainScope.launch {
                lokksmith.launchAuthFlow(authFlow.initiation)
            }
        },
        onCopyToClipboard = { /* TODO */ },
    )
}
