package dev.lokksmith.demo

import android.content.ClipData
import android.content.Intent
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
                onIntentCreated = { intent ->
                    /**
                     * Example of how we can force the authentication to be run in a specific
                     * browser even when another browser is set as standard. Note, this will crash
                     * the app if Chrome is not installed.
                     */
                    (intent as Intent).`package` = "com.android.chrome"
                },
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
