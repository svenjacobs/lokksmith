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
package dev.lokksmith.client.request.token

import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.jwt.JwtDecoder
import dev.lokksmith.client.token.JwtToIdTokenMapper
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.serialization.json.Json

public abstract class TokenResponseValidator<T : IdToken?>(
    serializer: Json,
    protected val client: InternalClient,
    private val jwtDecoder: JwtDecoder = JwtDecoder(serializer),
    private val jwtToIdTokenMapper: JwtToIdTokenMapper = JwtToIdTokenMapper(),
) {

    public data class Result<T : IdToken?>(
        val accessToken: Tokens.AccessToken,
        val refreshToken: Tokens.RefreshToken?,
        val idToken: T,
    )

    public fun validate(response: TokenResponse, previousIdToken: IdToken? = null): Result<T> {
        val idToken = getIdToken(response)

        // If a previous ID token exists it must be validated against the new token
        if (idToken != null && previousIdToken != null) {
            require(idToken.issuer == previousIdToken.issuer) { "iss mismatch with previous token" }
            require(idToken.subject == previousIdToken.subject) {
                "sub mismatch with previous token"
            }
            require(idToken.audiences.sorted() == previousIdToken.audiences.sorted()) {
                "aud mismatch with previous token"
            }
            requireTemporal(idToken.issuedAt > previousIdToken.issuedAt) {
                "iat not greater than previous token"
            }
        }

        if (idToken != null) {
            validateIdToken(idToken)
        }

        return Result<T>(
            accessToken =
                Tokens.AccessToken(
                    token = response.accessToken,
                    expiresAt = response.expiresIn?.let { it + client.provider.instantProvider() },
                ),
            refreshToken =
                response.refreshToken?.let {
                    Tokens.RefreshToken(
                        token = it,
                        expiresAt =
                            response.refreshExpiresIn?.let {
                                it + client.provider.instantProvider()
                            },
                    )
                },
            idToken = idToken,
        )
    }

    protected fun decodeIdToken(rawIdToken: String): IdToken {
        val jwt = jwtDecoder.decode(rawIdToken)
        return jwtToIdTokenMapper(jwt, rawIdToken)
    }

    protected abstract fun getIdToken(response: TokenResponse): T

    protected abstract fun validateIdTokenNonce(idToken: IdToken)

    /**
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation">ID
     *   Token Validation</a>
     */
    private fun validateIdToken(idToken: IdToken) {
        val now = client.provider.instantProvider()

        require(idToken.issuer == client.metadata.issuer) { "iss mismatch" }
        require(idToken.audiences.contains(client.id.value)) { "client_id missing in aud" }
        requireTemporal(idToken.issuedAt <= now + client.options.leewaySeconds) {
            "iat is in the future"
        }
        requireTemporal(now - client.options.leewaySeconds < idToken.expiration) {
            "exp before current time"
        }

        idToken.notBefore?.let { nbf ->
            requireTemporal(now + client.options.leewaySeconds >= nbf) {
                "token not yet valid (nbf)"
            }
        }

        validateIdTokenNonce(idToken)

        // TODO check auth_time?
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun requireTemporal(value: Boolean, lazyMessage: () -> String) {
        contract { returns() implies value }
        if (!value) throw TokenTemporalValidationException(message = lazyMessage())
    }
}
