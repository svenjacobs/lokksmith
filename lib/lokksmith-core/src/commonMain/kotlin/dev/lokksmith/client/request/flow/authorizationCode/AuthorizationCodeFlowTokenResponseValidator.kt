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

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.token.TokenResponse
import dev.lokksmith.client.request.token.TokenResponseValidator
import kotlinx.serialization.json.Json

/**
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#TokenResponseValidation">Token Response Validation</a>
 */
public class AuthorizationCodeFlowTokenResponseValidator(
    serializer: Json,
    client: InternalClient,
) : TokenResponseValidator<IdToken>(
    serializer = serializer,
    client = client,
) {

    override fun getIdToken(response: TokenResponse): IdToken {
        val rawIdToken = requireNotNull(response.idToken) { "ID Token is null" }
        return decodeIdToken(rawIdToken)
    }

    override fun validateIdTokenNonce(idToken: IdToken) {
        require(idToken.nonce == client.snapshots.value.nonce) { "nonce mismatch" }
    }
}
