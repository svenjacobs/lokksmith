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
import dev.lokksmith.client.snapshot.Snapshot.FlowResult

/** Performs necessary cleanup when an auth flow was cancelled. */
internal class AuthFlowCancellation(
    client: InternalClient,
    private val stateFinalizer: AuthFlowStateFinalizer = AuthFlowStateFinalizer(client),
) {

    suspend fun cancel(state: String) {
        stateFinalizer.finalize { copy(flowResult = FlowResult.Cancelled(state = state)) }
    }
}
