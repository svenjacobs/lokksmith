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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.Key
import dev.lokksmith.client.snapshot.InternalSnapshotStore.Persistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** (De)serializes and persists [Snapshot] instances. */
public interface SnapshotStore {

    public fun observe(key: Key): Flow<Snapshot?>

    public suspend fun getForState(state: String): Snapshot?

    public suspend fun set(key: Key, snapshot: Snapshot): Snapshot

    public suspend fun delete(key: Key): Boolean

    public suspend fun exists(key: Key): Boolean
}

internal interface InternalSnapshotStore : SnapshotStore {

    interface Persistence {

        val data: Flow<Map<String, String>>

        fun observe(key: Key): Flow<String?>

        suspend fun get(key: Key): String?

        suspend fun set(key: Key, snapshot: String)

        suspend fun delete(key: Key)

        suspend fun contains(key: Key): Boolean
    }

    val persistence: Persistence

    val serializer: Json

    /** This Mutex ensures that no concurrent write operations occur here and in [contract]. */
    val writeMutex: Mutex

    suspend fun internalSet(key: Key, snapshot: Snapshot): Snapshot {
        persistence.set(key, serializer.encodeToString(snapshot))
        return snapshot
    }
}

internal class SnapshotStoreImpl(
    override val persistence: Persistence,
    override val serializer: Json,
) : InternalSnapshotStore {

    internal constructor(
        dataStore: DataStore<Preferences>,
        serializer: Json,
    ) : this(persistence = DataStorePersistence(dataStore), serializer = serializer)

    override val writeMutex = Mutex()

    override fun observe(key: Key): Flow<Snapshot?> =
        persistence.observe(key).map { it?.let(serializer::decodeFromString) }

    override suspend fun getForState(state: String): Snapshot? =
        persistence.data
            .first()
            .values
            .mapNotNull { runCatching { serializer.decodeFromString<Snapshot>(it) }.getOrNull() }
            .find { it.ephemeralFlowState?.state == state }

    override suspend fun set(key: Key, snapshot: Snapshot): Snapshot =
        writeMutex.withLock { internalSet(key, snapshot) }

    override suspend fun delete(key: Key): Boolean {
        if (!exists(key)) return false
        persistence.delete(key)
        return true
    }

    override suspend fun exists(key: Key): Boolean = persistence.contains(key)

    private class DataStorePersistence(private val dataStore: DataStore<Preferences>) :
        Persistence {

        override val data: Flow<Map<String, String>>
            get() =
                dataStore.data.map { prefs ->
                    prefs.asMap().map { (key, value) -> key.name to value as String }.toMap()
                }

        override fun observe(key: Key): Flow<String?> =
            dataStore.data.map { prefs -> prefs[key.prefKey] }

        override suspend fun get(key: Key): String? = prefs()[key.prefKey]

        override suspend fun set(key: Key, snapshot: String) {
            dataStore.edit { prefs -> prefs[key.prefKey] = snapshot }
        }

        override suspend fun delete(key: Key) {
            dataStore.edit { prefs -> prefs.remove(key.prefKey) }
        }

        override suspend fun contains(key: Key): Boolean = prefs().contains(key.prefKey)

        private suspend fun prefs() = dataStore.data.first()

        private val Key.prefKey: Preferences.Key<String>
            get() = stringPreferencesKey(value)
    }
}

/**
 * Returns a contract for interaction between [dev.lokksmith.client.Client] and [SnapshotStore].
 *
 * Creates a [StateFlow] internally and suspends until the first value was received. Therefor
 * [contract] must only be called after the initial snapshot has been stored!
 */
internal suspend fun InternalSnapshotStore.contract(
    key: Key,
    coroutineScope: CoroutineScope,
): InternalClient.SnapshotContract {
    val snapshots = observe(key).filterNotNull()

    // A StateFlow already behaves like distinctUntilChanged() is applied, so we don't need to
    // explicitly use it here. We don't want this Flow to emit values if the underlying snapshot
    // changes but remains structurally equal.
    val snapshotsStateFlow = snapshots.stateIn(coroutineScope)

    return object : InternalClient.SnapshotContract {

        override val snapshots: StateFlow<Snapshot> = snapshotsStateFlow

        override suspend fun updateSnapshot(body: Snapshot.() -> Snapshot) =
            writeMutex.withLock {
                // We don't use `snapshotStateFlow.value` of the StateFlow at this point to fetch
                // the current value because since it collects in a different coroutine, swift
                // consecutive executions of `updateSnapshot` might see stale data.
                internalSet(key, snapshots.first().body())
            }
    }
}
