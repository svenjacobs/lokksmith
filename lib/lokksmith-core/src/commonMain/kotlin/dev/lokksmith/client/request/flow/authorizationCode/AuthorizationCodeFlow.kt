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
package dev.lokksmith.client.request.flow.authorizationCode

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.Defaults
import dev.lokksmith.client.request.Random
import dev.lokksmith.client.request.RequestException
import dev.lokksmith.client.request.flow.AbstractAuthFlow
import dev.lokksmith.client.request.flow.AuthFlow
import dev.lokksmith.client.request.flow.addAdditionalParameters
import dev.lokksmith.client.request.flow.addOptionalParameter
import dev.lokksmith.client.request.parameter.CodeChallengeMethod
import dev.lokksmith.client.request.parameter.Display
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.request.parameter.Prompt
import dev.lokksmith.client.request.parameter.ResponseType
import dev.lokksmith.client.request.parameter.Scope
import dev.lokksmith.client.snapshot.Snapshot
import io.ktor.client.HttpClient
import io.ktor.http.URLParserException
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json

/**
 * Implements the OpenID Connect Authorization Code Flow.
 *
 * Use the result of [prepare] to initiate the flow by opening the URL in a browser (e.g., a Custom
 * Tab), following [OAuth 2.0 for Native Apps](https://www.rfc-editor.org/info/rfc8252).
 *
 * Keep the same instance of this class in memory throughout the flow. After the user completes
 * authentication, pass the full redirect URI received from the OpenID provider to [onResponse].
 */
