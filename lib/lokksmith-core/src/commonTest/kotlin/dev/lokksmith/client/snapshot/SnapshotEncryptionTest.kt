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
import dev.lokksmith.client.snapshot.InternalSnapshotStore.Persistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SnapshotEncryptionTest {

    private val dek = ByteArray(32) { it.toByte() }
    private val otherDek = ByteArray(32) { (it + 1).toByte() }

    private fun cipher(key: ByteArray = dek) = AesGcmSnapshotCipher { key }

    private fun encryptingPersistence(
        persistence: Persistence,
        cipher: SnapshotCipher = cipher(),
        migrationState: SnapshotMigrationState = SnapshotMigrationStateFake(),
    ) = EncryptingPersistence(persistence, cipher, migrationState)

    private fun encryptingStore(
        persistence: Persistence,
        cipher: SnapshotCipher = cipher(),
        migrationState: SnapshotMigrationState = SnapshotMigrationStateFake(),
    ): SnapshotStore =
        SnapshotStoreImpl(
            persistence = encryptingPersistence(persistence, cipher, migrationState),
            serializer = Json,
        )

    @Test
    fun `cipher round-trips and produces distinct ciphertexts`() = runTest {
        val cipher = cipher()
        val plaintext = """{"hello":"world"}"""

        val a = cipher.encrypt("key", plaintext)
        val b = cipher.encrypt("key", plaintext)

        assertNotEquals(plaintext, a)
        assertNotEquals(a, b) // random IV per encryption
        assertEquals(plaintext, cipher.decrypt("key", a))
        assertEquals(plaintext, cipher.decrypt("key", b))
    }

    @Test
    fun `cipher rejects a value encrypted for a different entry`() = runTest {
        val cipher = cipher()

        val encrypted = cipher.encrypt("clientA", """{"hello":"world"}""")

        assertFailsWith<UndecryptableSnapshotException> { cipher.decrypt("clientB", encrypted) }
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
    fun `a snapshot cannot be relocated to another entry`() = runTest {
        val persistence = PersistenceFake()
        val victim = "victim".asKey()
        val attacker = "attacker".asKey()
        val store = encryptingStore(persistence)
        store.set(victim, newSnapshot(victim))

        // Copy the victim's ciphertext into another entry, as someone with write access to the
        // storage file but no access to the key could.
        val ciphertext = persistence.memory.value.getValue(victim.value)
        persistence.seed(attacker.value, ciphertext)

        assertNull(
            store.observe(attacker).firstOrNull(),
            "a ciphertext written for another entry must not decrypt",
        )
        assertEquals(
            newSnapshot(victim),
            store.observe(victim).firstOrNull(),
            "the original entry is unaffected",
        )
    }

    @Test
    fun `legacy plaintext is migrated on first access without any write by the caller`() = runTest {
        val key = "key".asKey()
        val snapshot = newSnapshot(key)
        val plaintext = Json.encodeToString(snapshot)
        val persistence = PersistenceFake(mapOf(key.value to plaintext))
        val migrationState = SnapshotMigrationStateFake()
        val store = encryptingStore(persistence, migrationState = migrationState)

        // A single read. Nothing calls set().
        assertEquals(snapshot, store.observe(key).firstOrNull())

        val stored = persistence.memory.value.getValue(key.value)
        assertNotEquals(plaintext, stored, "plaintext must not survive the first access")
        assertFalse(
            stored.trimStart().startsWith('{'),
            "the stored value should be ciphertext, not plaintext JSON",
        )
        assertTrue(migrationState.isMigrated(), "the migration should have been recorded")
    }

    @Test
    fun `migration converts every plaintext entry and not just the one being read`() = runTest {
        val read = "read".asKey()
        val untouched = "untouched".asKey()
        val persistence =
            PersistenceFake(
                mapOf(
                    read.value to Json.encodeToString(newSnapshot(read)),
                    untouched.value to Json.encodeToString(newSnapshot(untouched)),
                )
            )
        val store = encryptingStore(persistence)

        store.observe(read).firstOrNull()

        assertFalse(
            persistence.memory.value.getValue(untouched.value).trimStart().startsWith('{'),
            "an entry nobody read should have been migrated too",
        )
        assertEquals(newSnapshot(untouched), store.observe(untouched).firstOrNull())
    }

    @Test
    fun `an empty store is marked migrated without creating key material`() = runTest {
        val migrationState = SnapshotMigrationStateFake()
        var dekRequested = false
        val cipher = AesGcmSnapshotCipher {
            dekRequested = true
            dek
        }
        val store = encryptingStore(PersistenceFake(), cipher, migrationState)

        assertNull(store.observe("key".asKey()).firstOrNull())

        assertTrue(migrationState.isMigrated())
        assertFalse(dekRequested, "nothing to migrate should not force a DEK to be created")
    }

    @Test
    fun `plaintext is rejected once the store has been migrated`() = runTest {
        val key = "key".asKey()
        val plaintext = Json.encodeToString(newSnapshot(key))
        val persistence = PersistenceFake(mapOf(key.value to plaintext))
        // The marker is already set, so this plaintext cannot be a legitimate pre-migration value:
        // it was injected, or written by a downgraded version.
        val migrationState = SnapshotMigrationStateFake(migrated = true)
        val store = encryptingStore(persistence, migrationState = migrationState)

        assertNull(
            store.observe(key).firstOrNull(),
            "plaintext must not be accepted after migration",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a delete during migration is not undone by the sweep`() = runTest {
        val key = "key".asKey()
        val persistence = PersistenceFake(mapOf(key.value to Json.encodeToString(newSnapshot(key))))
        // Holds the sweep open after it has read the plaintext but before it writes anything back.
        val keyMaterial = CompletableDeferred<Unit>()
        val store =
            encryptingStore(
                persistence,
                AesGcmSnapshotCipher {
                    keyMaterial.await()
                    dek
                },
            )

        val read = launch { store.observe(key).firstOrNull() }
        runCurrent()

        val deleted = async { store.delete(key) }
        runCurrent()

        keyMaterial.complete(Unit)
        read.join()

        assertTrue(deleted.await(), "the entry existed, so it should report as deleted")
        assertFalse(
            key.value in persistence.memory.value,
            "the sweep must not write back an entry that was deleted while it ran",
        )
    }

    @Test
    fun `unreadable value with wrong key is treated as absent`() = runTest {
        val key = "key".asKey()
        val persistence = PersistenceFake()
        encryptingStore(persistence).set(key, newSnapshot(key))

        val store = encryptingStore(persistence, cipher(otherDek))

        assertNull(store.observe(key).firstOrNull())
    }

    @Test
    fun `unreadable value can still be deleted`() = runTest {
        val key = "key".asKey()
        val persistence = PersistenceFake()
        encryptingStore(persistence).set(key, newSnapshot(key))

        val store = encryptingStore(persistence, cipher(otherDek))

        assertTrue(store.delete(key), "an unreadable entry should still be deletable")
        assertFalse(key.value in persistence.memory.value, "the physical row should be gone")
    }

    @Test
    fun `disabled encryption stores plaintext and round-trips`() = runTest {
        val persistence = PersistenceFake()
        val store = encryptingStore(persistence, PlaintextSnapshotCipher)
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
        encryptingStore(persistence).set(key, newSnapshot(key))

        val store = encryptingStore(persistence, PlaintextSnapshotCipher)

        assertNull(store.observe(key).firstOrNull())
    }

    @Test
    fun `disabled encryption never records a migration`() = runTest {
        val migrationState = SnapshotMigrationStateFake()
        val store = encryptingStore(PersistenceFake(), PlaintextSnapshotCipher, migrationState)
        val key = "key".asKey()

        store.set(key, newSnapshot(key))
        store.observe(key).firstOrNull()

        assertFalse(
            migrationState.isMigrated(),
            "a plaintext store is not migrated, so enabling encryption later must still convert it",
        )
    }

    @Test
    fun `enabling encryption on a plaintext store migrates it`() = runTest {
        val key = "key".asKey()
        val snapshot = newSnapshot(key)
        val persistence = PersistenceFake()
        val migrationState = SnapshotMigrationStateFake()
        encryptingStore(persistence, PlaintextSnapshotCipher, migrationState).set(key, snapshot)

        val store = encryptingStore(persistence, cipher(), migrationState)

        assertEquals(snapshot, store.observe(key).firstOrNull())
        assertFalse(persistence.memory.value.getValue(key.value).trimStart().startsWith('{'))
    }
}

/**
 * Asserts that a transient failure to obtain key material is never reported as "no snapshot".
 *
 * Doing so would let a caller conclude the user is signed out and overwrite a snapshot that is
 * perfectly valid, which is the outcome the transient/permanent split in `KeyEnvelope` exists to
 * prevent. These drive the failure through [EncryptingPersistence] rather than through
 * [EnvelopeDekProvider] directly, which is where the distinction was previously lost.
 */
class EncryptingPersistenceTransientFailureTest {

    private class TransientKeyFailure : Exception("secure store temporarily unavailable")

    private fun failingStore(persistence: Persistence): SnapshotStore =
        SnapshotStoreImpl(
            persistence =
                EncryptingPersistence(
                    delegate = persistence,
                    cipher = AesGcmSnapshotCipher { throw TransientKeyFailure() },
                    migrationState = SnapshotMigrationStateFake(migrated = true),
                ),
            serializer = Json,
        )

    private fun seededPersistence(key: Key): PersistenceFake =
        // Any value will do: the failure happens while acquiring the key, before it is examined.
        PersistenceFake(mapOf(key.value to "irrelevant-ciphertext"))

    @Test
    fun `observe propagates instead of emitting null`() = runTest {
        val key = "key".asKey()
        val store = failingStore(seededPersistence(key))

        assertFailsWith<TransientKeyFailure> { store.observe(key).firstOrNull() }
    }

    @Test
    fun `exists propagates instead of returning false`() = runTest {
        val key = "key".asKey()
        val store = failingStore(seededPersistence(key))

        assertFailsWith<TransientKeyFailure> { store.exists(key) }
    }

    @Test
    fun `getForState propagates instead of returning null`() = runTest {
        val key = "key".asKey()
        val store = failingStore(seededPersistence(key))

        assertFailsWith<TransientKeyFailure> { store.getForState("state") }
    }

    @Test
    fun `contract fails on the caller rather than in the collector it launches`() = runTest {
        val key = "key".asKey()
        val persistence = PersistenceFake()
        // contract() requires an existing snapshot, so write one while a key is still available.
        SnapshotStoreImpl(
                EncryptingPersistence(
                    delegate = persistence,
                    cipher = AesGcmSnapshotCipher { ByteArray(32) { it.toByte() } },
                    migrationState = SnapshotMigrationStateFake(migrated = true),
                ),
                Json,
            )
            .set(key, newSnapshot(key))

        val store = failingStore(persistence) as InternalSnapshotStore

        // The failure has to reach this caller. Raised inside the collector that stateIn launches
        // it
        // would have nowhere to go but the scope's CoroutineExceptionHandler, and an unhandled
        // coroutine exception terminates the application on Android.
        assertFailsWith<TransientKeyFailure> { store.contract(key, backgroundScope) }
        assertTrue(backgroundScope.isActive, "the scope must survive a failed contract")
    }

    @Test
    fun `a failed migration is not recorded and plaintext survives for a later attempt`() =
        runTest {
            val key = "key".asKey()
            val snapshot = newSnapshot(key)
            val plaintext = Json.encodeToString(snapshot)
            val persistence = PersistenceFake(mapOf(key.value to plaintext))
            val migrationState = SnapshotMigrationStateFake()
            val dek = ByteArray(32) { it.toByte() }
            var fail = true
            val store =
                SnapshotStoreImpl(
                    persistence =
                        EncryptingPersistence(
                            delegate = persistence,
                            cipher =
                                AesGcmSnapshotCipher {
                                    if (fail) throw TransientKeyFailure() else dek
                                },
                            migrationState = migrationState,
                        ),
                    serializer = Json,
                )

            assertFailsWith<TransientKeyFailure> { store.observe(key).firstOrNull() }
            assertFalse(migrationState.isMigrated(), "a failed sweep must not be recorded")
            assertEquals(
                plaintext,
                persistence.memory.value.getValue(key.value),
                "the plaintext must be left intact so a later attempt can still convert it",
            )

            // The secure store recovers.
            fail = false
            assertEquals(snapshot, store.observe(key).firstOrNull())
            assertTrue(migrationState.isMigrated())
            assertFalse(persistence.memory.value.getValue(key.value).trimStart().startsWith('{'))
        }
}

class SnapshotMigrationStateFake(migrated: Boolean = false) : SnapshotMigrationState {

    private var migrated = migrated

    override suspend fun isMigrated(): Boolean = migrated

    override suspend fun markMigrated() {
        migrated = true
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
