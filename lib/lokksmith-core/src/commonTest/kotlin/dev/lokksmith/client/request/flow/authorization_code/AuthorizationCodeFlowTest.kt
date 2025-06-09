package dev.lokksmith.client.request.flow.authorization_code

import dev.lokksmith.client.Id
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.Key
import dev.lokksmith.client.TEST_INSTANT
import dev.lokksmith.client.TestProvider
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.client.createTestClient
import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import dev.lokksmith.client.request.OAuthError
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.parameter.CodeChallengeMethod
import dev.lokksmith.client.request.parameter.GrantType
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.request.parameter.Prompt
import dev.lokksmith.client.request.parameter.Scope
import dev.lokksmith.client.request.token.TokenErrorResponse
import dev.lokksmith.client.request.token.TokenResponse
import dev.lokksmith.client.snapshot.Snapshot.EphemeralAuthorizationCodeFlowState
import dev.lokksmith.client.snapshot.Snapshot.FlowResult
import dev.lokksmith.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.buildUrl
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorizationCodeFlowTest {

    private val httpJson = Json { prettyPrint = true }

    @Test
    fun `prepare() should prepare Authorization Code Flow`() = runTest {
        val (flow, client) = createFlow(
            request = AuthorizationCodeFlow.Request(
                redirectUri = "https://example.com/app/redirect",
                scope = setOf(Scope.Email, Scope.Profile),
                prompt = setOf(Prompt.Login),
            ),
        )

        val initiation = flow.prepare()
        runCurrent()
        val requestUrl = Url(initiation.requestUrl)

        val codeChallengeStrategy = CodeChallengeFactory.forMethod(
            CodeChallengeMethod.SHA256,
        )(flow.codeVerifier!!)

        assertEquals("key", initiation.clientKey)

        assertEquals("https", requestUrl.protocol.name)
        assertEquals("example.com", requestUrl.host)
        assertEquals("/authorizationEndpoint", requestUrl.encodedPath)
        assertEquals("email profile openid", requestUrl.parameters[Parameter.SCOPE])
        assertEquals("login", requestUrl.parameters[Parameter.PROMPT])
        assertEquals("code", requestUrl.parameters[Parameter.RESPONSE_TYPE])
        assertEquals("clientId", requestUrl.parameters[Parameter.CLIENT_ID])
        assertEquals(
            "https://example.com/app/redirect",
            requestUrl.parameters[Parameter.REDIRECT_URI]
        )
        assertEquals(flow.state, requestUrl.parameters[Parameter.STATE])
        assertEquals(flow.nonce, requestUrl.parameters[Parameter.NONCE])
        assertEquals(codeChallengeStrategy, requestUrl.parameters[Parameter.CODE_CHALLENGE])
        assertEquals("S256", requestUrl.parameters[Parameter.CODE_CHALLENGE_METHOD])

        val flowState = assertIs<EphemeralAuthorizationCodeFlowState>(
            client.snapshots.value.ephemeralFlowState
        )
        assertEquals(flow.state, flowState.state)
        assertEquals(flow.codeVerifier, flowState.codeVerifier)
        assertEquals("https://example.com/app/redirect", flowState.redirectUri)
    }

    @Test
    fun `prepare() should prepare Authorization Code Flow without PKCE`() = runTest {
        val (flow, client) = createFlow(
            request = AuthorizationCodeFlow.Request(
                redirectUri = "https://example.com/app/redirect",
                codeChallengeMethod = null,
            ),
        )

        val initiation = flow.prepare()
        runCurrent()
        val requestUrl = Url(initiation.requestUrl)

        assertEquals("key", initiation.clientKey)

        assertEquals(null, requestUrl.parameters[Parameter.CODE_CHALLENGE])
        assertEquals(null, requestUrl.parameters[Parameter.CODE_CHALLENGE_METHOD])

        val flowState = assertIs<EphemeralAuthorizationCodeFlowState>(
            client.snapshots.value.ephemeralFlowState
        )
        assertNull(flowState.codeVerifier)
    }

    @Test
    fun `onResponse() should handle successful response`() = runTest {
        val jwtEncoder = JwtEncoder(Json)

        val (flow, client, testState) = createFlow { testState ->
            { request ->
                when (request.url.toString()) {
                    "https://example.com/tokenEndpoint" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        val body = assertIs<FormDataContent>(request.body)

                        assertEquals(
                            GrantType.AuthorizationCode.value,
                            body.formData[Parameter.GRANT_TYPE],
                        )
                        assertEquals("clientId", body.formData[Parameter.CLIENT_ID])
                        assertEquals("s7FBqWPnG2", body.formData[Parameter.CODE])
                        assertEquals(
                            "https://example.com/app/redirect",
                            body.formData[Parameter.REDIRECT_URI]
                        )
                        assertEquals(testState.codeVerifier, body.formData[Parameter.CODE_VERIFIER])

                        val idToken = Jwt(
                            header = Jwt.Header(
                                alg = "none",
                            ),
                            payload = Jwt.Payload(
                                iss = "issuer",
                                sub = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
                                aud = listOf("clientId"),
                                exp = TEST_INSTANT + 600,
                                iat = TEST_INSTANT,
                                extra = mapOf("nonce" to JsonPrimitive(testState.nonce)),
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
        }

        flow.prepare()
        runCurrent()

        assertNotNull(
            client.snapshots.value.ephemeralFlowState,
            "ephemeralFlowState must not be null",
        )
        assertNotNull(
            client.snapshots.value.nonce,
            "nonce must not be null",
        )
        assertNull(
            client.snapshots.value.flowResult,
            "flowResult must be null",
        )

        // values that we require in the mock request lambda above which however can't access the
        // flow instance before it was created.
        testState.codeVerifier = flow.codeVerifier
        testState.nonce = flow.nonce

        val responseUrl = buildUrl {
            takeFrom("https://example.com/app/redirect")
            parameters[Parameter.STATE] = flow.state
            parameters[Parameter.CODE] = "s7FBqWPnG2"
        }.toString()

        flow.onResponse(responseUrl)
        runCurrent()

        val tokens = assertNotNull(client.tokens.value)
        assertEquals(tokens.accessToken.token, "Lh0rP8vrtQH")
        assertEquals(tokens.accessToken.expiresAt, TEST_INSTANT + 600)
        assertEquals(tokens.refreshToken?.token, "cyaVZ3zPU")
        assertEquals(tokens.refreshToken?.expiresAt, TEST_INSTANT + 1_209_600)
        assertEquals(tokens.idToken.issuer, "issuer")
        assertEquals(tokens.idToken.subject, "8582ce26-3994-42e7-afb0-39d42e18fd1f")
        assertEquals(tokens.idToken.audiences, listOf("clientId"))
        assertEquals(tokens.idToken.expiration, TEST_INSTANT + 600)
        assertEquals(tokens.idToken.issuedAt, TEST_INSTANT)
        assertEquals(tokens.idToken.nonce, flow.nonce)

        assertEquals(FlowResult.Success, client.snapshots.value.flowResult)
    }

    @Test
    fun `onResponse() should handle error in code response`() = runTest {
        val (flow, client) = createFlow()

        flow.prepare()
        runCurrent()

        val responseUrl = buildUrl {
            takeFrom("https://example.com/app/redirect")
            parameters[Parameter.STATE] = flow.state
            parameters[Parameter.ERROR] = OAuthError.InvalidGrant.code
            parameters[Parameter.ERROR_DESCRIPTION] = "error description"
            parameters[Parameter.ERROR_URI] = "error URI"
        }.toString()

        val exception = assertFailsWith<OAuthResponseException> {
            flow.onResponse(responseUrl)
        }

        runCurrent()

        assertEquals(exception.error, OAuthError.InvalidGrant)
        assertEquals(exception.errorDescription, "error description")
        assertEquals(exception.errorUri, "error URI")

        assertEquals(
            FlowResult.Error(
                message = """OAuthResponseException(error="invalid_grant, errorDescription="error description", errorUri="error URI")""",
                code = OAuthError.InvalidGrant.code,
            ), client.snapshots.value.flowResult
        )

        assertNull(client.snapshots.value.ephemeralFlowState)
    }

    @Test
    fun `onResponse() should handle error in token response`() = runTest {
        val (flow, client) = createFlow { testState ->
            {
                val response = httpJson.encodeToString(
                    TokenErrorResponse(
                        error = OAuthError.InvalidGrant.code,
                        errorDescription = "error description",
                        errorUri = "error URI",
                    )
                )

                respond(
                    content = response,
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        }

        flow.prepare()
        runCurrent()

        val responseUrl = buildUrl {
            takeFrom("https://example.com/app/redirect")
            parameters[Parameter.STATE] = flow.state
            parameters[Parameter.CODE] = "s7FBqWPnG2"
        }.toString()

        val exception = assertFailsWith<OAuthResponseException> {
            flow.onResponse(responseUrl)
        }

        runCurrent()

        assertEquals(exception.error, OAuthError.InvalidGrant)
        assertEquals(exception.errorDescription, "error description")
        assertEquals(exception.errorUri, "error URI")

        assertEquals(
            FlowResult.Error(
                message = """OAuthResponseException(error="invalid_grant, errorDescription="error description", errorUri="error URI", statusCode=400)""",
                code = OAuthError.InvalidGrant.code,
            ), client.snapshots.value.flowResult
        )

        assertNull(client.snapshots.value.ephemeralFlowState)
    }

    @Test
    fun `cancel() should cancel flow`() = runTest {
        val (flow, client) = createFlow { testState ->
            {
                respond(
                    content = "",
                    status = HttpStatusCode.BadRequest,
                    headersOf("Content-Type", "application/json"),
                )
            }
        }

        flow.prepare()
        runCurrent()

        assertNotNull(
            client.snapshots.value.ephemeralFlowState,
            "ephemeralFlowState must not be null",
        )
        assertNotNull(
            client.snapshots.value.nonce,
            "nonce must not be null",
        )
        assertNull(
            client.snapshots.value.flowResult,
            "flowResult must be null",
        )

        flow.cancel()
        runCurrent()

        assertNull(client.snapshots.value.ephemeralFlowState)
        assertNull(client.snapshots.value.nonce)
        assertEquals(FlowResult.Cancelled, client.snapshots.value.flowResult)
    }
}

private data class TestState(
    var codeVerifier: String? = null,
    var nonce: String? = null,
)

private suspend fun TestScope.createFlow(
    key: Key = "key".asKey(),
    id: Id = "clientId".asId(),
    request: AuthorizationCodeFlow.Request = AuthorizationCodeFlow.Request(
        redirectUri = "https://example.com/app/redirect",
    ),
    requestHandler: (TestState) -> MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { { respondBadRequest() } },
): Triple<AuthorizationCodeFlow, InternalClient, TestState> {
    val testState = TestState()
    val mockEngine = MockEngine(requestHandler(testState))
    val httpClient = createHttpClient(mockEngine)

    val client = createTestClient(
        key = key,
        id = id,
        provider = TestProvider(httpClient = httpClient),
    )

    return Triple(
        AuthorizationCodeFlow.create(
            request = request,
            client = client,
            httpClient = httpClient,
            serializer = Json,
        ),
        client,
        testState,
    )
}
