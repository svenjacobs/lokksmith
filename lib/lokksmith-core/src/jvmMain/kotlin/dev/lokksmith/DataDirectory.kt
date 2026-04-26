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

import java.io.File

/** Specifies where Lokksmith stores its data on JVM/Desktop. */
public sealed interface DataDirectory {
    /**
     * Derives the data directory from the application name using platform-specific conventions.
     * - **Linux**: `$XDG_DATA_HOME/<appName>` (defaults to `~/.local/share/<appName>`)
     * - **macOS**: `~/Library/Application Support/<appName>`
     * - **Windows**: `%APPDATA%/<appName>`
     *
     * [appName] must be a single directory segment: blank values, path separators (`/`, `\`), `..`,
     * and NUL characters are rejected to prevent path traversal (CWE-22).
     */
    public data class Default(val appName: String) : DataDirectory {
        init {
            require(appName.isNotBlank()) { "appName must not be blank" }
            require(appName != ".") { "appName must not be '.'" }
            require(appName.none { it == '/' || it == '\\' || it == '\u0000' }) {
                "appName must not contain path separators ('/', '\\') or NUL characters"
            }
            require(!appName.contains("..")) { "appName must not contain '..'" }
        }
    }

    /** Uses a custom absolute path as the data directory. */
    public data class Custom(val path: File) : DataDirectory
}

internal fun appDataDir(appName: String): File =
    when (OperatingSystem.current) {
        OperatingSystem.Windows -> File(windowsAppData(), appName)
        OperatingSystem.MacOS -> File(macUserHome(), "$MACOS_APP_SUPPORT_PATH/$appName")
        OperatingSystem.Linux -> File(linuxDataHome(), appName)
    }

private fun macUserHome(): String =
    System.getProperty(USER_HOME_PROPERTY)
        ?: error("system property '$USER_HOME_PROPERTY' is not set")

private fun windowsAppData(): String =
    System.getenv(WINDOWS_APPDATA_ENV)
        ?: error("environment variable '$WINDOWS_APPDATA_ENV' is not set")

private fun linuxDataHome(): String =
    System.getenv(LINUX_XDG_DATA_HOME_ENV) ?: "${macUserHome()}/$LINUX_LOCAL_SHARE_PATH"

private const val USER_HOME_PROPERTY = "user.home"
private const val WINDOWS_APPDATA_ENV = "APPDATA"
private const val MACOS_APP_SUPPORT_PATH = "Library/Application Support"
private const val LINUX_XDG_DATA_HOME_ENV = "XDG_DATA_HOME"
private const val LINUX_LOCAL_SHARE_PATH = ".local/share"
