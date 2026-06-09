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

internal actual object PlatformContext

/**
 * Creates the [DataStore] backing the snapshot store on the Web target.
 *
 * Persistence is backed by the browser's `localStorage` via [LocalStoragePreferenceDataStore].
 *
 * **Migration note:** This custom implementation is a temporary stand-in. The official AndroidX
 * DataStore web storage (`WebLocalStorage`) is only available since `androidx.datastore`
 * 1.3.0-alpha07 and the project currently pins the stable 1.2.1 release. Once 1.3.0 reaches a
 * stable release, this actual should be replaced with the official API and
 * [LocalStoragePreferenceDataStore] deleted:
 * ```kotlin
 * internal actual fun createDataStore(
 *     fileName: String,
 *     platformContext: PlatformContext,
 * ): DataStore<Preferences> =
 *     PreferenceDataStoreFactory.create(
 *         storage = WebLocalStorage(serializer = PreferencesSerializer, name = fileName)
 *     )
 * ```
 *
 * (`WebLocalStorage` / `WebSessionStorage` live in `androidx.datastore.core`;
 * `PreferencesSerializer` in `androidx.datastore.preferences.core`.)
 */
internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> = LocalStoragePreferenceDataStore(name = fileName)

internal actual val platformUserAgentSuffix = "Web"
