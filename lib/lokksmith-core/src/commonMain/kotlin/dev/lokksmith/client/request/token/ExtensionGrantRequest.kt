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
package dev.lokksmith.client.request.token

import dev.lokksmith.client.Client
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.parameter.Parameter
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.5">Extension Grants</a> */
public fun interface ExtensionGrantRequest {

    public suspend operator fun invoke(
        grantType: String,
        parameters: Map<String, String>,
    ): Client.Tokens
}

internal class ExtensionGrantRequestImpl(
    private val client: InternalClient,
    httpClient: HttpClient,
    serializer: Json,
    private val tokenRequest: TokenRequest = TokenRequest(client, httpClient),
    private val tokenResponseValidator: ExtensionGrantResponseValidator =
        ExtensionGrantResponseValidator(serializer = serializer, client = client),
) : ExtensionGrantRequest {

    override suspend operator fun invoke(
        grantType: String,
        parameters: Map<String, String>,
    ): Client.Tokens {
        require(grantType.isNotBlank()) { "grantType must not be blank" }

        val response = tokenRequest {
            append(Parameter.GRANT_TYPE, grantType)
            append(Parameter.CLIENT_ID, client.id.value)
            appendAdditionalParameters(parameters)
        }

        // No previousIdToken is passed: an extension grant has no prior token of its own to compare
        // against, and validating against whatever the client happens to already hold would reject
        // a legitimate re-login as a different subject. AuthorizationCodeFlowResponseHandler omits
        // it for the same reason.
        val result =
            try {
                tokenResponseValidator.validate(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TokenValidationException) {
                throw e
            } catch (e: Exception) {
                throw TokenValidationException(cause = e)
            }

        return Client.Tokens(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            idToken = result.idToken,
        )
    }
}
