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

private const val OS_NAME_PROPERTY = "os.name"
private const val OS_NAME_MAC = "mac"
private const val OS_NAME_WIN = "win"

internal enum class OperatingSystem {
    Linux,
    MacOS,
    Windows;

    companion object {
        val current: OperatingSystem by lazy { fromOsName(System.getProperty(OS_NAME_PROPERTY)) }

        internal fun fromOsName(osName: String?): OperatingSystem {
            checkNotNull(osName) { "system property '$OS_NAME_PROPERTY' is not set" }
            val name = osName.lowercase()
            return when {
                OS_NAME_WIN in name -> Windows
                OS_NAME_MAC in name -> MacOS
                else -> Linux
            }
        }
    }
}
