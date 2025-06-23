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

import dev.drewhamilton.poko.Poko
import dev.lokksmith.client.Client
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result.Error.Type
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.confirmConsumed
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.forClient
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.Snapshot.FlowResult.Error.Type as FlowResultErrorType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Provides an observable [Result] of the current or last known progress of an auth flow which can
 * be used to display some progression in the UI layer of an application.
 *
 * @see AuthFlowResultProvider.forClient
 * @see AuthFlowResultProvider.confirmConsumed
 * @see authFlowResult
 */
public object AuthFlowResultProvider {

    public sealed interface Result {

        public data object Undefined : Result

        @Poko public class Processing(public val state: String) : Result

        @Poko public class Success(public val state: String) : Result

        @Poko public class Cancelled(public val state: String) : Result

        @Poko
        public class Error(
            public val state: String,
            public val type: Type,
            public val message: String?,
            public val code: String? = null,
        ) : Result {

            public enum class Type {
                Generic,
                OAuth,
                Validation,
                TemporalValidation,
            }
        }
    }

    /**
     * Provides an observable [Result] of the current or last known progress of an auth flow which
     * can be used to display some progression in the UI layer of an application. Returns
     * [Result.Undefined] if no auth flow is currently being processed.
     *
     * Note: The `Flow` might be blocking and not immediately and always return a value, especially
     * when a previous result has been confirmed.
     *
     * @see authFlowResult
     * @see confirmConsumed
     */
    public fun forClient(client: Client): Flow<Result> =
        (client as InternalClient)
            .snapshots
            .map { snapshot ->
                val flowResult = snapshot.flowResult
                when {
                    flowResult is Snapshot.FlowResult.Success ->
                        Result.Success(state = flowResult.state)

                    flowResult is Snapshot.FlowResult.Cancelled ->
                        Result.Cancelled(state = flowResult.state)

                    flowResult is Snapshot.FlowResult.Error ->
                        Result.Error(
                            state = flowResult.state,
                            type =
                                when (flowResult.type) {
                                    FlowResultErrorType.Generic -> Type.Generic
                                    FlowResultErrorType.OAuth -> Type.OAuth
                                    FlowResultErrorType.Validation -> Type.Validation
                                    FlowResultErrorType.TemporalValidation ->
                                        Type.TemporalValidation
                                },
                            message = flowResult.message,
                            code = flowResult.code,
                        )

                    flowResult is Snapshot.FlowResult.Consumed -> null

                    snapshot.ephemeralFlowState != null ->
                        Result.Processing(state = snapshot.ephemeralFlowState.state)

                    else -> Result.Undefined
                }
            }
            .filterNotNull()
            .distinctUntilChanged()

    /**
     * Confirms that the result has been consumed and displayed to the user so that it does not
     * reappear. The `Flow` provided via [forClient] does not produce a new value until a new auth
     * flow was initiated.
     *
     * @see confirmAuthFlowResultConsumed
     */
    public suspend fun confirmConsumed(client: Client) {
        (client as InternalClient).updateSnapshot {
            copy(flowResult = Snapshot.FlowResult.Consumed)
        }
    }
}

/** @see AuthFlowResultProvider.forClient */
public val Client.authFlowResult: Flow<AuthFlowResultProvider.Result>
    get() = forClient(this)

/** @see AuthFlowResultProvider.confirmConsumed */
public suspend fun Client.confirmAuthFlowResultConsumed() {
    confirmConsumed(this)
}
