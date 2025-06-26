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
package dev.lokksmith.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.lokksmith.SingletonLokksmithProvider.lokksmith
import dev.lokksmith.android.LokksmithAuthFlowActivity
import dev.lokksmith.android.LokksmithAuthFlowActivity.Companion.getErrorMessageFromIntent
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowUserAgentResponseHandler
import dev.lokksmith.compose.AuthFlowLauncher.PlatformLauncher
import dev.lokksmith.compose.AuthFlowLauncher.PlatformOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public actual fun rememberAuthFlowLauncher(): AuthFlowLauncher {
    lateinit var launcher: AuthFlowLauncher
    val scope = rememberCoroutineScope()
    val activityLauncher =
        rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                val initiation = launcher.initiation ?: return@rememberLauncherForActivityResult

                scope.launch {
                    val errorMessage = getErrorMessageFromIntent(result.data)
                    val responseHandler = AuthFlowUserAgentResponseHandler(lokksmith)

                    with(responseHandler) {
                        when (errorMessage) {
                            null -> onCancel(initiation.clientKey)
                            else -> onError(initiation.clientKey, errorMessage)
                        }
                    }
                }
            }
        }

    val platformLauncher =
        AndroidPlatformLauncher(context = LocalContext.current, activityLauncher = activityLauncher)

    launcher = rememberAuthFlowLauncher(platformLauncher)

    return launcher
}

private class AndroidPlatformLauncher(
    private val context: Context,
    private val activityLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
) : PlatformLauncher {

    override suspend fun launchBrowser(
        initiation: Initiation,
        headers: Map<String, String>,
        options: PlatformOptions,
    ) {
        withContext(Dispatchers.Main.immediate) {
            val intent =
                LokksmithAuthFlowActivity.createCustomTabsIntent(
                    context = context,
                    url = initiation.requestUrl,
                    clientKey = initiation.clientKey,
                    headers = headers,
                )

            activityLauncher.launch(intent)
        }
    }

    override fun logException(msg: String, e: Exception) {
        Log.e("AuthFlowLauncher", msg, e)
    }
}
