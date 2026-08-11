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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lokksmith.PlatformContext
import dev.lokksmith.client.Client
import dev.lokksmith.client.Key
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.crypto.KeyEnvelope
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/**
 * End-to-end test of the encrypted persistence stack assembled exactly as
 * [dev.lokksmith.ContainerImpl] does it (real DataStores, real [KeyEnvelope], real files), driven
 * across a simulated process restart.
 *
 * A "restart" is modelled by tearing down a [Generation]'s DataStore scope (releasing the file
 * locks) and building a fresh [Generation] over the same directory. DataStore forbids two live
 * instances over the same file within a process, so [Generation.shutdown] must complete before the
 * next generation is created.
 */
class EncryptedPersistenceRestartJvmTest {

    @Test
    fun `snapshot persists across a simulated restart`() = storeTest { dir ->
        val key = "key".asKey()
        val snapshot = newSnapshot(key)

        val first = generation(dir)
        first.store.set(key, snapshot)
        first.shutdown()

        val second = generation(dir)
        try {
            assertEquals(snapshot, second.store.observe(key).first())
        } finally {
            second.shutdown()
        }
    }

    @Test
    fun `on-disk value is ciphertext, not plaintext`() = storeTest { dir ->
        val key = "key".asKey()
        val snapshot = newSnapshot(key)

        val gen = generation(dir)
        try {
            gen.store.set(key, snapshot)

            val stored = gen.snapshotDs.data.first()[stringPreferencesKey(key.value)]
            assertNotNull(stored, "snapshot should be persisted")
            assertNotEquals(Json.encodeToString(snapshot), stored)
            assertFalse("issuer" in stored, "issuer leaked into stored value")
            assertFalse("tokenEndpoint" in stored, "endpoint leaked into stored value")
        } finally {
            gen.shutdown()
        }
    }

    @Test
    fun `legacy plaintext migrates transparently across restart`() = storeTest { dir ->
        val key = "key".asKey()
        val snapshot = newSnapshot(key)

        // Seed the snapshot file as an older, unencrypted version would have left it.
        seedRawSnapshot(dir, key, Json.encodeToString(snapshot))

        val first = generation(dir)
        // Legacy plaintext is read via fallback, then re-encrypted on the next write.
        assertEquals(snapshot, first.store.observe(key).first())
        first.store.set(key, first.store.observe(key).first()!!)
        first.shutdown()

        val second = generation(dir)
        try {
            assertEquals(snapshot, second.store.observe(key).first())
            val stored = second.snapshotDs.data.first()[stringPreferencesKey(key.value)]
            assertNotNull(stored)
            assertFalse(
                stored.trimStart().startsWith('{'),
                "value should have been re-encrypted, not left as plaintext JSON",
            )
        } finally {
            second.shutdown()
        }
    }

    @Test
    fun `lost KEK across restart makes the snapshot absent without crashing`() = storeTest { dir ->
        val key = "key".asKey()

        val first = generation(dir)
        first.store.set(key, newSnapshot(key))
        first.shutdown()

        // Simulate the platform secure store being cleared between runs.
        assertTrue(dir.resolve("$BASE.kek").delete(), "KEK file should exist and be deletable")

        val second = generation(dir)
        try {
            // The old snapshot can no longer be decrypted; it is treated as absent, not fatal.
            assertNull(second.store.observe(key).first())
            assertFalse(second.store.exists(key))

            // The orphaned, undecryptable row is still physically removable.
            assertTrue(second.store.delete(key), "an unreadable entry should be deletable")
            assertNull(
                second.snapshotDs.data.first()[stringPreferencesKey(key.value)],
                "the physical row should be gone after delete",
            )

            // The store remains usable: a new snapshot round-trips under the regenerated key.
            val fresh = newSnapshot(key)
            second.store.set(key, fresh)
            assertEquals(fresh, second.store.observe(key).first())
        } finally {
            second.shutdown()
        }
    }

    /** One instantiation of the persistence stack over [dir], mirroring `ContainerImpl`. */
    private class Generation(
        val store: SnapshotStore,
        val snapshotDs: DataStore<Preferences>,
        private val job: Job,
    ) {
        suspend fun shutdown() = job.cancelAndJoin()
    }

    private fun generation(dir: File): Generation {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val snapshotDs = dataStore(scope, dir, "$BASE.preferences_pb")
        val keyDs = dataStore(scope, dir, "$BASE.key.preferences_pb")
        val provider =
            EnvelopeDekProvider(
                envelope = KeyEnvelope(PlatformContext(dataDirectory = dir), alias = BASE),
                wrappedStore = keyDs,
            )
        val store =
            SnapshotStoreImpl(
                persistence =
                    EncryptingPersistence(
                        delegate = DataStorePersistence(snapshotDs),
                        cipher = AesGcmSnapshotCipher { provider.getOrCreateDek() },
                    ),
                serializer = Json,
            )
        return Generation(store, snapshotDs, job)
    }

    /** Writes [value] straight into the snapshot file, bypassing encryption (legacy state). */
    private suspend fun seedRawSnapshot(dir: File, key: Key, value: String) {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            dataStore(scope, dir, "$BASE.preferences_pb").edit {
                it[stringPreferencesKey(key.value)] = value
            }
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun dataStore(scope: CoroutineScope, dir: File, fileName: String) =
        PreferenceDataStoreFactory.createWithPath(scope = scope) {
            dir.resolve(fileName).absolutePath.toPath()
        }

    private fun storeTest(block: suspend (dir: File) -> Unit) = runTest {
        val dir = createTempDirectory("lokksmith-enc-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun newSnapshot(key: Key, state: String? = null): Snapshot =
        Snapshot(
            key = key,
            id = key.value.asId(),
            metadata =
                Client.Metadata(
                    issuer = "issuer",
                    authorizationEndpoint = "authorizationEndpoint",
                    tokenEndpoint = "tokenEndpoint",
                ),
            ephemeralFlowState =
                state?.let {
                    Snapshot.EphemeralAuthorizationCodeFlowState(
                        state = it,
                        redirectUri = "redirectUri",
                        codeVerifier = null,
                        responseUri = null,
                    )
                },
        )

    private companion object {
        const val BASE = "lokksmith_clients"
    }
}
