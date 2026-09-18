package dev.lokksmith.demo

import android.content.ClipData
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
            val clipboard = LocalClipboard.current

            App(
                /**
                 * Example of how we can force the authentication to be run in a specific browser
                 * even when another browser is set as standard. A package that is not installed
                 * fails the flow rather than crashing the app.
                 */
                browserPackage = "com.android.chrome",
                onCopyToClipboard = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData.newPlainText("Token", it).toClipEntry(),
                        )
                    }
                }
            )
        }
    }
}
