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
) : AuthFlow {

    private val redirectUriHandler: RedirectUriHandler = client.provider.redirectUriHandler(client)

    /** The redirect URI as supplied by the consumer in the [AuthFlow.Request]. */
    internal abstract val rawRedirectUri: String

    /**
     * What the redirect is for. Lets the [redirectUriHandler] tailor its response (e.g. JVM picks a
     * different success page for sign-in vs sign-out).
     */
    internal abstract val redirectPurpose: RedirectUriHandler.Purpose

    /** Returns the URL to initiate the flow, using the resolved [redirectUri]. */
    internal abstract suspend fun onPrepare(redirectUri: String): String

    /**
     * Builds the ephemeral flow state to persist for this flow, using the resolved [redirectUri].
     */
    internal abstract fun createEphemeralFlowState(redirectUri: String): Snapshot.EphemeralFlowState

    internal open fun onPrepareUpdateSnapshot(snapshot: Snapshot): Snapshot = snapshot

    override suspend fun prepare(): Initiation {
        val redirectUri = redirectUriHandler.resolve(rawRedirectUri, state, redirectPurpose)
        val requestUrl = onPrepare(redirectUri)

        client.updateSnapshot {
            onPrepareUpdateSnapshot(
                copy(flowResult = null, ephemeralFlowState = createEphemeralFlowState(redirectUri))
            )
        }

        return Initiation(state = state, requestUrl = requestUrl, clientKey = client.key.value)
    }

    override suspend fun onResponse(redirectUri: String) {
        responseHandler.onResponse(redirectUri)
    }

    override suspend fun cancel() {
        try {
            AuthFlowCancellation(client).cancel(state)
        } finally {
            redirectUriHandler.release(state)
        }
    }
}
