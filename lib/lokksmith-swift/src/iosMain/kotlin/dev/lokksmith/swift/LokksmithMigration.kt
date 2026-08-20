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

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.migration

/**
 * Moves an existing session from another OIDC library into a Lokksmith client, so users are not
 * signed out by the switch.
 *
 * Perform this once per client. It is not a token-management API.
 *
 * A typical migration configures the client from [LokksmithClientConfiguration.metadata] — reusing
 * the endpoints the previous library persisted, so no discovery request is needed and the migration
 * works offline — then calls [setTokens] and forces one [LokksmithClient.refresh].
 */
public class LokksmithMigration internal constructor(private val lokksmith: Lokksmith) {

    /**
     * Injects externally obtained tokens into [client].
     *
     * All tokens are replaced together. Providers that do not expose a refresh token expiry are
     * common: pass the access token's expiry as [refreshTokenExpiresAt] and refresh immediately
     * afterwards.
     *
     * @throws LokksmithFailure if [client] was already migrated, or the ID token cannot be decoded.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun setTokens(
        client: LokksmithClient,
        accessToken: String,
        accessTokenExpiresAt: Long?,
        refreshToken: String?,
        refreshTokenExpiresAt: Long?,
        idToken: String,
    ): Unit = mapFailures {
        lokksmith.migration.setTokens(
            client = client.coreClient(),
            accessToken = accessToken,
            accessTokenExpiresAt = accessTokenExpiresAt,
            refreshToken = refreshToken,
            refreshTokenExpiresAt = refreshTokenExpiresAt,
            idToken = idToken,
        )
    }

    /** `true` if [client] was already migrated. Calling [setTokens] again would fail. */
    public fun isMigrated(client: LokksmithClient): Boolean =
        lokksmith.migration.isMigrated(client.coreClient())
}
