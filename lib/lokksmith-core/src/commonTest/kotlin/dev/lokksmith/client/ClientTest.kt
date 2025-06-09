package dev.lokksmith.client

import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import dev.lokksmith.client.request.OAuthError
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.flow.AuthFlow
import dev.lokksmith.client.request.flow.authorization_code.AuthorizationCodeFlow
import dev.lokksmith.client.request.flow.end_session.EndSessionFlow
import dev.lokksmith.client.request.parameter.GrantType
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.request.refresh.RefreshTokenRequest
import dev.lokksmith.client.request.refresh.RefreshTokenRequestImpl
import dev.lokksmith.client.request.token.TokenErrorResponse
import dev.lokksmith.client.request.token.TokenResponse
import dev.lokksmith.client.snapshot.PersistenceFake
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.FlowResult
import dev.lokksmith.client.snapshot.SnapshotStore
import dev.lokksmith.client.snapshot.SnapshotStoreImpl
import dev.lokksmith.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ClientTest {

    private val httpJson = Json { prettyPrint = true }

    @Test
    fun `refresh() should refresh tokens`() = runTest {
        val jwtEncoder = JwtEncoder(Json)
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/tokenEndpoint" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    val body = assertIs<FormDataContent>(request.body)

                    assertEquals(
                        GrantType.RefreshToken.value,
                        body.formData[Parameter.GRANT_TYPE],
                    )
                    assertEquals("clientId", body.formData[Parameter.CLIENT_ID])
                    assertEquals("bMGysPYch", body.formData[Parameter.REFRESH_TOKEN])

                    val idToken = Jwt(
                        header = Jwt.Header(
                            alg = "none",
                        ),
                        payload = Jwt.Payload(
                            iss = "issuer",
                            sub = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                            aud = listOf("clientId"),
                            exp = 1748706999 + 600,
                            iat = 1748706999,
                            extra = mapOf("nonce" to JsonPrimitive("0D1ck61")),
                        ),
                    )

                    val response = TokenResponse(
                        tokenType = "Bearer",
                        accessToken = "Lh0rP8vrtQH",
                        expiresIn = 600,
                        refreshToken = "cyaVZ3zPU",
                        refreshExpiresIn = 1_209_600,
                        idToken = jwtEncoder.encode(idToken),
                    )

                    val json = httpJson.encodeToString(response)

                    respond(
                        content = json,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respondBadRequest()
            }
        }

        val client = createTestClient(
            provider = TestProvider(
                httpClient = createHttpClient(engine),
                instantProvider = { 1748706999 },
            ),
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS,
                    nonce = "0D1ck61",
                )
            }
        )

        assertEquals(SAMPLE_TOKENS, client.tokens.value)

        client.refresh()
        runCurrent()

        val tokens = assertNotNull(client.tokens.value)
        assertEquals("Lh0rP8vrtQH", tokens.accessToken.token)
        assertEquals(1748706999 + 600, tokens.accessToken.expiresAt)
        assertEquals("cyaVZ3zPU", tokens.refreshToken?.token)
        assertEquals(1748706999 + 1_209_600, tokens.refreshToken?.expiresAt)
        assertEquals(1748706999 + 600, tokens.idToken.expiration)
        assertEquals(1748706999, tokens.idToken.issuedAt)
    }

    @Test
    fun `refresh() should throw exception on OAuth error`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/tokenEndpoint" -> {
                    val response = TokenErrorResponse(
                        error = OAuthError.InvalidGrant.code,
                        errorDescription = "error description",
                        errorUri = "error URI",
                    )

                    val json = httpJson.encodeToString(response)

                    respond(
                        content = json,
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respondBadRequest()
            }
        }

        val client = createTestClient(
            provider = TestProvider(
                httpClient = createHttpClient(engine),
                instantProvider = { 1748706999 },
            ),
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS,
                    nonce = "0D1ck61",
                )
            }
        )

        assertEquals(SAMPLE_TOKENS, client.tokens.value)

        val exception = assertFailsWith<OAuthResponseException> {
            client.refresh()
        }

        runCurrent()

        assertEquals(OAuthError.InvalidGrant, exception.error)
        assertEquals("error description", exception.errorDescription)
        assertEquals("error URI", exception.errorUri)
    }

    @Test
    fun `resetTokens() should reset tokens`() = runTest {
        val client = createTestClient(
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS,
                    nonce = "0D1ck61",
                    flowResult = FlowResult.Success,
                )
            }
        )

        assertEquals(SAMPLE_TOKENS, client.snapshots.value.tokens)
        assertEquals("0D1ck61", client.snapshots.value.nonce)
        assertEquals(FlowResult.Success, client.snapshots.value.flowResult)

        client.resetTokens()
        runCurrent()

        assertNull(client.snapshots.value.tokens)
        assertNull(client.snapshots.value.nonce)
        assertNull(client.snapshots.value.flowResult)
    }

    @Test
    fun `runWithTokens() should run lambda with current tokens`() = runTest {
        val client = createTestClient(
            provider = TestProvider(
                refreshTokenRequest = {
                    RefreshTokenRequest {
                        fail("RefreshTokenRequest was called")
                    }
                }
            ),
            initialSnapshot = {
                copy(
                    tokens = SAMPLE_TOKENS,
                )
            }
        )

        client.runWithTokens { tokens ->
            assertEquals(
                SAMPLE_TOKENS,
                tokens
            )
        }
    }

    @Test
    fun `runWithTokens() should run lambda with refreshed tokens`() = runTest {
        var refreshTokenCalled = false
        val client = createTestClient(
            provider = TestProvider(
                refreshTokenRequest = {
                    RefreshTokenRequest {
                        refreshTokenCalled = true
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
                    tokens = Client.Tokens(
                        accessToken = Client.Tokens.AccessToken(
                            token = "sjrgCu2Elk4",
                            expiresAt = TEST_INSTANT - 60,
                        ),
                        refreshToken = Client.Tokens.RefreshToken(
                            token = "zMyQZqI",
                            expiresAt = null,
                        ),
                        idToken = SAMPLE_TOKENS.idToken.copy(
                            expiration = TEST_INSTANT - 60,
                            issuedAt = TEST_INSTANT - 120,
                        ),
                    ),
                )
            }
        )

        client.runWithTokens { tokens ->
            assertEquals(
                SAMPLE_TOKENS,
                tokens
            )
        }

        assertTrue(refreshTokenCalled)
    }

    @Test
    fun `isExpired() should return true for expired token`() = runTest {
        val client = createTestClient()

        assertTrue {
            client.isExpired(
                Tokens.AccessToken(
                    token = "xaneJeBElWQ",
                    expiresAt = TEST_INSTANT - 60,
                )
            )
        }

        assertTrue {
            client.isExpired(
                SAMPLE_TOKENS.idToken.copy(
                    expiration = TEST_INSTANT - 60,
                )
            )
        }
    }

    @Test
    fun `isExpired() should return false for valid token`() = runTest {
        val client = createTestClient()

        assertFalse {
            client.isExpired(
                Tokens.AccessToken(
                    token = "xaneJeBElWQ",
                    expiresAt = TEST_INSTANT + 120,
                )
            )
        }

        assertFalse {
            client.isExpired(
                SAMPLE_TOKENS.idToken.copy(
                    expiration = TEST_INSTANT + 120,
                )
            )
        }
    }

    @Test
    fun `dispose() should cancel coroutine`() = runTest {
        val client = createTestClient()
        client.dispose()
        assertFalse((client as ClientImpl).coroutineScope.isActive)
    }
}

