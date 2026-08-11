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
package dev.lokksmith.client.snapshot

import dev.lokksmith.client.Client
import dev.lokksmith.client.Key
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SnapshotEncryptionTest {

    private val dek = ByteArray(32) { it.toByte() }

    private fun cipher() = AesGcmSnapshotCipher { dek }

    private fun encryptingStore(persistence: PersistenceFake): SnapshotStore =
        SnapshotStoreImpl(
            persistence = EncryptingPersistence(persistence, cipher()),
            serializer = Json,
        )

    @Test
    fun `cipher round-trips and produces distinct ciphertexts`() = runTest {
        val cipher = cipher()
        val plaintext = """{"hello":"world"}"""

        val a = cipher.encrypt(plaintext)
        val b = cipher.encrypt(plaintext)

        assertNotEquals(plaintext, a)
        assertNotEquals(a, b) // random IV per encryption
        assertEquals(plaintext, cipher.decrypt(a))
        assertEquals(plaintext, cipher.decrypt(b))
    }

    @Test
    fun `set then observe round-trips the snapshot`() = runTest {
        val persistence = PersistenceFake()
        val store = encryptingStore(persistence)
        val key = "key".asKey()
        val snapshot = newSnapshot(key)

        store.set(key, snapshot)

        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `getForState round-trips through encryption`() = runTest {
        val persistence = PersistenceFake()
        val store = encryptingStore(persistence)
        val key = "key".asKey()
        val state = "Ly5GJLkj"
        val snapshot = newSnapshot(key, state)

        store.set(key, snapshot)

        assertEquals(snapshot, store.getForState(state))
    }

    @Test
    fun `stored value is ciphertext and not plaintext`() = runTest {
        val persistence = PersistenceFake()
        val store = encryptingStore(persistence)
        val key = "key".asKey()
        val snapshot = newSnapshot(key)

        store.set(key, snapshot)

        val stored = persistence.memory.value.getValue(key.value)
        val plaintext = Json.encodeToString(snapshot)
        assertNotEquals(plaintext, stored)
        assertTrue("issuer" !in stored, "issuer leaked into stored value")
        assertTrue("tokenEndpoint" !in stored, "endpoint leaked into stored value")
    }

    @Test
    fun `legacy plaintext is read via fallback`() = runTest {
        val key = "key".asKey()
        val snapshot = newSnapshot(key)
        val persistence = PersistenceFake(mapOf(key.value to Json.encodeToString(snapshot)))
        val store = encryptingStore(persistence)

        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `legacy plaintext is re-encrypted on next write`() = runTest {
        val key = "key".asKey()
        val snapshot = newSnapshot(key)
        val persistence = PersistenceFake(mapOf(key.value to Json.encodeToString(snapshot)))
        val store = encryptingStore(persistence)

        val loaded = store.observe(key).firstOrNull()
        store.set(key, loaded!!)

        val stored = persistence.memory.value.getValue(key.value)
        assertNotEquals(Json.encodeToString(snapshot), stored)
        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `unreadable value with wrong key is treated as absent`() = runTest {
        val key = "key".asKey()
        val snapshot = newSnapshot(key)
        val persistence = PersistenceFake()
        SnapshotStoreImpl(EncryptingPersistence(persistence, AesGcmSnapshotCipher { dek }), Json)
            .set(key, snapshot)
        val otherDek = ByteArray(32) { (it + 1).toByte() }
        val store =
            SnapshotStoreImpl(
                EncryptingPersistence(persistence, AesGcmSnapshotCipher { otherDek }),
                Json,
            )

        assertNull(store.observe(key).firstOrNull())
    }

    @Test
    fun `unreadable value can still be deleted`() = runTest {
        val key = "key".asKey()
        val persistence = PersistenceFake()
        SnapshotStoreImpl(EncryptingPersistence(persistence, AesGcmSnapshotCipher { dek }), Json)
            .set(key, newSnapshot(key))
        val otherDek = ByteArray(32) { (it + 1).toByte() }
        val store =
            SnapshotStoreImpl(
                EncryptingPersistence(persistence, AesGcmSnapshotCipher { otherDek }),
                Json,
            )

        assertTrue(store.delete(key), "an unreadable entry should still be deletable")
        assertFalse(key.value in persistence.memory.value, "the physical row should be gone")
    }

    @Test
    fun `disabled encryption stores plaintext and round-trips`() = runTest {
        val persistence = PersistenceFake()
        val store =
            SnapshotStoreImpl(EncryptingPersistence(persistence, PlaintextSnapshotCipher), Json)
        val key = "key".asKey()
        val snapshot = newSnapshot(key)

        store.set(key, snapshot)

        assertEquals(Json.encodeToString(snapshot), persistence.memory.value.getValue(key.value))
        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `disabled encryption treats leftover ciphertext as absent`() = runTest {
        val persistence = PersistenceFake()
        val key = "key".asKey()
        // A value written while encryption was enabled.
        SnapshotStoreImpl(EncryptingPersistence(persistence, cipher()), Json)
            .set(key, newSnapshot(key))

        val store =
            SnapshotStoreImpl(EncryptingPersistence(persistence, PlaintextSnapshotCipher), Json)

        assertNull(store.observe(key).firstOrNull())
    }
}

private fun newSnapshot(key: Key, state: String? = null): Snapshot {
    val ephemeralFlowState = state?.let {
        Snapshot.EphemeralAuthorizationCodeFlowState(
            state = state,
            redirectUri = "redirectUri",
            codeVerifier = null,
            responseUri = null,
        )
    }

    return Snapshot(
        key = key,
        id = key.value.asId(),
        metadata =
            Client.Metadata(
                issuer = "issuer",
                authorizationEndpoint = "authorizationEndpoint",
                tokenEndpoint = "tokenEndpoint",
            ),
        ephemeralFlowState = ephemeralFlowState,
    )
}
