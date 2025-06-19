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
package dev.lokksmith.client.snapshot

import dev.lokksmith.client.createTestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotMigrationTest {

    @Test
    fun `migrate should migrate client from schema version 1 to 2`() = runTest {
        val snapshotStore =
            SnapshotStoreSpy(SnapshotStoreImpl(persistence = PersistenceFake(), serializer = Json))

        val client = createTestClient(snapshotStore = snapshotStore) { copy(schemaVersion = 1) }

        snapshotStore.internalSetCalls.clear()

        assertEquals(1, client.snapshots.value.schemaVersion)

        client.migrate()
        runCurrent()

        assertEquals(2, client.snapshots.value.schemaVersion)
        assertEquals(1, snapshotStore.internalSetCalls.size)
    }

    @Test
    fun `migrate should not migrate current schema`() = runTest {
        val snapshotStore =
            SnapshotStoreSpy(SnapshotStoreImpl(persistence = PersistenceFake(), serializer = Json))

        val client = createTestClient(snapshotStore = snapshotStore)

        snapshotStore.internalSetCalls.clear()

        assertEquals(CURRENT_SCHEMA_VERSION, client.snapshots.value.schemaVersion)

        client.migrate()
        runCurrent()

        assertEquals(0, snapshotStore.internalSetCalls.size)
    }
}
