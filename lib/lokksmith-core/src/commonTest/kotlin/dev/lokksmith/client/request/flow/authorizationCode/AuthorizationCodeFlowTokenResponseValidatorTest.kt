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
import dev.lokksmith.client.request.token.AbstractTokenResponseValidatorTest
import kotlinx.serialization.json.Json

@Suppress("unused")
class AuthorizationCodeFlowTokenResponseValidatorTest :
    AbstractTokenResponseValidatorTest<IdToken>() {

    override fun newValidator(client: InternalClient) =
        AuthorizationCodeFlowTokenResponseValidator(
            serializer = Json,
            client = client,
        )
}
