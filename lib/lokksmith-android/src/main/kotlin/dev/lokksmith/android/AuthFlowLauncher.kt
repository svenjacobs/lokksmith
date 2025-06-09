package dev.lokksmith.android

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
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowResultProvider
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result
import dev.lokksmith.client.request.flow.AuthFlowStateResponseHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Stable
public class AuthFlowLauncher internal constructor(
    private val activityLauncher: ActivityResultLauncher<Intent>,
    private val context: Context,
    private val scope: CoroutineScope,
    internal val resultState: MutableState<ResultHolder>,
    internal var initiation: Initiation? = null,
) {

    @Parcelize
    @TypeParceler<Result, ResultParceler>
    internal data class ResultHolder(
        val result: Result,
    ) : Parcelable

    /**
     * Observable result of the current flow progress.
     *
     * @see AuthFlowResultProvider.forClient
     */
    public val result: Result
        get() = resultState.value.result

    private var job: Job? = null

    internal fun onStart() {
        initiation?.let {
            scope.watchClientState(it.clientKey)
        }
    }

    internal fun onStop() {
        cancel()
    }

    /**
     * Starts the authentication flow by launching the request URL in a Custom Tab browser.
     * The progress can be observed via [result], which is backed by Compose state.
     *
     * @param initiation The initiation parameters for the auth flow, including the client key and request URL.
     * @param headers Extra headers that are passed to Custom Tab.
     *                See documentation of Custom Tabs, especially regarding CORS.
     *
     * @see result
     */
    public suspend fun launch(
        initiation: Initiation,
        headers: Map<String, String> = emptyMap(),
    ) {
        this.initiation = initiation
        scope.watchClientState(initiation.clientKey)

        withContext(Dispatchers.Main.immediate) {
            val intent = LokksmithAuthFlowActivity.createCustomTabsIntent(
                context = context,
                url = initiation.requestUrl,
                clientKey = initiation.clientKey,
                headers = headers,
            )

            activityLauncher.launch(intent)
        }
    }

    private fun cancel() {
        job?.cancel()
        job = null
    }

    private fun CoroutineScope.watchClientState(key: String) {
        cancel()

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            resultState.value = Result.Error(
                code = null,
                message = throwable.message,
            ).wrap()
        }

        job = launch(exceptionHandler) {
            val client = getClient(key)

            launch {
                client.snapshots
                    .map { it.ephemeralFlowState?.responseUri }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { responseUri ->
                        AuthFlowStateResponseHandler(context.lokksmith).onResponse(responseUri)
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
    val scope = rememberCoroutineScope()
    val activityLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        Log.d("AuthFlowLauncher", "Received Activity result $result")
    }

    val saver = run {
        val resultKey = "result"
        val initiationRequestUrlKey = "initiationRequestUrl"
        val initiationClientKeyKey = "initiationClientKey"

        mapSaver(
            save = {
                mapOf(
                    resultKey to it.resultState,
                    initiationRequestUrlKey to it.initiation?.requestUrl,
                    initiationClientKeyKey to it.initiation?.clientKey,
                )
            },
            restore = {
                val initiationRequestUrl = it[initiationRequestUrlKey] as String?
                val initiationClientKey = it[initiationClientKeyKey] as String?

                val initiation = when {
                    initiationRequestUrl != null && initiationClientKey != null -> Initiation(
                        initiationRequestUrl,
                        initiationClientKey,
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

    val launcher = rememberSaveable(saver = saver) {
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

    override fun Result.write(
        parcel: Parcel,
        flags: Int
    ) {
        when (val result = this) {
            Result.Undefined -> parcel.writeInt(0)
            Result.Processing -> parcel.writeInt(1)
            Result.Success -> parcel.writeInt(2)
            Result.Cancelled -> parcel.writeInt(3)
            is Result.Error -> {
                parcel.writeInt(4)
                parcel.writeString(result.code)
                parcel.writeString(result.message)
            }
        }
    }

    override fun create(parcel: Parcel): Result {
        val type = parcel.readInt()
        return when (type) {
            0 -> Result.Undefined

            1 -> Result.Processing

            2 -> Result.Success

            3 -> Result.Cancelled

            4 -> Result.Error(
                code = parcel.readString(),
                message = parcel.readString(),
            )

            else -> throw IllegalArgumentException("unknown type $type")
        }
    }
}
