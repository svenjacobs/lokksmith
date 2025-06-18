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
package dev.lokksmith.client.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RandomTest {

    private val random = Random()

    @Test
    fun `randomAsciiString should produce string of specified length`() {
        assertEquals(
            128,
            random.randomAsciiString(128).length,
        )
    }

    @Test
    fun `randomAsciiString should produce string with only allowed characters`() {
        assertTrue(
            random.randomAsciiString(128).all { it in Random.asciiChars },
        )
    }

    @Test
    fun `randomAsciiString should throw exception if length is 0`() {
        assertFailsWith<IllegalArgumentException> {
            random.randomAsciiString(0)
        }
    }

    @Test
    fun `randomCodeVerifier should produce string of specified length`() {
        assertEquals(
            43,
            random.randomCodeVerifier(43).length,
        )
    }

    @Test
    fun `randomCodeVerifier should produce string with only allowed characters`() {
        assertTrue(
            random.randomCodeVerifier(128).all { it in Random.codeVerifierChars },
        )
    }

    @Test
    fun `randomCodeVerifier should throw exception if length is less than 43 or greater than 128`() {
        assertFailsWith<IllegalArgumentException> {
            random.randomCodeVerifier(42)
        }

        assertFailsWith<IllegalArgumentException> {
            random.randomCodeVerifier(129)
        }
    }
}
