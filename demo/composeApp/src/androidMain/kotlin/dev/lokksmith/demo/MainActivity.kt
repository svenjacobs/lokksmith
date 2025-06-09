package dev.lokksmith.demo

import android.content.ClipData
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import co.touchlab.kermit.Logger
import dev.lokksmith.android.rememberAuthFlowLauncher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val launcher = rememberAuthFlowLauncher()
            val scope = rememberCoroutineScope()
            val clipboard = LocalClipboard.current

            LaunchedEffect(launcher.result) {
                Logger.d("MainActivity") { "Received auth flow result: ${launcher.result}" }
            }

            App(
                onStartAuthFlow = { flow ->
                    scope.launch {
                        launcher.launch(flow.initiation)
                    }
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
