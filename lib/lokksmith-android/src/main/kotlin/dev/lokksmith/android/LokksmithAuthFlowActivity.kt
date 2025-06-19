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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import dev.lokksmith.Lokksmith
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.parameter.Parameter
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This Activity serves two main purposes:
 * 1. Initiates the Custom Tabs activity to present an authentication flow to the user.
 * 2. Handles incoming redirect responses and updates client authentication states accordingly.
 *
 * @see LokksmithRedirectActivity
 */
public class LokksmithAuthFlowActivity : ComponentActivity() {

    private var intentToHandle: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentToHandle = intent
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentToHandle = intent
    }

    override fun onResume() {
        super.onResume()

        intentToHandle?.let {
            handleIntent(it)
            intentToHandle = null
            return
        }

        /**
         * If at this point onResume() was called and the original Intent data is null, it means the
         * Custom Tabs Activity was cancelled by the user or system. In this case, cancel and finish
         * this Activity as well.
         */
        if (intent.data == null) {
            val clientKey =
                checkNotNull(intent.getStringExtra(EXTRA_LOKKSMITH_CLIENT_KEY)) {
                    "$EXTRA_LOKKSMITH_CLIENT_KEY missing from Intent extras"
                }

            coroutineScope.launch(exceptionHandler { "Error cancelling flow" }) {
                val client =
                    checkNotNull(lokksmith.get(clientKey)) {
                        "Client with key $clientKey not found"
                    }
                        as InternalClient

                client.cancelPendingFlow()
            }

            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_LOKKSMITH_URL)

        if (uri != null) {
            val customTabsIntent = CustomTabsIntent.Builder().build()

            // https://developer.chrome.com/docs/android/custom-tabs/howto-custom-tab-request-headers
            val headers = intent.getBundleExtra(EXTRA_LOKKSMITH_HEADERS)
            if (headers?.isEmpty == false) {
                customTabsIntent.intent.putExtra(Browser.EXTRA_HEADERS, headers)
            }

            customTabsIntent.launchUrl(this, uri.toUri())
        } else {
            handleFlowResponse(intent)
        }
    }

    private fun handleFlowResponse(intent: Intent) {
        try {
            val data = requireNotNull(intent.dataString) { "Intent data is null" }
            val url = Url(data)
            val state =
                checkNotNull(url.parameters[Parameter.STATE]) {
                    "Parameter \"${Parameter.STATE}\" missing from response"
                }

            coroutineScope.launch(exceptionHandler { "Error handling response" }) {
                val snapshot =
                    checkNotNull(lokksmith.container.snapshotStore.getForState(state)) {
                        "No client snapshot found for state"
                    }

                val client =
                    checkNotNull(lokksmith.get(snapshot.key.value)) { "No client found for state" }
                        as InternalClient

                // Update the pending flow state with the response URI and let the UI layer do the
                // handling so that this Activity is closed as fast as possible. We don't want to
                // do network calls etc. here.
                client.updatePendingFlowResponse(data)

                withContext(Dispatchers.Main.immediate) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling response", e)
            setResult(
                RESULT_CANCELED,
                createErrorResultIntent("[$TAG] Error handling response: ${e.message.orEmpty()}"),
            )
            finish()
        }
    }

    private fun exceptionHandler(errorMessage: () -> String) =
        CoroutineExceptionHandler { _, throwable ->
            val msg = errorMessage()
            Log.e(TAG, msg, throwable)
            setResult(
                RESULT_CANCELED,
                createErrorResultIntent("[$TAG] $msg: ${throwable.message.orEmpty()}"),
            )
            finish()
        }

    private val lokksmith: Lokksmith
        get() = lokksmithContext.lokksmith

    private val coroutineScope: CoroutineScope
        get() = lokksmithContext.coroutineScope

    private val lokksmithContext: LokksmithContext by lazy {
        checkNotNull((applicationContext as? LokksmithContextProvider)?.lokksmithContext) {
            "Application class must implement LokksmithContextProvider"
        }
    }

    public companion object {

        /**
         * Creates [Intent] for opening a Custom Tab for authentication via
         * [LokksmithAuthFlowActivity].
         *
         * @param clientKey Key of client
         * @param url URL to open in Custom Tab
         * @param headers Extra headers that are passed to Custom Tab. See documentation of Custom
         *   Tabs, especially regarding CORS.
         */
        public fun createCustomTabsIntent(
            context: Context,
            clientKey: String,
            url: String,
            headers: Map<String, String> = emptyMap(),
        ): Intent =
            Intent(context, LokksmithAuthFlowActivity::class.java).apply {
                putExtra(EXTRA_LOKKSMITH_CLIENT_KEY, clientKey)
                putExtra(EXTRA_LOKKSMITH_URL, url)
                putExtra(EXTRA_LOKKSMITH_HEADERS, bundleOf(*headers.toList().toTypedArray()))
            }

        internal fun createRedirectIntent(context: Context, intentData: Uri?): Intent =
            Intent(context, LokksmithAuthFlowActivity::class.java).apply {
                data = intentData
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        internal fun createErrorResultIntent(errorMessage: String): Intent =
            Intent().apply { putExtra(RESULT_EXTRA_ERROR_MESSAGE, errorMessage) }

        internal fun getErrorMessageFromIntent(intent: Intent?): String? =
            intent?.getStringExtra(RESULT_EXTRA_ERROR_MESSAGE)

        private const val EXTRA_LOKKSMITH_URL = "EXTRA_LOKKSMITH_URL"
        private const val EXTRA_LOKKSMITH_CLIENT_KEY = "EXTRA_LOKKSMITH_CLIENT_KEY"
        private const val EXTRA_LOKKSMITH_HEADERS = "EXTRA_LOKKSMITH_HEADERS"

        private const val RESULT_EXTRA_ERROR_MESSAGE = "RESULT_EXTRA_ERROR_MESSAGE"

        private val TAG = LokksmithAuthFlowActivity::class.simpleName.orEmpty()
    }
}
