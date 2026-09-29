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
package dev.lokksmith.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeaderFieldsToApplyTest {

    private val headers: Map<Any?, *> = mapOf("X-Test" to "value")

    @Test
    fun `should return headers when supported`() {
        assertEquals(headers, headerFieldsToApply(fields = headers, isSupported = true))
    }

    @Test
    fun `should drop headers when not supported`() {
        assertNull(headerFieldsToApply(fields = headers, isSupported = false))
    }

    @Test
    fun `should return null for empty headers`() {
        assertNull(headerFieldsToApply(fields = emptyMap<Any?, Any?>(), isSupported = true))
    }

    @Test
    fun `should return null for null headers`() {
        assertNull(headerFieldsToApply(fields = null, isSupported = true))
    }
}
