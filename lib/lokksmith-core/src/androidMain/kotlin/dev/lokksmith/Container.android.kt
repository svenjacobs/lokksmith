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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal actual class PlatformContext(val context: Context)

internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> =
    createDataStore(fileName) { name ->
        platformContext.context.filesDir.resolve(name).absolutePath
    }

internal actual val platformUserAgentSuffix = "Android"
