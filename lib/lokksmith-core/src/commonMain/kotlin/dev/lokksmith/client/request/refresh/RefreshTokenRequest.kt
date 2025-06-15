package dev.lokksmith.client.request.refresh

import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.parameter.GrantType
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.request.refresh.RefreshTokenRequest.Response
import dev.lokksmith.client.request.token.TokenRequest
import dev.lokksmith.client.request.token.TokenValidationException
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokens">Refresh Request</a>
 */
public fun interface RefreshTokenRequest {

    public data class Response(
        val accessToken: Tokens.AccessToken,
        val refreshToken: Tokens.RefreshToken?,
        val idToken: IdToken?,
    )

    public suspend operator fun invoke(): Response
}

internal class RefreshTokenRequestImpl(
    private val client: InternalClient,
    httpClient: HttpClient,
    serializer: Json,
    private val tokenRequest: TokenRequest = TokenRequest(client, httpClient),
    private val tokenResponseValidator: RefreshTokenResponseValidator =
        RefreshTokenResponseValidator(
            serializer = serializer,
            client = client,
        ),
) : RefreshTokenRequest {

    override suspend operator fun invoke(): Response {
        val refreshToken = checkNotNull(client.snapshots.value.tokens?.refreshToken) {
            "refresh token is null"
        }

        val response = tokenRequest {
            append(Parameter.GRANT_TYPE, GrantType.RefreshToken.value)
            append(Parameter.CLIENT_ID, client.id.value)
            append(Parameter.REFRESH_TOKEN, refreshToken.token)
        }

        val result = try {
            tokenResponseValidator.validate(
                response = response,
                previousIdToken = client.snapshots.value.tokens?.idToken,
            )
        } catch (e: TokenValidationException) {
            throw e
        } catch (e: Exception) {
            throw TokenValidationException(cause = e)
        }

        return Response(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            idToken = result.idToken,
        )
    }
}
