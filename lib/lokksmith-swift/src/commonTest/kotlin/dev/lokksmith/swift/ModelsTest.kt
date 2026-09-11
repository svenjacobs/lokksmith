/*
 * Copyright 2026 Sven Jacobs
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
package dev.lokksmith.swift

import dev.lokksmith.client.Client
import dev.lokksmith.client.request.parameter.Prompt
import dev.lokksmith.client.request.parameter.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ModelsTest {

    private fun idToken(extra: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()) =
        Client.Tokens.IdToken(
            issuer = "https://example.com",
            subject = "8582ce26",
            audiences = listOf("clientId"),
            expiration = 1_700_000_600,
            issuedAt = 1_700_000_000,
            authTime = 1_699_999_000,
            notBefore = 1_699_999_500,
            nonce = "0D1ck61",
            authenticationContextClassReference = "acr-value",
            authenticationMethodsReferences = listOf("pwd", "otp"),
            authorizedParty = "clientId",
            extra = extra,
            raw = "header.payload.signature",
        )

    private fun tokens(idToken: Client.Tokens.IdToken = idToken()) =
        Client.Tokens(
            accessToken =
                Client.Tokens.AccessToken(token = "access-token", expiresAt = 1_700_000_600),
            refreshToken =
                Client.Tokens.RefreshToken(token = "refresh-token", expiresAt = 1_700_600_000),
            idToken = idToken,
        )

    @Test
    fun `toSwift should map every token field`() {
        val swift = tokens().toSwift()

        assertEquals("access-token", swift.accessToken.token)
        assertEquals(1_700_000_600, swift.accessToken.expiresAt)
        assertEquals("refresh-token", swift.refreshToken?.token)
        assertEquals(1_700_600_000, swift.refreshToken?.expiresAt)

        with(swift.idToken) {
            assertEquals("header.payload.signature", raw)
            assertEquals("https://example.com", issuer)
            assertEquals("8582ce26", subject)
            assertEquals(listOf("clientId"), audiences)
            assertEquals(1_700_000_600, expiration)
            assertEquals(1_700_000_000, issuedAt)
            assertEquals(1_699_999_000, authTime)
            assertEquals(1_699_999_500, notBefore)
            assertEquals("0D1ck61", nonce)
            assertEquals("acr-value", authenticationContextClassReference)
            assertEquals(listOf("pwd", "otp"), authenticationMethodsReferences)
            assertEquals("clientId", authorizedParty)
        }
    }

    @Test
    fun `toSwift should map an absent refresh token to null`() {
        val swift =
            Client.Tokens(
                    accessToken =
                        Client.Tokens.AccessToken(token = "access-token", expiresAt = null),
                    refreshToken = null,
                    idToken = idToken(),
                )
                .toSwift()

        assertNull(swift.refreshToken)
        assertNull(swift.accessToken.expiresAt)
    }

    @Test
    fun `toSwift should unquote string claims and keep JSON text for the rest`() {
        val swift =
            tokens(
                    idToken(
                        extra =
                            mapOf(
                                "email" to JsonPrimitive("user@example.com"),
                                "email_verified" to JsonPrimitive(true),
                                "loyalty_id" to JsonPrimitive(42),
                                "address" to
                                    buildJsonObject { put("city", JsonPrimitive("Krefeld")) },
                            )
                    )
                )
                .toSwift()

        assertEquals("user@example.com", swift.idToken.extraClaims["email"])
        assertEquals("true", swift.idToken.extraClaims["email_verified"])
        assertEquals("42", swift.idToken.extraClaims["loyalty_id"])
        assertEquals("""{"city":"Krefeld"}""", swift.idToken.extraClaims["address"])
    }

    @Test
    fun `toSwift should map an empty claim set to an empty map`() {
        assertTrue(tokens().toSwift().idToken.extraClaims.isEmpty())
    }

    @Test
    fun `authorization request should drop openid from the requested scopes`() {
        // The flow appends Scope.OpenId itself, so passing it through would emit it twice.
        val request =
            LokksmithAuthorizationRequest(redirectUri = "my-app://openid-response").apply {
                scopes = listOf("openid", "profile", "email")
            }

        val scopes = request.toCore().scope.map { (it as Scope.Custom).scope }

        assertEquals(listOf("profile", "email"), scopes)
    }

    @Test
    fun `authorization request should drop blank scopes`() {
        val request =
            LokksmithAuthorizationRequest(redirectUri = "my-app://openid-response").apply {
                scopes = listOf("profile", "", "  ")
            }

        assertEquals(listOf("profile"), request.toCore().scope.map { (it as Scope.Custom).scope })
    }

    @Test
    fun `authorization request should map the remaining parameters`() {
        val request =
            LokksmithAuthorizationRequest(redirectUri = "my-app://openid-response").apply {
                prompts = listOf(LokksmithPrompt.Login, LokksmithPrompt.Create)
                uiLocales = listOf("de", "en")
                loginHint = "user@example.com"
                maxAge = 300
                additionalParameters = mapOf("vendor" to "value")
            }

        val core = request.toCore()

        assertEquals("my-app://openid-response", core.redirectUri)
        assertEquals(setOf(Prompt.Login, Prompt.Create), core.prompt)
        assertEquals(listOf("de", "en"), core.uiLocales)
        assertEquals("user@example.com", core.loginHint)
        assertEquals(300, core.maxAge)
        assertEquals(mapOf("vendor" to "value"), core.additionalParameters)
    }

    @Test
    fun `authorization request should default its optional parameters to empty`() {
        val core = LokksmithAuthorizationRequest(redirectUri = "my-app://openid-response").toCore()

        assertTrue(core.scope.isEmpty())
        assertTrue(core.prompt.isEmpty())
        assertTrue(core.uiLocales.isEmpty())
        assertTrue(core.additionalParameters.isEmpty())
        assertNull(core.loginHint)
        assertNull(core.maxAge)
    }

    @Test
    fun `end session request should map its parameters`() {
        val request =
            LokksmithEndSessionRequest(redirectUri = "my-app://openid-response").apply {
                logoutHint = "user@example.com"
                uiLocales = listOf("de")
                additionalParameters = mapOf("vendor" to "value")
            }

        val core = request.toCore()

        assertEquals("my-app://openid-response", core.redirectUri)
        assertEquals("user@example.com", core.logoutHint)
        assertEquals(listOf("de"), core.uiLocales)
        assertEquals(mapOf("vendor" to "value"), core.additionalParameters)
    }

    @Test
    fun `metadata should map every endpoint`() {
        val core =
            LokksmithMetadata(
                    issuer = "https://example.com",
                    authorizationEndpoint = "https://example.com/authorize",
                    tokenEndpoint = "https://example.com/token",
                    jwksUri = "https://example.com/jwks",
                    endSessionEndpoint = "https://example.com/logout",
                    userInfoEndpoint = "https://example.com/userinfo",
                )
                .toCore()

        assertEquals("https://example.com", core.issuer)
        assertEquals("https://example.com/authorize", core.authorizationEndpoint)
        assertEquals("https://example.com/token", core.tokenEndpoint)
        assertEquals("https://example.com/jwks", core.jwksUri)
        assertEquals("https://example.com/logout", core.endSessionEndpoint)
        assertEquals("https://example.com/userinfo", core.userInfoEndpoint)
    }

    @Test
    fun `the short metadata constructor should leave the optional endpoints unset`() {
        val core =
            LokksmithMetadata(
                    issuer = "https://example.com",
                    authorizationEndpoint = "https://example.com/authorize",
                    tokenEndpoint = "https://example.com/token",
                )
                .toCore()

        assertNull(core.jwksUri)
        assertNull(core.endSessionEndpoint)
        assertNull(core.userInfoEndpoint)
    }

    @Test
    fun `client configuration should carry either a discovery URL or metadata`() {
        val discovery =
            LokksmithClientConfiguration.discovery(
                clientId = "clientId",
                discoveryUrl = "https://example.com/.well-known/openid-configuration",
            )

        assertEquals("clientId", discovery.clientId)
        assertEquals(
            "https://example.com/.well-known/openid-configuration",
            discovery.discoveryUrl,
        )
        assertNull(discovery.metadata)

        val static =
            LokksmithClientConfiguration.metadata(
                clientId = "clientId",
                metadata =
                    LokksmithMetadata(
                        issuer = "https://example.com",
                        authorizationEndpoint = "https://example.com/authorize",
                        tokenEndpoint = "https://example.com/token",
                    ),
            )

        assertEquals("clientId", static.clientId)
        assertNull(static.discoveryUrl)
        assertEquals("https://example.com", static.metadata?.issuer)
    }

    @Test
    fun `prompts should map onto their core counterparts`() {
        assertEquals(
            listOf(
                Prompt.None,
                Prompt.Login,
                Prompt.Consent,
                Prompt.SelectAccount,
                Prompt.Create,
            ),
            LokksmithPrompt.entries.map { it.toCore() },
        )
    }
}
