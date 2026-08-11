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
import dev.lokksmith.crypto.KeyEnvelope
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

/**
 * Exercises [EnvelopeDekProvider] against the real JVM (file-based) [KeyEnvelope] and a real
 * DataStore, rather than the in-memory fakes used elsewhere. This is the only test that covers the
 * envelope wrap/unwrap and DEK persistence path.
 */
class EnvelopeDekProviderJvmTest {

    // Literal storage key mirrored from EnvelopeDekProvider (private there). This is intentionally
    // brittle: it pins the on-disk contract.
    private val wrappedDekKey = stringPreferencesKey("lokksmith.snapshot.wrappedDek")

    @Test
    fun `generates a 32-byte DEK and persists only the wrapped copy`() =
        withStore { dir, keyStore ->
            val dek = provider(dir, keyStore).getOrCreateDek()

            assertEquals(32, dek.size, "DEK should be AES-256 (32 bytes)")
            val stored = keyStore.data.first()[wrappedDekKey]
            assertNotNull(stored, "wrapped DEK should be persisted")
            // What is stored is the wrapped DEK, never the raw DEK itself.
            assertNotEquals(Base64.encode(dek), stored, "raw DEK must not be persisted")
        }

    @Test
    fun `returns the cached DEK within a provider`() = withStore { dir, keyStore ->
        val provider = provider(dir, keyStore)

        assertContentEquals(provider.getOrCreateDek(), provider.getOrCreateDek())
    }

    @Test
    fun `a fresh provider over the same store unwraps the same DEK`() = withStore { dir, keyStore ->
        val dek1 = provider(dir, keyStore).getOrCreateDek()
        // New provider + new envelope over the same directory (same KEK file) and the same store.
        val dek2 = provider(dir, keyStore).getOrCreateDek()

        assertContentEquals(dek1, dek2, "the persisted, wrapped DEK should be unwrapped unchanged")
    }

    @Test
    fun `lost KEK regenerates a new DEK`() = withStore { dir, keyStore ->
        val dek1 = provider(dir, keyStore).getOrCreateDek()

        // Simulate KEK loss (e.g. secure store cleared). The next envelope generates a fresh KEK,
        // so the stored wrapped DEK can no longer be unwrapped.
        assertTrue(dir.resolve("$ALIAS.kek").delete(), "KEK file should exist and be deletable")
        val dek2 = provider(dir, keyStore).getOrCreateDek()

        // Any unwrap failure regenerates and discards the previous DEK. A transient failure would
        // do the same, losing all snapshots.
        assertFalse(dek1.contentEquals(dek2), "a lost KEK should force a new DEK")
        assertNotEquals(
            Base64.encode(dek1),
            keyStore.data.first()[wrappedDekKey],
            "the wrapped DEK should have been rewritten",
        )
    }

    @Test
    fun `corrupt wrapped DEK regenerates`() = withStore { dir, keyStore ->
        keyStore.edit { it[wrappedDekKey] = "!!not-valid-base64!!" }

        val dek = provider(dir, keyStore).getOrCreateDek()

        assertEquals(32, dek.size)
        assertNotEquals(
            "!!not-valid-base64!!",
            keyStore.data.first()[wrappedDekKey],
            "the corrupt value should have been replaced with a valid wrapped DEK",
        )
    }

    @Test
    fun `transient KEK read error propagates without discarding the wrapped DEK`() =
        withStore { dir, keyStore ->
            provider(dir, keyStore).getOrCreateDek()
            val wrappedBefore = keyStore.data.first()[wrappedDekKey]
            assertNotNull(wrappedBefore)

            // KEK path exists but is unreadable (a directory), which is a read error, not absence.
            val kekFile = dir.resolve("$ALIAS.kek")
            assertTrue(kekFile.delete())
            assertTrue(kekFile.mkdir())

            assertFailsWith<Exception> { provider(dir, keyStore).getOrCreateDek() }
            assertEquals(
                wrappedBefore,
                keyStore.data.first()[wrappedDekKey],
                "a transient failure must not overwrite the wrapped DEK",
            )
        }

    private fun provider(dir: File, keyStore: DataStore<Preferences>) =
        EnvelopeDekProvider(
            envelope = KeyEnvelope(PlatformContext(dataDirectory = dir), alias = ALIAS),
            wrappedStore = keyStore,
        )

    /**
     * Runs [block] with a temp directory and a real DataStore for the wrapped DEK, tearing both
     * down afterwards. Providers can share the single live [DataStore] instance; only cross-process
     * restart needs a fresh one (see [EncryptedPersistenceRestartJvmTest]).
     */
    private fun withStore(block: suspend (dir: File, keyStore: DataStore<Preferences>) -> Unit) =
        runTest {
            val dir = createTempDirectory("lokksmith-dek-").toFile()
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.IO + job)
            try {
                val keyStore =
                    PreferenceDataStoreFactory.createWithPath(scope = scope) {
                        dir.resolve("$ALIAS.key.preferences_pb").absolutePath.toPath()
                    }
                block(dir, keyStore)
            } finally {
                job.cancelAndJoin()
                dir.deleteRecursively()
            }
        }

    private companion object {
        const val ALIAS = "test"
    }
}
