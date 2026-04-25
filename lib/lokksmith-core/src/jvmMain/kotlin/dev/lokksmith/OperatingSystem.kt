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

private const val OS_NAME_PROPERTY = "os.name"
private const val OS_NAME_MAC = "mac"
private const val OS_NAME_WIN = "win"
private const val USER_HOME_PROPERTY = "user.home"
private const val WINDOWS_APPDATA_ENV = "APPDATA"
private const val MACOS_APP_SUPPORT_PATH = "Library/Application Support"
private const val LINUX_XDG_DATA_HOME_ENV = "XDG_DATA_HOME"
private const val LINUX_LOCAL_SHARE_PATH = ".local/share"

internal enum class OperatingSystem {
    Linux,
    MacOS,
    Windows;

    companion object {
        val current: OperatingSystem by lazy {
            val osName = System.getProperty(OS_NAME_PROPERTY).lowercase()
            when {
                OS_NAME_WIN in osName -> Windows
                OS_NAME_MAC in osName -> MacOS
                else -> Linux
            }
        }
    }
}

internal fun appDataDir(appName: String): File {
    val userHome = System.getProperty(USER_HOME_PROPERTY)
    return when (OperatingSystem.current) {
        OperatingSystem.Windows -> File(System.getenv(WINDOWS_APPDATA_ENV), appName)
        OperatingSystem.MacOS -> File(userHome, "$MACOS_APP_SUPPORT_PATH/$appName")
        OperatingSystem.Linux ->
            File(
                System.getenv(LINUX_XDG_DATA_HOME_ENV) ?: "$userHome/$LINUX_LOCAL_SHARE_PATH",
                appName,
            )
    }
}
