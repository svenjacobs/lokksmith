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
package dev.lokksmith.client.request.refresh

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.token.TokenResponse
import dev.lokksmith.client.request.token.TokenResponseValidator
import kotlinx.serialization.json.Json

internal class RefreshTokenResponseValidator(serializer: Json, client: InternalClient) :
    TokenResponseValidator<IdToken?>(serializer = serializer, client = client) {

    override fun getIdToken(response: TokenResponse) = response.idToken?.let(::decodeIdToken)

    override fun validateIdTokenNonce(idToken: IdToken) {
        // nonce is optional for a refresh response but if it's present it must be validated
        idToken.nonce?.let {
            require(idToken.nonce == client.snapshots.value.nonce) { "nonce mismatch" }
        }
    }
}
