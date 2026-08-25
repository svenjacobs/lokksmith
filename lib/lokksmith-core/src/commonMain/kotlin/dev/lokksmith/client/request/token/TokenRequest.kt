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
package dev.lokksmith.client.request.token

import dev.lokksmith.client.Client
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.RequestException
import dev.lokksmith.client.request.ResponseException
import dev.lokksmith.client.request.bodyOrThrow
import dev.lokksmith.internal.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

public class TokenRequest(private val client: Client, private val httpClient: HttpClient) {

    public suspend operator fun invoke(builder: ParametersBuilder.() -> Unit): TokenResponse {
        val formParameters = Parameters.build {
            builder()
            appendAdditionalParameters(client.options.additionalTokenRequestParameters)
        }

        val response =
            try {
                withContext(ioDispatcher) {
                    httpClient.submitForm(
                        url = client.metadata.tokenEndpoint,
                        formParameters = formParameters,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RequestException(cause = e, reason = RequestException.Reason.HttpError)
            }

        if (!response.status.isSuccess()) {
            val tokenResponse = response.bodyOrThrow<TokenErrorResponse>()

            tokenResponse.error?.let {
                throw OAuthResponseException(
                    error = it,
                    errorDescription = tokenResponse.errorDescription,
                    errorUri = tokenResponse.errorUri,
                    statusCode = response.status.value,
                )
            }
                ?: throw ResponseException(
                    message =
                        "error status code ${response.status.value} received from token endpoint",
                    reason = ResponseException.Reason.HttpError,
                )
        }

        val tokenResponse = response.bodyOrThrow<TokenResponse>()

        if (tokenResponse.tokenType != "Bearer") {
            throw ResponseException(
                message = "token type must be \"Bearer\" in token response",
                reason = ResponseException.Reason.InvalidResponse,
            )
        }

        return tokenResponse
    }
}

/**
 * Appends [additionalParameters] to the request, rejecting any key the request already carries.
 *
 * [Client.Options] validates its parameters against the known OAuth/OIDC parameters when it is
 * constructed, which covers everything these requests send today. This check is not redundant with
 * that one: it covers a different moment in time, and it keeps the guarantee intact for a future
 * call site that appends a parameter the constructor-time list does not know about.
 *
 * Rejecting rather than skipping matters because form parameters are a multimap — appending a
 * duplicate `grant_type` would send both values and leave the choice between them to the server.
 */
private fun ParametersBuilder.appendAdditionalParameters(
    additionalParameters: Map<String, String>
) {
    additionalParameters.forEach { (name, value) ->
        require(!contains(name)) { "Parameter \"$name\" is already present" }
        append(name, value)
    }
}
