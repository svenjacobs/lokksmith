package dev.lokksmith.demo

import androidx.compose.ui.window.ComposeUIViewController
import dev.lokksmith.createLokksmith
import platform.UIKit.UIPasteboard

@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController(
    configure = {
        setupApp(
            lokksmith = createLokksmith()
        )
    }
) {
    App(
        onCopyToClipboard = { text ->
            UIPasteboard.generalPasteboard.string = text
        },
    )
}
