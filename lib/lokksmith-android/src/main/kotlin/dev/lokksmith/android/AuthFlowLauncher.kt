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
package dev.lokksmith.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.lokksmith.android.LokksmithAuthFlowActivity.Companion.getErrorMessageFromIntent
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowResultProvider
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result
import dev.lokksmith.client.request.flow.AuthFlowStateResponseHandler
import dev.lokksmith.client.snapshot.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Stable
public class AuthFlowLauncher
internal constructor(
    private val activityLauncher: ActivityResultLauncher<Intent>,
    private val context: Context,
    private val scope: CoroutineScope,
    internal val resultState: MutableState<ResultHolder>,
    internal var initiation: Initiation? = null,
) {

    @Parcelize
    @TypeParceler<Result, ResultParceler>
    internal data class ResultHolder(val result: Result) : Parcelable

    /**
     * Observable result of the current flow progress.
     *
     * @see AuthFlowResultProvider.forClient
     */
    public val result: Result
        get() = resultState.value.result

    private var job: Job? = null

    internal fun onStart() {
        initiation?.let { scope.watchClientState(it) }
    }

    internal fun onStop() {
        cancel()
    }

    /**
     * Starts the authentication flow by launching the request URL in a Custom Tab browser. The
     * progress can be observed via [result], which is backed by Compose state.
     *
     * @param initiation The initiation parameters for the auth flow, including the client key and
     *   request URL.
     * @param headers Extra headers that are passed to Custom Tab. See documentation of Custom Tabs,
     *   especially regarding CORS.
     * @see result
     */
    public suspend fun launch(initiation: Initiation, headers: Map<String, String> = emptyMap()) {
        this.initiation = initiation
        scope.watchClientState(initiation)

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

    /**
     * The method is called when we receive a cancellation from the ActivityResultLauncher. This
     * might happen for instance if an error occurred in the response Activity early on. We ensure
     * here that the FlowResult is properly set to a cancelled or error state.
     */
    internal suspend fun cancelPendingFlow(errorMessage: String?) {
        val initiation = initiation ?: return
        val client = getClient(initiation.clientKey)

        if (client.snapshots.value.flowResult != null) return

        client.updateSnapshot {
            copy(
                flowResult =
                    when (errorMessage) {
                        null -> Snapshot.FlowResult.Cancelled(state = initiation.state)
                        else ->
                            Snapshot.FlowResult.Error(
                                state = initiation.state,
                                type = Snapshot.FlowResult.Error.Type.Generic,
                                message = errorMessage,
                            )
                    },
                ephemeralFlowState = null,
            )
        }
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
                                AuthFlowStateResponseHandler(context.lokksmith)
                                    .onResponse(responseUri)
                            } catch (e: Exception) {
                                Log.e(
                                    "AuthFlowLauncher",
                                    "Received exception in AuthFlowStateResponseHandler.onResponse()",
                                    e,
                                )
                            }
                        }
                }

                AuthFlowResultProvider.forClient(client).collect { result ->
                    resultState.value = result.wrap()
                }
            }
    }

    private suspend fun getClient(key: String): InternalClient =
        checkNotNull(context.lokksmith.get(key)) { "client with key not found" } as InternalClient
}

@Composable
public fun rememberAuthFlowLauncher(): AuthFlowLauncher {
    val context = LocalContext.current

    // We're calling this check here to eagerly identify any errors with the setup
    requireLokksmithContext(context)

    lateinit var launcher: AuthFlowLauncher
    val scope = rememberCoroutineScope()
    val activityLauncher =
        rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                scope.launch {
                    launcher.cancelPendingFlow(
                        errorMessage = getErrorMessageFromIntent(result.data)
                    )
                }
            }
        }

    val saver = run {
        val resultKey = "result"
        val initiationStateKey = "initiationState"
        val initiationRequestUrlKey = "initiationRequestUrl"
        val initiationClientKeyKey = "initiationClientKey"

        mapSaver(
            save = {
                mapOf(
                    resultKey to it.resultState,
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

                @Suppress("UNCHECKED_CAST")
                AuthFlowLauncher(
                    activityLauncher = activityLauncher,
                    context = context,
                    scope = scope,
                    resultState = it[resultKey] as MutableState<AuthFlowLauncher.ResultHolder>,
                    initiation = initiation,
                )
            },
        )
    }

    launcher =
        rememberSaveable(saver = saver) {
            AuthFlowLauncher(
                activityLauncher = activityLauncher,
                context = context,
                scope = scope,
                resultState = mutableStateOf(Result.Undefined.wrap()),
            )
        }

    LifecycleStartEffect(Unit) {
        launcher.onStart()
        onStopOrDispose { launcher.onStop() }
    }

    return launcher
}

private fun Result.wrap() = AuthFlowLauncher.ResultHolder(this)

private object ResultParceler : Parceler<Result> {

    override fun Result.write(parcel: Parcel, flags: Int) {
        when (val result = this) {
            Result.Undefined -> parcel.writeInt(0)
            is Result.Processing -> {
                parcel.writeInt(1)
                parcel.writeString(result.state)
            }

            is Result.Success -> {
                parcel.writeInt(2)
                parcel.writeString(result.state)
            }

            is Result.Cancelled -> {
                parcel.writeInt(3)
                parcel.writeString(result.state)
            }

            is Result.Error -> {
                parcel.writeInt(4)
                parcel.writeString(result.state)
                parcel.writeInt(
                    when (result.type) {
                        Result.Error.Type.Generic -> 0
                        Result.Error.Type.OAuth -> 1
                        Result.Error.Type.Validation -> 2
                        Result.Error.Type.TemporalValidation -> 3
                    }
                )
                parcel.writeString(result.message)
                parcel.writeString(result.code)
            }
        }
    }

    override fun create(parcel: Parcel): Result {
        val type = parcel.readInt()
        return when (type) {
            0 -> Result.Undefined

            1 -> Result.Processing(state = requireNotNull(parcel.readString()))

            2 -> Result.Success(state = requireNotNull(parcel.readString()))

            3 -> Result.Cancelled(state = requireNotNull(parcel.readString()))

            4 ->
                Result.Error(
                    state = requireNotNull(parcel.readString()),
                    type =
                        parcel.readInt().let { errorType ->
                            when (errorType) {
                                0 -> Result.Error.Type.Generic
                                1 -> Result.Error.Type.OAuth
                                2 -> Result.Error.Type.Validation
                                3 -> Result.Error.Type.TemporalValidation
                                else ->
                                    throw IllegalArgumentException(
                                        "Unknown Result.Error.Type $errorType"
                                    )
                            }
                        },
                    message = parcel.readString(),
                    code = parcel.readString(),
                )

            else -> throw IllegalArgumentException("Unknown type $type")
        }
    }
}
