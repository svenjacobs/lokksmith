package dev.lokksmith.demo

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    App(
        onStartAuthFlow = { TODO() },
        onCopyToClipboard = { TODO() },
    )
}
