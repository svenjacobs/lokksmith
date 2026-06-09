/*
 * Copyright 2026 Sven Jacobs
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
import dev.lokksmith.SingletonLokksmithProvider.lokksmith
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.compose.AuthFlowLauncher.PlatformLauncher
import dev.lokksmith.compose.AuthFlowLauncher.PlatformOptions
import dev.lokksmith.web.launchAuthFlow

@Composable
public actual fun rememberAuthFlowLauncher(): AuthFlowLauncher =
    rememberAuthFlowLauncher(WebPlatformLauncher())

private class WebPlatformLauncher : PlatformLauncher {

    /**
     * Navigates the current document to the request URL (full-page redirect). The response is
     * handled after the application reloads on the redirect URI; see
     * [dev.lokksmith.web.completeAuthFlowFromRedirect]. [headers] and [options] are
     * Android/iOS-specific and have no effect on the Web.
     */
    override suspend fun launchBrowser(
        initiation: Initiation,
        headers: Map<String, String>,
        options: PlatformOptions,
    ) {
        lokksmith.launchAuthFlow(initiation)
    }

    override fun logException(msg: String, e: Exception) {
        println("Lokksmith: $msg\n${e.stackTraceToString()}")
    }
}
