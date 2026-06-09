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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class LocalStoragePreferenceDataStoreTest {

    private val name = "lokksmith_test.preferences_pb"
    private val key = stringPreferencesKey("key")

    @BeforeTest
    @AfterTest
    fun clearStorage() {
        localStorage.removeItem(name)
    }

    @Test
    fun returnsEmptyWhenNothingPersisted() = runTest {
        val store = LocalStoragePreferenceDataStore(name)

        assertNull(store.data.first()[key])
    }

    @Test
    fun persistsAndReadsBackAcrossInstances() = runTest {
        LocalStoragePreferenceDataStore(name).edit { it[key] = "value" }

        // A fresh instance must read the value back from localStorage.
        val reopened = LocalStoragePreferenceDataStore(name)

        assertEquals("value", reopened.data.first()[key])
    }

    @Test
    fun updatesAndRemovesValues() = runTest {
        val store = LocalStoragePreferenceDataStore(name)

        store.edit { it[key] = "first" }
        store.edit { it[key] = "second" }
        assertEquals("second", store.data.first()[key])

        store.edit { it.remove(key) }
        assertNull(store.data.first()[key])
    }
}
