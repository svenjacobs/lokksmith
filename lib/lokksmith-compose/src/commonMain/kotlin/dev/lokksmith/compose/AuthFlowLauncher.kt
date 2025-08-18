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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.lokksmith.SingletonLokksmithProvider.lokksmith
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowResultProvider
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result
import dev.lokksmith.client.request.flow.AuthFlowStateResponseHandler
import dev.lokksmith.compose.AuthFlowLauncher.PlatformLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Stable
public class AuthFlowLauncher
internal constructor(
    private val platformLauncher: PlatformLauncher,
    private val scope: CoroutineScope,
    internal val resultState: MutableState<Result>,
    internal var initiation: Initiation? = null,
) {
    public data class PlatformOptions(val android: Android = Android(), val iOS: Ios = Ios()) {
        public data class Android(
            /**
             * Method to be used.
             *
             * Either Custom Tab or Auth Tab. See Android documentation of Custom Tab / Auth Tab for
             * more details. Note that Auth Tab might not be supported on all smartphones.
             */
            public val method: Method = Method.CustomTab,

            /**
             * Specifies whether to use an Ephemeral Tab which launches a fully isolated web browser
             * within the app, which doesn't retain any data (cookies) and does not sync them with
             * the system browser. Just like a private browsing session.
             */
            public val ephemeralBrowsing: Boolean = false,
        ) {
            public sealed interface Method {

                /** Opens authentication in a (Chrome) Custom Tab. */
                public data object CustomTab : Method

                /**
                 * Opens authentication in an
                 * [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab).
                 *
                 * Please note that on devices which don't support Auth Tab, a Custom Tab will be
                 * used as a
                 * [fallback solution](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab#fallback_to_custom_tabs).
                 * Hence the app setup/configuration regarding Custom Tab must be present.
                 */
                public data class AuthTab(public val redirect: Redirect = Redirect.CustomScheme) :
                    Method {

                    public enum class Redirect {
                        CustomScheme,
                        Https,
                    }
                }
            }
        }

        public data class Ios(
            /**
             * Specifies whether to use an ephemeral browsing session which launches a fully
             * isolated web browser within the app, which doesn't retain any data (cookies) and does
             * not sync them with the system browser. Just like a private browsing session.
             */
            public val prefersEphemeralWebBrowserSession: Boolean = false
        )
    }

    internal interface PlatformLauncher {
        suspend fun launchBrowser(
            initiation: Initiation,
            headers: Map<String, String>,
            options: PlatformOptions,
        )

        fun logException(msg: String, e: Exception)
    }

    /**
     * Observable result of the current flow progress.
     *
     * @see AuthFlowResultProvider.forClient
     */
    public val result: Result
        get() = resultState.value

    private var job: Job? = null

    internal fun onStart() {
        initiation?.let { scope.watchClientState(it) }
    }

    internal fun onStop() {
        cancel()
    }

    /**
     * Starts the authentication flow by launching the request URL in a Custom Tab browser on
     * Android and a `ASWebAuthenticationSession` on iOS. The progress can be observed via [result],
     * which is backed by Compose state.
     *
     * @param initiation The initiation parameters for the auth flow, including the client key and
     *   request URL.
     * @param headers Extra headers that are passed to the browser Tab. See documentation of Custom
     *   Tabs, especially regarding CORS.
     * @param options Additional platform-specific options.
     * @see result
     */
    public suspend fun launch(
        initiation: Initiation,
        headers: Map<String, String> = emptyMap(),
        options: PlatformOptions = PlatformOptions(),
    ) {
        this.initiation = initiation
        scope.watchClientState(initiation)
        platformLauncher.launchBrowser(
            initiation = initiation,
            headers = headers,
            options = options,
        )
    }

    private fun cancel() {
        job?.cancel()
        job = null
    }

    private fun CoroutineScope.watchClientState(initiation: Initiation) {
        cancel()

        job =
            launch(SupervisorJob()) {
                val client = getClient(initiation.clientKey)

                launch {
                    client.snapshots
                        .map { it.ephemeralFlowState?.responseUri }
                        .filterNotNull()
                        .distinctUntilChanged()
                        .collect { responseUri ->
                            // We're catching all exceptions here because we assume that in case of
                            // an error the client's result state has been updated accordingly.
                            try {
                                AuthFlowStateResponseHandler(lokksmith).onResponse(responseUri)
                            } catch (e: Exception) {
                                platformLauncher.logException(
                                    "Received exception in AuthFlowStateResponseHandler.onResponse()",
                                    e,
                                )
                            }
                        }
                }

                AuthFlowResultProvider.forClient(client).collect { result ->
                    resultState.value = result
                }
            }
    }

    private suspend fun getClient(key: String): InternalClient =
        checkNotNull(lokksmith.get(key)) { "client with key not found" } as InternalClient
}

@Composable public expect fun rememberAuthFlowLauncher(): AuthFlowLauncher

@Composable
internal fun rememberAuthFlowLauncher(platformLauncher: PlatformLauncher): AuthFlowLauncher {
    val scope = rememberCoroutineScope()
    val saver = run {
        val resultKey = "result"
        val initiationStateKey = "initiationState"
        val initiationRequestUrlKey = "initiationRequestUrl"
        val initiationClientKeyKey = "initiationClientKey"

        val serializer = lokksmith.container.serializer

        mapSaver(
            save = {
                mapOf(
                    resultKey to serializer.encodeToString(it.resultState.value),
                    initiationStateKey to it.initiation?.state,
                    initiationRequestUrlKey to it.initiation?.requestUrl,
                    initiationClientKeyKey to it.initiation?.clientKey,
                )
            },
            restore = {
                val initiationState = it[initiationStateKey] as String?
                val initiationRequestUrl = it[initiationRequestUrlKey] as String?
                val initiationClientKey = it[initiationClientKeyKey] as String?

                val initiation =
                    when {
                        initiationState != null &&
                            initiationRequestUrl != null &&
                            initiationClientKey != null ->
                            Initiation(
                                state = initiationState,
                                requestUrl = initiationRequestUrl,
                                clientKey = initiationClientKey,
                            )

                        else -> null
                    }

                AuthFlowLauncher(
                    platformLauncher = platformLauncher,
                    scope = scope,
                    resultState =
                        mutableStateOf(
                            serializer.decodeFromString<Result>(it[resultKey] as String)
                        ),
                    initiation = initiation,
                )
            },
        )
    }

    val launcher =
        rememberSaveable(saver = saver) {
            AuthFlowLauncher(
                platformLauncher = platformLauncher,
                scope = scope,
                resultState = mutableStateOf(Result.Undefined),
            )
        }

    LifecycleStartEffect(Unit) {
        launcher.onStart()
        onStopOrDispose { launcher.onStop() }
    }

    return launcher
}
