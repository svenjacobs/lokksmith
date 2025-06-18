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
package dev.lokksmith

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal actual object PlatformContext

internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> = createDataStore(fileName) { name ->
    // https://developer.android.com/kotlin/multiplatform/datastore#ios
    TODO()
}

internal actual val platformUserAgentSuffix = "iOS"
