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
package dev.lokksmith.ios

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowUserAgentResponseHandler
import dev.lokksmith.client.request.parameter.Parameter
import io.ktor.http.Url
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCompletionHandler
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

public suspend fun Lokksmith.launchAuthFlow(
    initiation: Initiation,
    prefersEphemeralWebBrowserSession: Boolean = false,
    additionalHeaderFields: Map<Any?, *>? = null,
) {
    val responseHandler = AuthFlowUserAgentResponseHandler(this)

    try {
        val responseUri =
            startAuthenticationSession(
                requestUrl = initiation.requestUrl,
                prefersEphemeralWebBrowserSession = prefersEphemeralWebBrowserSession,
                additionalHeaderFields = additionalHeaderFields,
            )

        with(responseHandler) {
            when (responseUri) {
                null -> onCancel(key = initiation.clientKey, state = initiation.state)
                else -> onResponse(key = initiation.clientKey, responseUri = responseUri)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        responseHandler.onError(
            key = initiation.clientKey,
            state = initiation.state,
            message = e.message,
        )
    }
}

private suspend fun startAuthenticationSession(
    requestUrl: String,
    prefersEphemeralWebBrowserSession: Boolean,
    additionalHeaderFields: Map<Any?, *>?,
): String? = suspendCoroutine { cont ->
    val url = checkNotNull(NSURL.URLWithString(requestUrl)) { "Could not create NSURL" }
    val redirectScheme = getRedirectScheme(requestUrl)

    val session =
        ASWebAuthenticationSession(
            uRL = url,
            callbackURLScheme = redirectScheme,
            completionHandler =
                object : ASWebAuthenticationSessionCompletionHandler {
                    override fun invoke(responseUri: NSURL?, error: NSError?) {
                        if (responseUri != null) {
                            cont.resume(responseUri.toString())
                            return
                        }

                        if (error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin) {
                            cont.resume(null)
                            return
                        }

                        cont.resumeWithException(
                            RuntimeException(
                                message = error?.localizedDescription ?: "No responseUri received"
                            )
                        )
                    }
                },
        )

    session.presentationContextProvider = PresentationContextProvider()
    session.prefersEphemeralWebBrowserSession = prefersEphemeralWebBrowserSession
    session.additionalHeaderFields = additionalHeaderFields

    session.start()
}

private fun getRedirectScheme(requestUrl: String): String {
    val url = Url(requestUrl)

    val redirectUri =
        checkNotNull(
            url.parameters[Parameter.REDIRECT_URI]
                ?: url.parameters[Parameter.POST_LOGOUT_REDIRECT_URI]
        ) {
            "Could not determine redirect URI"
        }

    return Url(redirectUri).protocol.name
}

private class PresentationContextProvider :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ) = UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow ?: ASPresentationAnchor()
}
