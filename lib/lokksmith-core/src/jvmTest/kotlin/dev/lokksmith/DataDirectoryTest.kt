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

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DataDirectoryTest {

    @Test
    fun `Default rejects blank appName`() {
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("") }
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("   ") }
    }

    @Test
    fun `Default rejects path separators`() {
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("a/b") }
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("a\\b") }
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("/etc/x") }
    }

    @Test
    fun `Default rejects parent traversal`() {
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("..") }
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("../evil") }
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("a..b") }
    }

    @Test
    fun `Default rejects single dot`() {
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default(".") }
    }

    @Test
    fun `Default rejects NUL`() {
        assertFailsWith<IllegalArgumentException> { DataDirectory.Default("a\u0000b") }
    }

    @Test
    fun `Default accepts ordinary appName`() {
        // No throw.
        DataDirectory.Default("MyApp")
        DataDirectory.Default("my-app_1")
        DataDirectory.Default("my app")
    }
}
