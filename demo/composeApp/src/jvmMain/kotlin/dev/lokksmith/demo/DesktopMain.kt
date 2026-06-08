/*
 * Copyright 2025 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
