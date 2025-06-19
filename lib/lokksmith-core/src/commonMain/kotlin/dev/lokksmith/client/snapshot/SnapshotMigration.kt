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

import dev.lokksmith.client.InternalClient

internal object SnapshotMigration {

    suspend fun migrate(client: InternalClient): InternalClient {
        if (client.snapshots.value.schemaVersion == CURRENT_SCHEMA_VERSION) return client

        client.updateSnapshot { migrateSnapshot(this) }

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

internal suspend fun InternalClient.migrate(): InternalClient = SnapshotMigration.migrate(this)
