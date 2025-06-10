package dev.lokksmith.client.snapshot

import dev.lokksmith.client.InternalClient

internal object SnapshotMigration {

    suspend fun migrate(client: InternalClient): InternalClient {
        if (client.snapshots.value.schemaVersion == CURRENT_SCHEMA_VERSION) return client

        client.updateSnapshot {
            migrateSnapshot(this)
        }

        return client
    }

    private fun migrateSnapshot(snapshot: Snapshot): Snapshot {
        if (snapshot.schemaVersion == 1) {
            // No migration required except updating the schema version
            return snapshot.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
        }
        return snapshot
    }
}

internal suspend fun InternalClient.migrate(): InternalClient =
    SnapshotMigration.migrate(this)
