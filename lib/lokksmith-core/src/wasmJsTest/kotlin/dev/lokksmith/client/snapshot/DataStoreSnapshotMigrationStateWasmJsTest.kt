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

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.lokksmith.LocalStoragePreferenceDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.browser.localStorage
import kotlinx.coroutines.test.runTest

/**
 * The migration marker over the real Web store.
 *
 * [LocalStoragePreferenceDataStore] holds the preference map as a JSON `Map<String, String>` and
 * supports string values only, so a marker persisted as a boolean fails to write at all. That
 * failure surfaces from `EncryptingPersistence.ensureMigrated`, which every read and write goes
 * through, so on Web it would break the snapshot store outright rather than only the marker.
 */
class DataStoreSnapshotMigrationStateWasmJsTest {

    private val name = "lokksmith_migration_test.key.preferences_pb"

    @BeforeTest
    @AfterTest
    fun clearStorage() {
        localStorage.removeItem(name)
    }

    @Test
    fun defaultsToNotMigrated() = runTest {
        val state = DataStoreSnapshotMigrationState(LocalStoragePreferenceDataStore(name))

        assertFalse(state.isMigrated())
    }

    @Test
    fun marksMigrated() = runTest {
        val state = DataStoreSnapshotMigrationState(LocalStoragePreferenceDataStore(name))

        state.markMigrated()

        assertTrue(state.isMigrated())
    }

    @Test
    fun markerSurvivesAFreshStoreOverTheSameStorage() = runTest {
        DataStoreSnapshotMigrationState(LocalStoragePreferenceDataStore(name)).markMigrated()

        // What a page reload does: a new store instance reading the same localStorage entry. A
        // marker that does not survive this would re-run the sweep on every load.
        val reopened = DataStoreSnapshotMigrationState(LocalStoragePreferenceDataStore(name))

        assertTrue(reopened.isMigrated())
    }

    @Test
    fun clearsMigrated() = runTest {
        val dataStore = LocalStoragePreferenceDataStore(name)
        val state = DataStoreSnapshotMigrationState(dataStore)
        state.markMigrated()

        state.clearMigrated()

        assertFalse(state.isMigrated())
        assertFalse(
            DataStoreSnapshotMigrationState(LocalStoragePreferenceDataStore(name)).isMigrated()
        )
    }

    @Test
    fun rejectsANonStringPreferenceWithADiagnosticNamingTheKey() = runTest {
        val dataStore = LocalStoragePreferenceDataStore(name)

        val error =
            assertFailsWith<IllegalArgumentException> {
                dataStore.edit { it[booleanPreferencesKey("some.boolean")] = true }
            }

        // The message has to identify the offending preference: this is exactly the shape of bug
        // the marker had, and a bare cast failure gives no clue which key caused it.
        assertTrue("some.boolean" in error.message.orEmpty(), "message: ${error.message}")
    }
}
