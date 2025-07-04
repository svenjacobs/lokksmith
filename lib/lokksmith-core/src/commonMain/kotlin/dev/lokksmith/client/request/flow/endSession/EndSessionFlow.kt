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
package dev.lokksmith.client.request.flow.endSession

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.Defaults
import dev.lokksmith.client.request.Random
import dev.lokksmith.client.request.RequestException
import dev.lokksmith.client.request.flow.AbstractAuthFlow
import dev.lokksmith.client.request.flow.AuthFlow
import dev.lokksmith.client.request.flow.addAdditionalParameters
import dev.lokksmith.client.request.flow.addOptionalParameter
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.snapshot.Snapshot
import io.ktor.http.URLParserException
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom

/**
 * Implements the OpenID Connect RP-Initiated Logout flow.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">OpenID Connect
 *   RP-Initiated Logout 1.0</a>
 */
public class EndSessionFlow
internal constructor(
    client: InternalClient,
    state: String,
    private val request: Request,
    private val endpoint: String,
) :
    AbstractAuthFlow(
        client = client,
        state = state,
        responseHandler = EndSessionFlowResponseHandler(state, client),
    ) {

    public data class Request(
        /** Redirect URI to which the response is sent by the OpenID provider. */
        override val redirectUri: String,

        /**
         * Length of the cryptographically random `state` parameter included in the end session
         * request.
         *
         * The `state` parameter is required to prevent cross-site request forgery (CSRF) attacks
         * and to allow Lokksmith to restore the flow when handling the end session response. It is
         * returned by the OpenID provider and must be validated by the client.
         *
         * Disabling the `state` parameter is not supported, as it is essential for both security
         * and Lokksmith's flow management. While the OpenID Connect specification does not define a
         * minimum or maximum length, Lokksmith enforces a minimum of 16 characters for security.
         */
        override val stateLength: Int = Defaults.STATE_MIN_LENGTH,

        /** Hint to the Authorization Server about the End-User that is logging out. */
        val logoutHint: String? = null,

        /** List of language tag values (RFC5646), ordered by preference. */
        override val uiLocales: List<String> = emptyList(),

        /**
         * Additional parameters (key/value pairs) appended to the request URI. The use of any
         * standard OAuth/OIDC parameters is an error and will throw an [IllegalArgumentException].
         * Values must not be URL-encoded!
         */
        override val additionalParameters: Map<String, String> = emptyMap(),
    ) : AuthFlow.Request

    override val ephemeralFlowState: Snapshot.EphemeralFlowState
        get() = Snapshot.EphemeralEndSessionFlowState(state = state, responseUri = null)

    override suspend fun onPrepare(): String =
        try {
            buildUrl {
                    takeFrom(endpoint)

                    parameters[Parameter.STATE] = state
                    parameters[Parameter.POST_LOGOUT_REDIRECT_URI] = request.redirectUri
                    parameters[Parameter.CLIENT_ID] = client.id.value

                    addOptionalParameter(Parameter.UI_LOCALES, request.uiLocales)
                    addOptionalParameter(
                        Parameter.ID_TOKEN_HINT,
                        client.snapshots.value.tokens?.idToken?.raw,
                    )
                    addOptionalParameter(Parameter.LOGOUT_HINT, request.logoutHint)
                    addAdditionalParameters(request.additionalParameters)
                }
                .toString()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: URLParserException) {
            throw RequestException(cause = e, reason = RequestException.Reason.UrlParsing)
        } catch (e: Exception) {
            throw RequestException(e)
        }

    internal companion object {

        internal fun createOrNull(
            client: InternalClient,
            request: Request,
            random: Random = Random(),
        ): EndSessionFlow? =
            client.metadata.endSessionEndpoint?.let {
                val state =
                    when {
                        request.stateLength >= Defaults.STATE_MIN_LENGTH ->
                            random.randomAsciiString(request.stateLength)

                        else ->
                            throw IllegalArgumentException(
                                "stateLength must not be less than ${Defaults.STATE_MIN_LENGTH}"
                            )
                    }

                EndSessionFlow(client = client, state = state, request = request, endpoint = it)
            }
    }
}
