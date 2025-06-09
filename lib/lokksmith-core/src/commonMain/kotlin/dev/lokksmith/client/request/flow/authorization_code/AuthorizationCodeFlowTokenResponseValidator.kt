package dev.lokksmith.client.request.flow.authorization_code

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
