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

/**
 * (De)serializes and persists [Snapshot] instances.
 *
 * When snapshots are encrypted at rest, reads distinguish two kinds of failure. A snapshot that
 * cannot be decrypted, because the platform key was lost or the stored value was tampered with, is
 * reported as absent: the client is re-created and the user re-authenticates. A failure to reach
 * the platform secure store at all is potentially transient — the Android Keystore during
 * direct-boot, the iOS Keychain before first unlock — and is thrown instead of being reported as
 * absent, because concluding "not signed in" would lead to overwriting a snapshot that is still
 * valid. Treat such an error as "unknown, try again".
 */
public interface SnapshotStore {

    /** @throws Exception if the platform secure store could not be reached; see [SnapshotStore]. */
    public fun observe(key: Key): Flow<Snapshot?>

    /** @throws Exception if the platform secure store could not be reached; see [SnapshotStore]. */
    public suspend fun getForState(state: String): Snapshot?

    public suspend fun set(key: Key, snapshot: Snapshot): Snapshot

    public suspend fun delete(key: Key): Boolean

    /** @throws Exception if the platform secure store could not be reached; see [SnapshotStore]. */
    public suspend fun exists(key: Key): Boolean
}

internal interface InternalSnapshotStore : SnapshotStore {

    interface Persistence {

        val data: Flow<Map<String, String>>

        fun observe(key: Key): Flow<String?>

        suspend fun get(key: Key): String?

        suspend fun set(key: Key, snapshot: String)

        /** Removes the physical entry for [key], returning whether one existed. */
        suspend fun delete(key: Key): Boolean

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

    override val writeMutex = Mutex()

    override fun observe(key: Key): Flow<Snapshot?> =
        persistence.observe(key).map { it?.let(serializer::decodeFromString) }

    override suspend fun getForState(state: String): Snapshot? =
        persistence.data
            .first()
            .values
            .mapNotNull { runCatching { serializer.decodeFromString<Snapshot>(it) }.getOrNull() }
            .find { it.ephemeralFlowState?.state == state }

    override suspend fun set(key: Key, snapshot: Snapshot): Snapshot = writeMutex.withLock {
        internalSet(key, snapshot)
    }

    override suspend fun delete(key: Key): Boolean = persistence.delete(key)

    override suspend fun exists(key: Key): Boolean = persistence.contains(key)
}

/**
 * [Persistence] backed by AndroidX [DataStore], keeping each snapshot as a string under
 * [Key.value].
 */
internal class DataStorePersistence(private val dataStore: DataStore<Preferences>) : Persistence {

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

    override suspend fun delete(key: Key): Boolean {
        var existed = false
        dataStore.edit { prefs ->
            existed = prefs.contains(key.prefKey)
            if (existed) prefs.remove(key.prefKey)
        }
        return existed
    }

    override suspend fun contains(key: Key): Boolean = prefs().contains(key.prefKey)

    private suspend fun prefs() = dataStore.data.first()

    private val Key.prefKey: Preferences.Key<String>
        get() = stringPreferencesKey(value)
}

/**
 * Returns a contract for interaction between [dev.lokksmith.client.Client] and [SnapshotStore].
 *
 * Creates a [StateFlow] internally and suspends until the first value was received. Therefor
 * [contract] must only be called after the initial snapshot has been stored!
 *
 * @throws Exception if the platform secure store could not be reached; see [SnapshotStore].
 */
internal suspend fun InternalSnapshotStore.contract(
    key: Key,
    coroutineScope: CoroutineScope,
): InternalClient.SnapshotContract {
    // Reads the snapshot once on the caller's coroutine, before the long-lived collector below is
    // launched. A read can fail because the platform secure store is transiently unavailable, and
    // that has to surface as a failure of this function. Left to the collector inside stateIn it
    // would instead fail that coroutine, which reports to the CoroutineExceptionHandler of
    // [coroutineScope] rather than to any caller.
    persistence.get(key)

    val snapshots = observe(key).filterNotNull()

    // A StateFlow already behaves like distinctUntilChanged() is applied, so we don't need to
    // explicitly use it here. We don't want this Flow to emit values if the underlying snapshot
    // changes but remains structurally equal.
    val snapshotsStateFlow = snapshots.stateIn(coroutineScope)

    return object : InternalClient.SnapshotContract {

        override val snapshots: StateFlow<Snapshot> = snapshotsStateFlow

        override suspend fun updateSnapshot(body: Snapshot.() -> Snapshot) = writeMutex.withLock {
            // We don't use `snapshotStateFlow.value` of the StateFlow at this point to fetch
            // the current value because since it collects in a different coroutine, swift
            // consecutive executions of `updateSnapshot` might see stale data.
            internalSet(key, snapshots.first().body())
        }
    }
}
