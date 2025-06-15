package dev.lokksmith.client.request.token

import dev.lokksmith.client.Client.Tokens.IdToken
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.TEST_INSTANT
import dev.lokksmith.client.asId
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import dev.lokksmith.client.request.token.TokenResponseValidator.Result
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

abstract class AbstractTokenResponseValidatorTest<T : IdToken?> {

    internal abstract fun newValidator(client: InternalClient): TokenResponseValidator<T>

    private val jwtEncoder = JwtEncoder(Json)

    private fun idTokenPayload(
        iss: String? = "issuer",
        sub: String? = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
        aud: List<String> = listOf("clientId"),
        exp: Long? = TEST_INSTANT + 600,
        nbf: Long? = TEST_INSTANT,
        iat: Long? = TEST_INSTANT,
        extra: Map<String, JsonElement> = mapOf("nonce" to JsonPrimitive("0D1ck61")),
    ) = Jwt.Payload(
        iss = iss,
        sub = sub,
        aud = aud,
        exp = exp,
        nbf = nbf,
        iat = iat,
        extra = extra,
    )

    private fun TestScope.`test validate`(
        client: InternalClient,
        idTokenPayload: Jwt.Payload = idTokenPayload(),
        accessToken: String = "YwV7xECTE0",
        tokenType: String = "Bearer",
        expiresIn: Long = 600,
        refreshToken: String? = "LMv1IT0",
        refreshExpiresIn: Long? = 1_209_600,
        previousIdToken: IdToken? = null,
    ): Result<T> {
        val validator = newValidator(client)

        val idToken = Jwt(
            header = Jwt.Header(
                alg = "none",
            ),
            payload = idTokenPayload,
        )

        val response = TokenResponse(
            idToken = jwtEncoder.encode(idToken),
            accessToken = accessToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            refreshToken = refreshToken,
            refreshExpiresIn = refreshExpiresIn,
        )

        return validator.validate(response, previousIdToken)
    }

    @Test
    fun `validate should succeed`() = runTest {
        `test validate`(
            client = createTestClient {
                copy(
                    nonce = "0D1ck61",
                )
            }
        )
    }

    @Test
    fun `validate should succeed with previous ID Token`() = runTest {
        `test validate`(
            client = createTestClient {
                copy(
                    nonce = "0D1ck61",
                )
            },
            previousIdToken = IdToken(
                issuer = "issuer",
                subject = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                audiences = listOf("clientId"),
                expiration = 600,
                issuedAt = TEST_INSTANT - 60,
                raw = "",
            ),
        )
    }

    @Test
    fun `validate should fail on nonce mismatch`() = runTest {
        val e = assertFailsWith<IllegalArgumentException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "7lOdSRX",
                    )
                }
            )
        }

        assertEquals("nonce mismatch", e.message)
    }

    @Test
    fun `validate should fail on issuer mismatch`() = runTest {
        val e = assertFailsWith<IllegalArgumentException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        metadata = metadata.copy(issuer = "another issuer"),
                    )
                },
            )
        }

        assertEquals("iss mismatch", e.message)
    }

    @Test
    fun `validate should fail on client ID mismatch`() = runTest {
        val e = assertFailsWith<IllegalArgumentException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        id = "another client ID".asId(),
                    )
                },
            )
        }

        assertEquals("client_id missing in aud", e.message)
    }

    @Test
    fun `validate should fail on issuedAt mismatch`() = runTest {
        val e = assertFailsWith<TokenTemporalValidationException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                idTokenPayload = idTokenPayload(
                    iat = TEST_INSTANT + 60, // iat in the future
                ),
            )
        }

        assertEquals("iat is in the future", e.message)
    }

    @Test
    fun `validate should fail on expiration mismatch`() = runTest {
        val e = assertFailsWith<TokenTemporalValidationException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                idTokenPayload = idTokenPayload(
                    exp = TEST_INSTANT - 60, // exp in the past
                ),
            )
        }

        assertEquals("exp before current time", e.message)
    }

    @Test
    fun `validate should fail on nbf mismatch`() = runTest {
        val e = assertFailsWith<TokenTemporalValidationException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                idTokenPayload = idTokenPayload(
                    nbf = TEST_INSTANT + 60, // nbf in the future
                ),
            )
        }

        assertEquals("token not yet valid (nbf)", e.message)
    }

    @Test
    fun `validate should fail with previous ID Token on issuer mismatch`() = runTest {
        val e = assertFailsWith<IllegalArgumentException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                previousIdToken = IdToken(
                    issuer = "issuer2", // another issuer
                    subject = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                    audiences = listOf("clientId"),
                    expiration = 600,
                    issuedAt = TEST_INSTANT - 60,
                    raw = "",
                ),
            )
        }

        assertEquals("iss mismatch with previous token", e.message)
    }

    @Test
    fun `validate should fail with previous ID Token on sub mismatch`() = runTest {
        val e = assertFailsWith<IllegalArgumentException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                previousIdToken = IdToken(
                    issuer = "issuer",
                    subject = "e1c9022a-baf6-4f76-9e38-54238c450768", // different subject
                    audiences = listOf("clientId"),
                    expiration = 600,
                    issuedAt = TEST_INSTANT - 60,
                    raw = "",
                ),
            )
        }

        assertEquals("sub mismatch with previous token", e.message)
    }

    @Test
    fun `validate should fail with previous ID Token on aud mismatch`() = runTest {
        val e = assertFailsWith<IllegalArgumentException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                previousIdToken = IdToken(
                    issuer = "issuer",
                    subject = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                    audiences = listOf("clientId2"), // other client ID
                    expiration = 600,
                    issuedAt = TEST_INSTANT - 60,
                    raw = "",
                ),
            )
        }

        assertEquals("aud mismatch with previous token", e.message)
    }

    @Test
    fun `validate should fail with previous ID Token on iat mismatch`() = runTest {
        val e = assertFailsWith<TokenTemporalValidationException> {
            `test validate`(
                client = createTestClient {
                    copy(
                        nonce = "0D1ck61",
                    )
                },
                previousIdToken = IdToken(
                    issuer = "issuer",
                    subject = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                    audiences = listOf("clientId"),
                    expiration = 600,
                    issuedAt = TEST_INSTANT, // same time
                    raw = "",
                ),
            )
        }

        assertEquals("iat not greater than previous token", e.message)
    }
}
