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
package dev.lokksmith.swift

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * Covers the parts of the failure mapping reachable from this module.
 *
 * The per-exception branches of `toFailure()` cannot be exercised here: every constructor in
 * `lokksmith-core`'s exception hierarchy is `internal`, so instances cannot be created from another
 * module. Those branches are covered indirectly by the flows that throw them.
 */
class LokksmithFailureTest {

    @Test
    fun `toFailure should pass an existing failure through unchanged`() {
        val failure =
            LokksmithFailure(
                kind = LokksmithFailureKind.OAuthRejection,
                message = "invalid_grant",
                code = "invalid_grant",
            )

        assertSame(failure, failure.toFailure())
    }

    @Test
    fun `toFailure should report an unknown throwable as generic`() {
        val failure = RuntimeException("something broke").toFailure()

        assertEquals(LokksmithFailureKind.Generic, failure.kind)
        assertEquals("something broke", failure.message)
        assertNull(failure.code)
    }

    @Test
    fun `toFailure should tolerate a throwable without a message`() {
        val failure = RuntimeException().toFailure()

        assertEquals(LokksmithFailureKind.Generic, failure.kind)
        assertNull(failure.message)
    }

    @Test
    fun `mapFailures should wrap a throwable as a failure`() = runTest {
        val failure =
            assertFailsWith<LokksmithFailure> { mapFailures { throw RuntimeException("boom") } }

        assertEquals(LokksmithFailureKind.Generic, failure.kind)
        assertEquals("boom", failure.message)
    }

    @Test
    fun `mapFailures should rethrow cancellation unchanged`() = runTest {
        // Wrapping cancellation would break structured concurrency: a cancelled Swift Task must
        // not surface as a Lokksmith failure, and the parent coroutine must still see the cancel.
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> { mapFailures { throw cancellation } }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `mapFailures should return the value of a successful block`() = runTest {
        assertEquals("value", mapFailures { "value" })
    }
}
