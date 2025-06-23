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
package dev.lokksmith.client.snapshot

import dev.drewhamilton.poko.Poko
import dev.lokksmith.client.Client
import dev.lokksmith.client.Id
import dev.lokksmith.client.Key
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val CURRENT_SCHEMA_VERSION = 2

/** A [Snapshot] represents the persistable state of a [Client]. */
@Serializable
public data class Snapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val key: Key,
    val id: Id,
    val metadata: Client.Metadata,
    val options: Client.Options,
    val tokens: Client.Tokens? = null,

    /**
     * nonce needs to be remembered beyond authorization code flow because it may be also required
     * for every refresh response validation.
     */
    val nonce: String? = null,

    /**
     * Holds the result of the most recently executed auth flow.
     *
     * This property is particularly important when flow initiation and response handling are
     * decoupled, as is common on mobile devices and with certain UI frameworks. By persisting the
     * flow result, the client can reliably track whether the last flow completed successfully or
     * encountered an error, even if the app process is restarted or the UI is recreated.
     *
     * @see dev.lokksmith.client.request.flow.AuthFlowResultProvider
     */
    val flowResult: FlowResult? = null,

    /** Holds temporary data that is required to fulfill an auth request. */
    val ephemeralFlowState: EphemeralFlowState? = null,

    /** Flag that denotes if the client was migrated via [dev.lokksmith.client.Migration]. */
    val migrated: Boolean = false,
) {

    @Serializable
    public sealed interface FlowResult {

        @Poko @Serializable public class Success(public val state: String) : FlowResult

        @Poko @Serializable public class Cancelled(public val state: String) : FlowResult

        @Poko
        @Serializable
        public class Error(
            public val state: String,
            @SerialName("errorType") public val type: Type,
            public val message: String?,
            public val code: String? = null,
        ) : FlowResult {

            public enum class Type {
                Generic,
                OAuth,
                Validation,
                TemporalValidation,
            }
        }

        @Serializable public data object Consumed : FlowResult
    }

    @Serializable
    public sealed interface EphemeralFlowState {
        public val state: String
        public val responseUri: String?
    }

    @Serializable
    public data class EphemeralAuthorizationCodeFlowState(
        override val state: String,
        override val responseUri: String?,
        val redirectUri: String,
        val codeVerifier: String?,
    ) : EphemeralFlowState

    @Serializable
    public data class EphemeralEndSessionFlowState(
        override val state: String,
        override val responseUri: String?,
    ) : EphemeralFlowState
}
