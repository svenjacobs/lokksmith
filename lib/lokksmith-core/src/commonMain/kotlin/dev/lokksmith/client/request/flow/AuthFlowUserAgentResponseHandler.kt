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
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.authorizationCode.AuthorizationCodeFlowCancellation
import dev.lokksmith.client.request.flow.endSession.EndSessionFlowCancellation
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.FlowResult
import dev.lokksmith.getInternal

/**
 * Handles OAuth flow success and failure responses when handling the result provided by the User
 * Agent (the browser).
 *
 * This handler is designed for scenarios where the initiation of an OAuth flow and the handling of
 * its response are decoupled, such as on mobile platforms.
 *
 * @see AuthFlowStateResponseHandler
 */
public class AuthFlowUserAgentResponseHandler(private val lokksmith: Lokksmith) {

    /** Stores the [responseUri] in the client's ephemeral flow state for further processing. */
    public suspend fun onResponse(key: String, responseUri: String) {
        val client = lokksmith.getInternal(key)
        client.updateSnapshot {
            val updatedFlowState =
                when (val state = ephemeralFlowState) {
                    is Snapshot.EphemeralAuthorizationCodeFlowState ->
                        state.copy(responseUri = responseUri)

                    is Snapshot.EphemeralEndSessionFlowState ->
                        state.copy(responseUri = responseUri)

                    null -> throw IllegalStateException("ephemeralFlowState is null")
                }

            copy(ephemeralFlowState = updatedFlowState)
        }
    }

    /** Marks the flow result as cancelled. */
    public suspend fun onCancel(key: String) {
        val client = lokksmith.getInternal(key)
        val ephemeralFlowState = client.requireEphemeralFlowState
        val cancellation =
            when (ephemeralFlowState) {
                is Snapshot.EphemeralAuthorizationCodeFlowState ->
                    ::AuthorizationCodeFlowCancellation

                is Snapshot.EphemeralEndSessionFlowState -> ::EndSessionFlowCancellation
            }(client)

        cancellation.cancel(ephemeralFlowState.state)
    }

    /** Marks the flow result as erroneous. */
    public suspend fun onError(
        key: String,
        message: String?,
        type: FlowResult.Error.Type = FlowResult.Error.Type.Generic,
    ) {
        val client = lokksmith.getInternal(key)
        val ephemeralFlowState = client.requireEphemeralFlowState

        client.updateSnapshot {
            copy(
                flowResult =
                    FlowResult.Error(
                        state = ephemeralFlowState.state,
                        type = type,
                        message = message,
                    )
            )
        }
    }

    private val InternalClient.requireEphemeralFlowState: Snapshot.EphemeralFlowState
        get() = checkNotNull(snapshots.value.ephemeralFlowState) { "ephemeral flow state is null" }
}
