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
package dev.lokksmith.client.request.flow

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.ResponseException
import dev.lokksmith.client.request.flow.authorizationCode.VerifierStrategy
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.request.token.TokenTemporalValidationException
import dev.lokksmith.client.request.token.TokenValidationException
import dev.lokksmith.client.snapshot.Snapshot.FlowResult
import dev.lokksmith.client.snapshot.Snapshot.FlowResult.Error.Type
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException

public abstract class AbstractAuthFlowResponseHandler(
    private val state: String,
    protected val client: InternalClient,
    private val stateFinalizer: AuthFlowStateFinalizer = AuthFlowStateFinalizer(client),
) : AuthFlowResponseHandler {

    private val stateVerifierStrategy = VerifierStrategy.forKeyValue(Parameter.STATE, state)

    override suspend fun onResponse(redirectUri: String) {
        try {
            val url =
                try {
                    URLBuilder(redirectUri).build()
                } catch (e: URLParserException) {
                    throw ResponseException(cause = e, reason = ResponseException.Reason.UrlParsing)
                } catch (e: Exception) {
                    throw ResponseException(cause = e)
                }

            // The "state" parameter needs to be verified first because it will also be added to
            // error responses.
            if (!stateVerifierStrategy.verify(url.parameters[Parameter.STATE])) {
                throw ResponseException(
                    message = "response state does not match request state",
                    reason = ResponseException.Reason.InvalidResponse,
                )
            }

            requireResponseSuccess(url)
            onResponse(url)
            setResult(FlowResult.Success(state))
        } catch (e: CancellationException) {
            throw e
        } catch (e: OAuthResponseException) {
            setResult(
                FlowResult.Error(
                    state = state,
                    type = Type.OAuth,
                    message = e.message,
                    code = e.error.code,
                )
            )

            throw e
        } catch (e: TokenTemporalValidationException) {
            setResult(
                FlowResult.Error(state = state, type = Type.TemporalValidation, message = e.message)
            )

            throw e
        } catch (e: TokenValidationException) {
            setResult(FlowResult.Error(state = state, type = Type.Validation, message = e.message))

            throw e
        } catch (e: Exception) {
            setResult(FlowResult.Error(state = state, type = Type.Generic, message = e.message))

            throw e
        }
    }

    protected abstract suspend fun onResponse(url: Url)

    /**
     * Checks the given [url] for an OAuth error response.
     *
     * If the URL contains an `error` parameter, this function throws an [OAuthResponseException]
     * with the error details extracted from the URL's query parameters, as specified by the OpenID
     * Connect and OAuth 2.0 standards.
     *
     * This function ensures that the authorization or token response does not indicate an error
     * before proceeding with further processing.
     *
     * @param url The [Url] to inspect for OAuth error parameters.
     * @throws OAuthResponseException if the `error` parameter is present in the URL.
     */
    private fun requireResponseSuccess(url: Url) {
        url.parameters[Parameter.ERROR]?.let { error ->
            throw OAuthResponseException(
                error = error,
                errorDescription = url.parameters[Parameter.ERROR_DESCRIPTION],
                errorUri = url.parameters[Parameter.ERROR_URI],
            )
        }
    }

    private suspend fun setResult(result: FlowResult) {
        stateFinalizer.finalize { copy(flowResult = result) }
    }
}
