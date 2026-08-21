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

    @Test
    fun `disabled encryption clears a marker left by an earlier encrypted run`() = runTest {
        val key = "key".asKey()
        val persistence = PersistenceFake()
        val migrationState = SnapshotMigrationStateFake()
        encryptingStore(persistence, cipher(), migrationState).set(key, newSnapshot(key))
        assertTrue(migrationState.isMigrated(), "precondition: the encrypted run records a marker")

        // Merely accessing the store in plaintext mode is enough: from here on anything written is
        // plaintext, so the store is no longer converted and the marker must not claim otherwise.
        encryptingStore(persistence, PlaintextSnapshotCipher, migrationState)
            .observe(key)
            .firstOrNull()

        assertFalse(migrationState.isMigrated())
    }

    @Test
    fun `re-enabling encryption migrates plaintext written while it was off`() = runTest {
        val key = "key".asKey()
        val snapshot = newSnapshot(key)
        val persistence = PersistenceFake()
        val migrationState = SnapshotMigrationStateFake()

        // A release with encryption on, which records the marker.
        encryptingStore(persistence, cipher(), migrationState).set(key, snapshot)

        // A release with it off. The ciphertext is unreadable in plaintext mode, so the client is
        // re-created and the replacement snapshot is written as plaintext.
        encryptingStore(persistence, PlaintextSnapshotCipher, migrationState).set(key, snapshot)
        assertEquals(Json.encodeToString(snapshot), persistence.memory.value.getValue(key.value))

        // On again. Without clearing the marker in plaintext mode this skips the sweep, and the
        // plaintext is then neither readable nor ever encrypted: it stays in the clear on disk
        // until something happens to write that client again, which may be never.
        val reEnabled = encryptingStore(persistence, cipher(), migrationState)

        assertEquals(snapshot, reEnabled.observe(key).firstOrNull())
        assertFalse(persistence.memory.value.getValue(key.value).trimStart().startsWith('{'))
        assertTrue(migrationState.isMigrated())
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

/**
 * Covers the one-shot conversion sweep itself, rather than the reads and writes around it.
 *
 * The sweep is the only thing that ever turns a plaintext entry into ciphertext, so every entry
 * point that can be the *first* one to touch a legacy store has to trigger it, it has to run
 * exactly once no matter how many callers arrive at the same time, and an attempt that fails part
 * of the way through has to be resumable.
 */
class SnapshotMigrationSweepTest {

    private val dek = ByteArray(32) { it.toByte() }

    private fun store(
        persistence: Persistence,
        cipher: SnapshotCipher = AesGcmSnapshotCipher { dek },
        migrationState: SnapshotMigrationState = SnapshotMigrationStateFake(),
    ): SnapshotStore =
        SnapshotStoreImpl(
            persistence = EncryptingPersistence(persistence, cipher, migrationState),
            serializer = Json,
        )

    @Test
    fun `a write is a first access and sweeps entries it does not touch`() = runTest {
        val legacy = "legacy".asKey()
        val written = "written".asKey()
        val plaintext = Json.encodeToString(newSnapshot(legacy))
        val persistence = PersistenceFake(mapOf(legacy.value to plaintext))
        val migrationState = SnapshotMigrationStateFake()
        val store = store(persistence, migrationState = migrationState)

        // The first thing this installation does is create a *different* client. Nothing reads the
        // legacy entry, so only the sweep can be what converts it.
        store.set(written, newSnapshot(written))

        assertFalse(
            persistence.memory.value.getValue(legacy.value).trimStart().startsWith('{'),
            "a write must convert the rest of the store, not just the entry it writes",
        )
        assertTrue(migrationState.isMigrated())
        assertEquals(newSnapshot(legacy), store.observe(legacy).firstOrNull())
    }

    @Test
    fun `getForState is a first access and sweeps`() = runTest {
        val key = "key".asKey()
        val state = "Ly5GJLkj"
        val snapshot = newSnapshot(key, state)
        val persistence = PersistenceFake(mapOf(key.value to Json.encodeToString(snapshot)))
        val store = store(persistence)

        // Resuming an authorization flow reads through data rather than observe, and on a legacy
        // store it is the very first access. The in-flight state it looks for is exactly what the
        // sweep protects: state, nonce and code verifier are all in the snapshot.
        assertEquals(snapshot, store.getForState(state))

        assertFalse(
            persistence.memory.value.getValue(key.value).trimStart().startsWith('{'),
            "plaintext must not survive a lookup by state either",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `concurrent first accesses sweep once`() = runTest {
        val first = "first".asKey()
        val second = "second".asKey()
        val persistence =
            PersistenceFake(
                mapOf(
                    first.value to Json.encodeToString(newSnapshot(first)),
                    second.value to Json.encodeToString(newSnapshot(second)),
                )
            )
        val migrationState = CountingMigrationState()
        // Holds the first sweep open, so the second caller is guaranteed to arrive while it runs.
        val keyMaterial = CompletableDeferred<Unit>()
        var dekRequests = 0
        val store =
            store(
                persistence,
                AesGcmSnapshotCipher {
                    dekRequests++
                    keyMaterial.await()
                    dek
                },
                migrationState,
            )

        val a = launch { store.observe(first).firstOrNull() }
        runCurrent()
        val b = launch { store.observe(second).firstOrNull() }
        runCurrent()

        keyMaterial.complete(Unit)
        a.join()
        b.join()

        assertEquals(1, dekRequests, "the DEK should be resolved once, not once per caller")
        assertEquals(1, migrationState.markCount, "the sweep must not run twice")
        assertEquals(newSnapshot(first), store.observe(first).firstOrNull())
        assertEquals(newSnapshot(second), store.observe(second).firstOrNull())
    }

    @Test
    fun `a sweep that fails midway is resumed by the next access`() = runTest {
        val converted = "aaa".asKey()
        val failing = "zzz".asKey()
        val persistence =
            PersistenceFake(
                mapOf(
                    converted.value to Json.encodeToString(newSnapshot(converted)),
                    failing.value to Json.encodeToString(newSnapshot(failing)),
                )
            )
        val migrationState = SnapshotMigrationStateFake()
        var failOn: String? = failing.value
        val store =
            store(
                persistence,
                // Fails while encrypting one specific entry, after another has already been
                // written back: the sweep is not atomic, so the store is left half converted.
                FailingForEntryCipher(AesGcmSnapshotCipher { dek }, failOn = { failOn }),
                migrationState,
            )

        assertFailsWith<IllegalStateException> { store.observe(converted).firstOrNull() }

        assertFalse(
            persistence.memory.value.getValue(converted.value).trimStart().startsWith('{'),
            "an entry converted before the failure stays converted",
        )
        assertTrue(
            persistence.memory.value.getValue(failing.value).trimStart().startsWith('{'),
            "the entry that failed is left as plaintext for a later attempt",
        )
        assertFalse(migrationState.isMigrated(), "a partial sweep must not be recorded")

        failOn = null

        // The retry has to cope with the half-converted store it inherited: skip what is already
        // ciphertext, convert the remainder.
        assertEquals(newSnapshot(converted), store.observe(converted).firstOrNull())
        assertEquals(newSnapshot(failing), store.observe(failing).firstOrNull())
        assertTrue(migrationState.isMigrated())
        assertTrue(
            persistence.memory.value.values.none { it.trimStart().startsWith('{') },
            "no plaintext should be left after the retry",
        )
    }

    private class CountingMigrationState : SnapshotMigrationState {

        var markCount = 0
            private set

        private var migrated = false

        override suspend fun isMigrated(): Boolean = migrated

        override suspend fun markMigrated() {
            markCount++
            migrated = true
        }

        override suspend fun clearMigrated() {
            migrated = false
        }
    }

    /** Delegates to [delegate], but fails to encrypt the entry currently named by [failOn]. */
    private class FailingForEntryCipher(
        private val delegate: SnapshotCipher,
        private val failOn: () -> String?,
    ) : SnapshotCipher {

        override val isEncrypting: Boolean = true

        override suspend fun encrypt(entryId: String, plaintext: String): String {
            check(entryId != failOn()) { "cannot encrypt $entryId" }
            return delegate.encrypt(entryId, plaintext)
        }

        override suspend fun decrypt(entryId: String, value: String): String =
            delegate.decrypt(entryId, value)
    }
}

class SnapshotMigrationStateFake(migrated: Boolean = false) : SnapshotMigrationState {

    private var migrated = migrated

    override suspend fun isMigrated(): Boolean = migrated

    override suspend fun markMigrated() {
        migrated = true
    }

    override suspend fun clearMigrated() {
        migrated = false
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
