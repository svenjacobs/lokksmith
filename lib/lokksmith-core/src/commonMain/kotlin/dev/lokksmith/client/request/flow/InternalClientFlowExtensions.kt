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

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.FlowResult

internal suspend fun InternalClient.recordResponseUri(responseUri: String) {
    updateSnapshot {
        val updated =
            when (val s = ephemeralFlowState) {
                is Snapshot.EphemeralAuthorizationCodeFlowState -> s.copy(responseUri = responseUri)
                is Snapshot.EphemeralEndSessionFlowState -> s.copy(responseUri = responseUri)
                null -> throw IllegalStateException("ephemeralFlowState is null")
            }
        copy(ephemeralFlowState = updated)
    }
}

internal suspend fun InternalClient.recordError(
    state: String,
    message: String?,
    type: FlowResult.Error.Type = FlowResult.Error.Type.Generic,
) {
    AuthFlowStateFinalizer(this).finalize {
        copy(flowResult = FlowResult.Error(state = state, type = type, message = message))
    }
}