public class AuthorizationCodeFlow
private constructor(
    client: InternalClient,
    state: String,
    responseHandler: AuthorizationCodeFlowResponseHandler,
    private val request: Request,
    internal val nonce: String?,
    internal val codeVerifier: String?,
) :
    AbstractAuthFlow(
        client = client,
        state = state,
        responseHandler = responseHandler,
        cancellation = AuthorizationCodeFlowCancellation(client),
    ) {

    /**
     * @see <a
     *   href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">Authentication
     *   Request</a>
     */
    public data class Request(

        /** Redirect URI to which the response is sent by the OpenID provider. */
        override val redirectUri: String,

        /**
         * Scopes to specify what access privileges are being requested for access tokens.
         *
         * Although the default value is an empty set, some flows might augment the set with
         * required scopes if needed.
         */
        val scope: Set<Scope> = emptySet(),

        /**
         * Specifies whether the authorization server prompts the end-user for reauthentication and
         * consent.
         */
        val prompt: Set<Prompt> = emptySet(),

        /**
         * Length of the cryptographically random `state` parameter included in the authorization
         * request.
         *
         * The `state` parameter is required to prevent cross-site request forgery (CSRF) attacks
         * and to allow Lokksmith to restore the flow when handling the authorization response. It
         * is returned by the OpenID provider and must be validated by the client.
         *
         * Disabling the `state` parameter is not supported, as it is essential for both security
         * and Lokksmith's flow management. While the OpenID Connect specification does not define a
         * minimum or maximum length, Lokksmith enforces a minimum of 16 characters for security.
         */
        override val stateLength: Int = Defaults.STATE_MIN_LENGTH,

        /**
         * Specifies the length of the `nonce` parameter to be included in the authorization
         * request.
         *
         * The `nonce` parameter is a cryptographically random string used to prevent replay
         * attacks. It is returned by the OpenID provider in the ID token and must be validated by
         * the client.
         *
         * A value of 0 disables the `nonce` parameter (not recommended). Although the OpenID
         * Connect specification does not mandate a minimum or maximum length, Lokksmith enforces a
         * minimum of 16 for security reasons.
         *
         * It is strongly recommended to keep this on unless the OpenID provider does not support
         * the `nonce` parameter.
         */
        val nonceLength: Int = Defaults.NONCE_MIN_LENGTH,

        /**
         * The PKCE code challenge method to use. Currently only SHA256 is supported. Setting this
         * property to `null` disables PKCE (not recommended).
         *
         * @see <a href="https://www.rfc-editor.org/info/rfc7636">Proof Key for Code Exchange by
         *   OAuth Public Clients</a>
         */
        val codeChallengeMethod: CodeChallengeMethod? = CodeChallengeMethod.SHA256,

        /**
         * Specifies how the Authorization Server displays the authentication and consent user
         * interface pages to the End-User.
         */
        val display: Display? = null,

        /**
         * Specifies the allowable elapsed time in seconds since the last time the End-User was
         * actively authenticated by the OP.
         */
        val maxAge: Int? = null,

        /** List of language tag values (RFC5646), ordered by preference. */
        override val uiLocales: List<String> = emptyList(),

        /**
         * Hint to the Authorization Server about the login identifier the End-User might use to log
         * in (if necessary).
         */
        val loginHint: String? = null,

        /**
         * Additional parameters (key/value pairs) appended to the request URI. The use of any
         * standard OAuth/OIDC parameters is an error and will throw an [IllegalArgumentException].
         * Values must not be URL-encoded!
         */
        override val additionalParameters: Map<String, String> = emptyMap(),
    ) : AuthFlow.Request

    private val stateVerifierStrategy = VerifierStrategy.forKeyValue(Parameter.STATE, state)
    private val nonceVerifierStrategy = VerifierStrategy.forKeyValue(Parameter.NONCE, nonce)

    private val scopes = (request.scope + Scope.OpenId).joinToString(" ")

    override val ephemeralFlowState: Snapshot.EphemeralFlowState
        get() =
            Snapshot.EphemeralAuthorizationCodeFlowState(
                state = state,
                redirectUri = request.redirectUri,
                codeVerifier = codeVerifier,
                responseUri = null,
            )

    override fun onPrepareUpdateSnapshot(snapshot: Snapshot) = snapshot.copy(nonce = nonce)

    override suspend fun onPrepare(): String {
        val codeChallengeStrategy =
            CodeChallengeStrategy.create(request.codeChallengeMethod, codeVerifier)

        return try {
            buildUrl {
                    takeFrom(client.metadata.authorizationEndpoint)

                    parameters[Parameter.SCOPE] = scopes
                    parameters[Parameter.RESPONSE_TYPE] = ResponseType.Code.value
                    parameters[Parameter.CLIENT_ID] = client.id.value
                    parameters[Parameter.REDIRECT_URI] = request.redirectUri

                    stateVerifierStrategy.addParameter()
                    nonceVerifierStrategy.addParameter()
                    codeChallengeStrategy.addParameters()

                    addOptionalParameter(Parameter.DISPLAY, request.display)
                    addOptionalParameter(Parameter.PROMPT, request.prompt)
                    addOptionalParameter(Parameter.MAX_AGE, request.maxAge)
                    addOptionalParameter(Parameter.UI_LOCALES, request.uiLocales)
                    addOptionalParameter(
                        Parameter.ID_TOKEN_HINT,
                        client.snapshots.value.tokens?.idToken?.raw,
                    )
                    addOptionalParameter(Parameter.LOGIN_HINT, request.loginHint)
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
    }

    internal companion object {

        internal fun create(
            client: InternalClient,
            request: Request,
            httpClient: HttpClient,
            serializer: Json,
            random: Random = Random(),
        ): AuthorizationCodeFlow {
            val state =
                when {
                    request.stateLength >= Defaults.STATE_MIN_LENGTH ->
                        random.randomAsciiString(request.stateLength)

                    else ->
                        throw IllegalArgumentException(
                            "stateLength must not be less than ${Defaults.STATE_MIN_LENGTH}"
                        )
                }

            val nonce =
                when {
                    request.nonceLength == 0 -> null
                    request.nonceLength >= Defaults.NONCE_MIN_LENGTH ->
                        random.randomAsciiString(request.nonceLength)

                    else ->
                        throw IllegalArgumentException(
                            "nonceLength must not be less than ${Defaults.NONCE_MIN_LENGTH}"
                        )
                }

            val codeVerifier =
                when (request.codeChallengeMethod) {
                    null -> null
                    else -> random.randomCodeVerifier(128)
                }

            val responseHandler =
                AuthorizationCodeFlowResponseHandler(
                    serializer = serializer,
                    state = state,
                    client = client,
                    httpClient = httpClient,
                    redirectUri = request.redirectUri,
                    codeVerifier = codeVerifier,
                )

            return AuthorizationCodeFlow(
                client = client,
                state = state,
                responseHandler = responseHandler,
                request = request,
                nonce = nonce,
                codeVerifier = codeVerifier,
            )
        }
    }
}
