package dev.lokksmith.client.request.refresh

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.token.AbstractTokenResponseValidatorTest
import kotlinx.serialization.json.Json

@Suppress("unused")
class RefreshTokenResponseValidatorTest :
    AbstractTokenResponseValidatorTest<IdToken?>() {

    override fun newValidator(client: InternalClient) =
        RefreshTokenResponseValidator(
            serializer = Json,
            client = client,
        )
}
