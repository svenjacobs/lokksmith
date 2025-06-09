package dev.lokksmith.client.request.refresh

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.token.TokenResponse
import dev.lokksmith.client.request.token.TokenResponseValidator
import kotlinx.serialization.json.Json

internal class RefreshTokenResponseValidator(
    serializer: Json,
    client: InternalClient,
) : TokenResponseValidator<IdToken?>(
    serializer = serializer,
    client = client,
) {

    override fun getIdToken(response: TokenResponse) =
        response.idToken?.let(::decodeIdToken)

    override fun validateIdTokenNonce(idToken: IdToken) {
        // nonce is optional for a refresh response but if it's present it must be validated
        idToken.nonce?.let {
            require(idToken.nonce == client.snapshots.value.nonce) { "nonce mismatch" }
        }
    }
}
