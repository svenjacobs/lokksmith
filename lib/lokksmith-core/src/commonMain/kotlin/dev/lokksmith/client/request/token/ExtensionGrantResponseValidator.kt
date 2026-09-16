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

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import kotlinx.serialization.json.Json

internal class ExtensionGrantResponseValidator(serializer: Json, client: InternalClient) :
    TokenResponseValidator<IdToken>(serializer = serializer, client = client) {

    override fun getIdToken(response: TokenResponse): IdToken =
        decodeIdToken(requireNotNull(response.idToken) { "ID Token is null" })

    // An extension grant has no authorization request, so no nonce was ever sent and there is
    // nothing to compare against. The ID Token's nonce is persisted by the caller so that a later
    // refresh response echoing it validates.
    override suspend fun validateIdTokenNonce(idToken: IdToken) {}
}
