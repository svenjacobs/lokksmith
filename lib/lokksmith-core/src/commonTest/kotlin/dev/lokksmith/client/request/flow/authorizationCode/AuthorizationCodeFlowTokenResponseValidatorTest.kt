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
