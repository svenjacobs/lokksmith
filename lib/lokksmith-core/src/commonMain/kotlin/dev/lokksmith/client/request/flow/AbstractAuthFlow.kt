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
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.snapshot.Snapshot

public abstract class AbstractAuthFlow
internal constructor(
    internal val client: InternalClient,
    internal val state: String,
    private val responseHandler: AuthFlowResponseHandler,
    private val cancellation: AuthFlowCancellation,
) : AuthFlow {

    internal abstract val ephemeralFlowState: Snapshot.EphemeralFlowState

    /** Returns the URL to initiate the flow. */
    internal abstract suspend fun onPrepare(): String

    internal open fun onPrepareUpdateSnapshot(snapshot: Snapshot): Snapshot = snapshot

    override suspend fun prepare(): Initiation {
        client.updateSnapshot {
            onPrepareUpdateSnapshot(
                copy(
                    flowResult = null,
                    ephemeralFlowState = this@AbstractAuthFlow.ephemeralFlowState,
                )
            )
        }

        return Initiation(state = state, requestUrl = onPrepare(), clientKey = client.key.value)
    }

    override suspend fun onResponse(redirectUri: String) {
        responseHandler.onResponse(redirectUri)
    }

    override suspend fun cancel() {
        cancellation.cancel(state)
    }
}
