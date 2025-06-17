package dev.lokksmith.client.usecase

import dev.lokksmith.client.SAMPLE_TOKENS
import dev.lokksmith.client.TEST_INSTANT
import dev.lokksmith.client.TestProvider
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.request.OAuthError
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.refresh.RefreshTokenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RunWithTokensOrResetUseCaseTest {

    @Test
    fun `invoke should reset tokens on expected error when refreshing`() = runTest {
        val client = createTestClient(
            provider = TestProvider(
                refreshTokenRequest = {
                    RefreshTokenRequest {
                        throw OAuthResponseException(
                            error = OAuthError.InvalidGrant,
                        )
                    }
                }
            ),
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS.copy(
                        accessToken = SAMPLE_TOKENS.accessToken.copy(expiresAt = TEST_INSTANT - 60),
                    )
                )
            }
        )

        assertFalse { client.runWithTokensOrReset {} }
        runCurrent()
        assertNull(client.tokens.value)
    }

    @Test
    fun `invoke should rethrow exception on unexpected error when refreshing`() = runTest {
        val client = createTestClient(
            provider = TestProvider(
                refreshTokenRequest = {
                    RefreshTokenRequest {
                        throw OAuthResponseException(
                            error = OAuthError.ServerError,
                        )
                    }
                }
            ),
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS.copy(
                        accessToken = SAMPLE_TOKENS.accessToken.copy(expiresAt = TEST_INSTANT - 60),
                    )
                )
            }
        )

        val exception = assertFailsWith<OAuthResponseException> { client.runWithTokensOrReset {} }
        assertEquals(OAuthError.ServerError, exception.error)
        runCurrent()
        assertNotNull(client.tokens.value)
    }

    @Test
    fun `invoke should reset tokens when ResetClientStateException is thrown`() = runTest {
        val client = createTestClient(
            initialSnapshot = {
                copy(tokens = SAMPLE_TOKENS)
            }
        )

        assertFalse {
            client.runWithTokensOrReset {
                throw RunWithTokensOrResetUseCase.ResetClientStateException()
            }
        }
        runCurrent()
        assertNull(client.tokens.value)
    }

    @Test
    fun `invoke should run lambda when tokens are valid`() = runTest {
        val client = createTestClient(
            initialSnapshot = {
                copy(tokens = SAMPLE_TOKENS)
            }
        )

        var bodyWasCalled = false

        assertTrue {
            client.runWithTokensOrReset { tokens ->
                assertEquals(SAMPLE_TOKENS, tokens)
                bodyWasCalled = true
            }
        }
        assertTrue(bodyWasCalled)
    }

    @Test
    fun `invoke should run lambda with refreshed tokens`() = runTest {
        val client = createTestClient(
            provider = TestProvider(
                refreshTokenRequest = {
                    RefreshTokenRequest {
                        RefreshTokenRequest.Response(
                            accessToken = SAMPLE_TOKENS.accessToken,
                            refreshToken = SAMPLE_TOKENS.refreshToken,
                            idToken = SAMPLE_TOKENS.idToken,
                        )
                    }
                }
            ),
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS.copy(
                        accessToken = SAMPLE_TOKENS.accessToken.copy(
                            token = "iQUiRkB",
                            expiresAt = TEST_INSTANT - 60,
                        ),
                    )
                )
            }
        )

        var bodyWasCalled = false

        assertTrue {
            client.runWithTokensOrReset { tokens ->
                assertEquals(SAMPLE_TOKENS, tokens)
                bodyWasCalled = true
            }
        }
        assertTrue(bodyWasCalled)
    }
}
