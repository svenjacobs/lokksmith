package dev.lokksmith.demo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.lokksmith.DataDirectory
import dev.lokksmith.createLokksmith
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun main() = application {
    setupApp(
        createLokksmith(dataDirectory = DataDirectory.Default("lokksmith-demo"))
    )


    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 480.dp, height = 800.dp),
        title = "Lokksmith Demo",
    ) {
        App(
            onCopyToClipboard = { text ->
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            },
        )
    }
}
