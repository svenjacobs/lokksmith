package dev.lokksmith.client.snapshot

import dev.lokksmith.client.createTestClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotMigrationTest {

    @Test
    fun `migrate should migrate client from schema version 1 to 2`() = runTest {
        val snapshotStore = SnapshotStoreSpy(
            SnapshotStoreImpl(
                persistence = PersistenceFake(),
                serializer = Json,
            )
        )

        val client = createTestClient(
            snapshotStore = snapshotStore,
        ) {
            copy(schemaVersion = 1)
        }

        snapshotStore.internalSetCalls.clear()

        assertEquals(1, client.snapshots.value.schemaVersion)

        client.migrate()
        runCurrent()

        assertEquals(2, client.snapshots.value.schemaVersion)
        assertEquals(1, snapshotStore.internalSetCalls.size)
    }

    @Test
    fun `migrate should not migrate current schema`() = runTest {
        val snapshotStore = SnapshotStoreSpy(
            SnapshotStoreImpl(
                persistence = PersistenceFake(),
                serializer = Json,
            )
        )

        val client = createTestClient(
            snapshotStore = snapshotStore,
        )

        snapshotStore.internalSetCalls.clear()

        assertEquals(CURRENT_SCHEMA_VERSION, client.snapshots.value.schemaVersion)

        client.migrate()
        runCurrent()

        assertEquals(0, snapshotStore.internalSetCalls.size)
    }
}
