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
package dev.lokksmith.client.request.flow

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.SnapshotStoreSpy
import dev.lokksmith.createTestLokksmith
import dev.lokksmith.mockMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowUserAgentResponseHandlerTest {

    @Test
    fun `onResponse should set response URI for Authorization Code Flow`() = runTest {
        val (lokksmith, snapshotStore) =
            createTestLokksmithWithEphemeralFlowState(
                Snapshot.EphemeralAuthorizationCodeFlowState(
                    state = "i0aMAY0V",
                    redirectUri = "https://example.com/redirect",
                    codeVerifier = "2XCBMopbO8",
                    responseUri = null,
                )
            )
        val handler = AuthFlowUserAgentResponseHandler(lokksmith)

        handler.onResponse(
            key = "key",
            responseUri =
                "https://example.com/redirect?code=B5ueWoUdeT&code_verifier=2XCBMopbO8&state=i0aMAY0V",
        )

        runCurrent()
        val snapshot = snapshotStore.observe("key".asKey()).first()

        assertEquals(
            "https://example.com/redirect?code=B5ueWoUdeT&code_verifier=2XCBMopbO8&state=i0aMAY0V",
            snapshot?.ephemeralFlowState?.responseUri,
        )
    }

    @Test
    fun `onResponse should set response URI for End Session Flow`() = runTest {
        val (lokksmith, snapshotStore) =
            createTestLokksmithWithEphemeralFlowState(
                Snapshot.EphemeralEndSessionFlowState(state = "i0aMAY0V", responseUri = null)
            )
        val handler = AuthFlowUserAgentResponseHandler(lokksmith)

        handler.onResponse(key = "key", responseUri = "https://example.com/redirect?state=i0aMAY0V")

        runCurrent()
        val snapshot = snapshotStore.observe("key".asKey()).first()

        assertEquals(
            "https://example.com/redirect?state=i0aMAY0V",
            snapshot?.ephemeralFlowState?.responseUri,
        )
    }

    @Test
    fun `onResponse should throw exception if ephemeralFlowState is null`() = runTest {
        val (lokksmith) = createTestLokksmithWithEphemeralFlowState(null)
        val handler = AuthFlowUserAgentResponseHandler(lokksmith)

        assertFailsWith<IllegalStateException> {
            handler.onResponse(key = "key", responseUri = "https://example.com/redirect")
        }
    }

    @Test
    fun `onCancel should cancel auth flow`() = runTest {
        val (lokksmith, snapshotStore) =
            createTestLokksmithWithEphemeralFlowState(
                Snapshot.EphemeralAuthorizationCodeFlowState(
                    state = "i0aMAY0V",
                    redirectUri = "https://example.com/redirect",
                    codeVerifier = "2XCBMopbO8",
                    responseUri = null,
                )
            )
        val handler = AuthFlowUserAgentResponseHandler(lokksmith)

        handler.onCancel(key = "key", state = "i0aMAY0V")

        runCurrent()
        val snapshot = snapshotStore.observe("key".asKey()).first()!!
        assertNull(snapshot.ephemeralFlowState)
        assertEquals(Snapshot.FlowResult.Cancelled(state = "i0aMAY0V"), snapshot.flowResult)
    }

    @Test
    fun `onError should set error state`() = runTest {
        val (lokksmith, snapshotStore) =
            createTestLokksmithWithEphemeralFlowState(
                Snapshot.EphemeralAuthorizationCodeFlowState(
                    state = "i0aMAY0V",
                    redirectUri = "https://example.com/redirect",
                    codeVerifier = "2XCBMopbO8",
                    responseUri = null,
                )
            )
        val handler = AuthFlowUserAgentResponseHandler(lokksmith)

        handler.onError(
            key = "key",
            state = "i0aMAY0V",
            message = "error message",
            type = Snapshot.FlowResult.Error.Type.Generic,
        )

        runCurrent()
        val snapshot = snapshotStore.observe("key".asKey()).first()!!
        assertNull(snapshot.ephemeralFlowState)
        assertEquals(
            Snapshot.FlowResult.Error(
                state = "i0aMAY0V",
                type = Snapshot.FlowResult.Error.Type.Generic,
                message = "error message",
            ),
            snapshot.flowResult,
        )
    }

    private suspend fun TestScope.createTestLokksmithWithEphemeralFlowState(
        ephemeralFlowState: Snapshot.EphemeralFlowState?
    ): Pair<Lokksmith, SnapshotStoreSpy> {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot =
                Snapshot(
                    key = key,
                    id = "clientId".asId(),
                    metadata = mockMetadata,
                    ephemeralFlowState = ephemeralFlowState,
                ),
        )

        return Pair(lokksmith, snapshotStore)
    }
}
