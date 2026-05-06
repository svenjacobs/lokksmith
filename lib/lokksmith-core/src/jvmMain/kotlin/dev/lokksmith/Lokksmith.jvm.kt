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
 * @param options Cross-platform configuration options for the [Lokksmith] instance.
 * @param desktop JVM/Desktop-specific configuration controlling the loopback redirect server and
 *   browser launching. See [DesktopOptions].
 */
public fun createLokksmith(
    dataDirectory: DataDirectory,
    options: Options = Options(),
    desktop: DesktopOptions = DesktopOptions(),
): Lokksmith {
    val dir =
        when (dataDirectory) {
            is DataDirectory.Default -> appDataDir(dataDirectory.appName)
            is DataDirectory.Custom -> dataDirectory.path
        }
    val platformContext = PlatformContext(dataDirectory = dir.resolve(LOKKSMITH_DIR))
    val baseContainer = ContainerImpl(platformContext = platformContext, options = options)
    return Lokksmith(
        container = JvmContainerImpl(delegate = baseContainer, desktopOptions = desktop)
    )
}

private const val LOKKSMITH_DIR = "lokksmith"

internal actual val platformHttpClientEngine: HttpClientEngine
    get() = OkHttp.create()
