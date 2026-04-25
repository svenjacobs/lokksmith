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

import dev.lokksmith.Lokksmith.Options
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Creates a new [Lokksmith] instance for JVM/Desktop.
 *
 * @param dataDirectory Specifies where Lokksmith stores its data.
 * @param options Configuration options for the [Lokksmith] instance.
 */
public fun createLokksmith(dataDirectory: DataDirectory, options: Options = Options()): Lokksmith {
    val dir =
        when (dataDirectory) {
            is DataDirectory.Default -> appDataDir(dataDirectory.value)
            is DataDirectory.Custom -> dataDirectory.path
        }
    return Lokksmith(
        platformContext = PlatformContext(dataDirectory = dir.resolve(LOKKSMITH_DIR)),
        options = options,
    )
}

private const val LOKKSMITH_DIR = ".lokksmith"

internal actual val platformHttpClientEngine: HttpClientEngine
    get() = OkHttp.create()
