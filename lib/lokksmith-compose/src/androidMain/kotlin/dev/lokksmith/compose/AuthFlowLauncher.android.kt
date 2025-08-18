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
import androidx.browser.auth.AuthTabIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import dev.lokksmith.SingletonLokksmithProvider.lokksmith
import dev.lokksmith.android.LokksmithAuthFlowActivity
import dev.lokksmith.android.LokksmithAuthFlowActivity.Companion.getErrorMessageFromIntent
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowUserAgentResponseHandler
import dev.lokksmith.compose.AuthFlowLauncher.PlatformLauncher
import dev.lokksmith.compose.AuthFlowLauncher.PlatformOptions
import dev.lokksmith.compose.AuthFlowLauncher.PlatformOptions.Android.Method
import dev.lokksmith.internal.getRedirectScheme
import dev.lokksmith.internal.getRedirectUri
import io.ktor.http.fullPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public actual fun rememberAuthFlowLauncher(): AuthFlowLauncher {
    lateinit var launcher: AuthFlowLauncher
    var isAuthTab by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val activityLauncher =
        rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            val initiation = checkNotNull(launcher.initiation) { "initiation is null" }
            val responseHandler = AuthFlowUserAgentResponseHandler(lokksmith)

            scope.launch {
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        // If this is not an Auth Tab response we're handling the response the
                        // classic way in LokksmithAuthFlowActivity.
                        if (!isAuthTab) return@launch

                        val data = checkNotNull(result.data) { "result data is null" }

                        responseHandler.onResponse(
                            key = initiation.clientKey,
                            responseUri = data.data.toString(),
                        )
                    }

                    Activity.RESULT_CANCELED -> {
                        val errorMessage = getErrorMessageFromIntent(result.data)

                        when (errorMessage) {
                            null ->
                                responseHandler.onCancel(
                                    key = initiation.clientKey,
                                    state = initiation.state,
                                )

                            else ->
                                responseHandler.onError(
                                    key = initiation.clientKey,
                                    state = initiation.state,
                                    message = errorMessage,
                                )
                        }
                    }

                    AuthTabIntent.RESULT_VERIFICATION_FAILED,
                    AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT,
                    AuthTabIntent.RESULT_UNKNOWN_CODE ->
                        responseHandler.onError(
                            key = initiation.clientKey,
                            state = initiation.state,
                            message = "Auth Tab failed with error code ${result.resultCode}",
                        )
                }
            }
        }

    val platformLauncher =
        AndroidPlatformLauncher(
            context = LocalContext.current,
            activityLauncher = activityLauncher,
            onLaunchBrowser = { options -> isAuthTab = options.android.method is Method.AuthTab },
        )

    launcher = rememberAuthFlowLauncher(platformLauncher)

    return launcher
}

private class AndroidPlatformLauncher(
    private val context: Context,
    private val activityLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val onLaunchBrowser: (options: PlatformOptions) -> Unit,
) : PlatformLauncher {

    override suspend fun launchBrowser(
        initiation: Initiation,
        headers: Map<String, String>,
        options: PlatformOptions,
    ) {
        onLaunchBrowser(options)

        withContext(Dispatchers.Main.immediate) {
            when (val method = options.android.method) {
                Method.CustomTab -> {
                    val intent =
                        LokksmithAuthFlowActivity.createCustomTabsIntent(
                            context = context,
                            initiation = initiation,
                            headers = headers,
                            ephemeralBrowsing = options.android.ephemeralBrowsing,
                        )

                    activityLauncher.launch(intent)
                }

                is Method.AuthTab -> {
                    val authTabIntent =
                        AuthTabIntent.Builder()
                            .setEphemeralBrowsingEnabled(options.android.ephemeralBrowsing)
                            .build()
                    val url = initiation.requestUrl.toUri()

                    when (method.redirect) {
                        Method.AuthTab.Redirect.CustomScheme -> {
                            authTabIntent.launch(
                                activityLauncher,
                                url,
                                getRedirectScheme(initiation.requestUrl),
                            )
                        }

                        Method.AuthTab.Redirect.Https -> {
                            val redirectUri = getRedirectUri(initiation.requestUrl)
                            authTabIntent.launch(
                                activityLauncher,
                                url,
                                redirectUri.host,
                                redirectUri.fullPath,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun logException(msg: String, e: Exception) {
        Log.e("AuthFlowLauncher", msg, e)
    }
}
