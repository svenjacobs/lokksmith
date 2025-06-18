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
package dev.lokksmith.client

import dev.lokksmith.client.jwt.Jwt
import dev.lokksmith.client.jwt.JwtEncoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationTest {

    private val migration = Migration(serializer = Json)
    private val jwtEncoder = JwtEncoder(serializer = Json)
    private val idToken = Jwt(
        header = Jwt.Header(
            alg = "none",
        ),
        payload = Jwt.Payload(
            iss = "issuer",
            sub = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
            aud = listOf("clientId"),
            exp = TEST_INSTANT + 600,
            iat = TEST_INSTANT,
        ),
    )

    @Test
    fun `setTokens should set tokens on client`() = runTest {
        val client = createTestClient()

        migration.setTokens(
            client = client,
            accessToken = "eosvcZMZq7e",
            accessTokenExpiresAt = TEST_INSTANT + 600,
            refreshToken = "m3h8Gu2r",
            refreshTokenExpiresAt = TEST_INSTANT + 1_209_600,
            idToken = jwtEncoder.encode(idToken),
        )

        runCurrent()
        val tokens = client.tokens.value

        assertEquals("eosvcZMZq7e", tokens?.accessToken?.token)
        assertEquals(TEST_INSTANT + 600, tokens?.accessToken?.expiresAt)
        assertEquals("m3h8Gu2r", tokens?.refreshToken?.token)
        assertEquals(TEST_INSTANT + 1_209_600, tokens?.refreshToken?.expiresAt)
        assertEquals("issuer", tokens?.idToken?.issuer)
        assertEquals("8582ce26-3994-42e7-afb0-39d42e18fd1f", tokens?.idToken?.subject)
        assertEquals(TEST_INSTANT + 600, tokens?.idToken?.expiration)
        assertEquals(TEST_INSTANT, tokens?.idToken?.issuedAt)
        assertContains(tokens?.idToken?.audiences.orEmpty(), "clientId")
    }

    @Test
    fun `setTokens should throw exception on migrated client`() = runTest {
        val client = createTestClient {
            copy(migrated = true)
        }

        assertFailsWith<IllegalStateException> {
            migration.setTokens(
                client = client,
                accessToken = "eosvcZMZq7e",
                accessTokenExpiresAt = TEST_INSTANT + 600,
                refreshToken = "m3h8Gu2r",
                refreshTokenExpiresAt = TEST_INSTANT + 1_209_600,
                idToken = jwtEncoder.encode(idToken),
            )
        }
    }

    @Test
    fun `isMigrated should return true after migration`() = runTest {
        val client = createTestClient()

        assertFalse(migration.isMigrated(client))

        migration.setTokens(
            client = client,
            accessToken = "eosvcZMZq7e",
            accessTokenExpiresAt = TEST_INSTANT + 600,
            refreshToken = "m3h8Gu2r",
            refreshTokenExpiresAt = TEST_INSTANT + 1_209_600,
            idToken = jwtEncoder.encode(idToken),
        )

        runCurrent()

        assertTrue(migration.isMigrated(client))
    }

    @Test
    fun `setMigrated should set the migration flag`() = runTest {
        val client = createTestClient()

        assertFalse(migration.isMigrated(client))

        migration.setMigrated(client)
        runCurrent()

        assertTrue(migration.isMigrated(client))
    }
}
