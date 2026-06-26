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
package dev.lokksmith

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * A minimal [DataStore] of [Preferences] backed by the browser's `localStorage`.
 *
 * The snapshot store only ever persists [String] values (see
 * `dev.lokksmith.client.snapshot.SnapshotStoreImpl`), so the whole preference map is serialized as
 * a single JSON `Map<String, String>` entry under [name].
 *
 * **This is a temporary implementation** that should be replaced by the official AndroidX DataStore
 * web storage (`WebLocalStorage`) once `androidx.datastore` 1.3.0 reaches a stable release. See
 * [createDataStore] for the replacement snippet.
 *
 * Limitations compared to the official implementation:
 * - No cross-tab synchronization (changes made in another tab are not observed by [data]).
 * - Only [String] preference values are supported.
 *
 * Security note: `localStorage` is not encrypted and is readable by any script on the same origin,
 * so persisted data (including tokens) is exposed to cross-site scripting (XSS) attacks.
 */
internal class LocalStoragePreferenceDataStore(
    private val name: String,
    private val json: Json = Json,
) : DataStore<Preferences> {

    private val writeMutex = Mutex()
    private val state = MutableStateFlow(readFromStorage())

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = writeMutex.withLock {
        val updated = transform(state.value)
        writeToStorage(updated)
        state.value = updated
        updated
    }

    private fun readFromStorage(): Preferences {
        val raw = localStorage.getItem(name) ?: return mutablePreferencesOf()
        val map =
            runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        return mutablePreferencesOf().apply {
            map.forEach { (key, value) -> set(stringPreferencesKey(key), value) }
        }
    }

    private fun writeToStorage(preferences: Preferences) {
        val map =
            preferences.asMap().entries.associate { (key, value) ->
                // Only String values are supported (see KDoc); cast fails fast otherwise.
                key.name to (value as String)
            }
        localStorage.setItem(name, json.encodeToString(map))
    }
}