internal data class TestProvider(
    private val httpClient: HttpClient = createHttpClient(engine = MockEngine { respondBadRequest() }),
    private val serializer: Json = Json,
    override val instantProvider: InstantProvider = { TEST_INSTANT },
    override val refreshTokenRequest: (InternalClient) -> RefreshTokenRequest = { client ->
        RefreshTokenRequestImpl(
            client = client,
            httpClient = httpClient,
            serializer = serializer,
        )
    },
    override val authorizationCodeFlow: (InternalClient, AuthorizationCodeFlow.Request) -> AuthFlow = { client, request ->
        AuthorizationCodeFlow.create(
            request = request,
            client = client,
            httpClient = httpClient,
            serializer = serializer,
        )
    },
    override val endSessionFlow: (InternalClient, EndSessionFlow.Request) -> AuthFlow? = { client, request ->
        EndSessionFlow.createOrNull(
            client = client,
            request = request,
        )
    },
) : InternalClient.Provider

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.createTestClient(
    key: Key = "key".asKey(),
    id: Id = "clientId".asId(),
    snapshotStore: SnapshotStore = SnapshotStoreImpl(
        persistence = PersistenceFake(),
        serializer = Json,
    ),
    provider: InternalClient.Provider = TestProvider(),
    initialSnapshot: Snapshot.() -> Snapshot = { this },
): InternalClient {
    // Create initial snapshot
    snapshotStore.set(
        key = key,
        snapshot = Snapshot(
            key = key,
            id = id,
            metadata = Client.Metadata(
                issuer = "issuer",
                authorizationEndpoint = "https://example.com/authorizationEndpoint",
                tokenEndpoint = "https://example.com/tokenEndpoint",
                endSessionEndpoint = "https://example.com/endSessionEndpoint",
            ),
            options = Client.Options(),
        )
    )

    val client = ClientImpl.create(
        key = key,
        coroutineScope = backgroundScope,
        snapshotStore = snapshotStore,
        provider = provider,
    ) as InternalClient

    client.updateSnapshot(initialSnapshot)
    runCurrent()

    return client
}
