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

import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.ResponseException
import dev.lokksmith.client.request.flow.AbstractAuthFlowResponseHandler
import dev.lokksmith.client.request.parameter.GrantType
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.request.token.TokenRequest
import dev.lokksmith.client.request.token.TokenValidationException
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.serialization.json.Json

public class AuthorizationCodeFlowResponseHandler(
    serializer: Json,
    state: String,
    client: InternalClient,
    httpClient: HttpClient,
    private val redirectUri: String,
    private val codeVerifier: String?,
    private val tokenRequest: TokenRequest = TokenRequest(client, httpClient),
    private val tokenResponseValidator: AuthorizationCodeFlowTokenResponseValidator =
        AuthorizationCodeFlowTokenResponseValidator(serializer = serializer, client = client),
) : AbstractAuthFlowResponseHandler(state, client) {

    override suspend fun onResponse(url: Url) {
        val code =
            url.parameters[Parameter.CODE]
                ?: throw ResponseException(
                    message = "code parameter missing from response",
                    reason = ResponseException.Reason.InvalidResponse,
                )

        val response = tokenRequest {
            append(Parameter.GRANT_TYPE, GrantType.AuthorizationCode.value)
            append(Parameter.CLIENT_ID, client.id.value)
            append(Parameter.CODE, code)
            append(Parameter.REDIRECT_URI, this@AuthorizationCodeFlowResponseHandler.redirectUri)
            codeVerifier?.let { append(Parameter.CODE_VERIFIER, it) }
        }

        val result =
            try {
                tokenResponseValidator.validate(response)
            } catch (e: TokenValidationException) {
                throw e
            } catch (e: Exception) {
                throw TokenValidationException(cause = e)
            }

        client.updateSnapshot {
            copy(
                tokens =
                    Tokens(
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        idToken = result.idToken,
                    )
            )
        }
    }
}
