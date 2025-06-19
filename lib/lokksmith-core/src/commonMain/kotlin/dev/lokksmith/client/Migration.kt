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
package dev.lokksmith.client

import dev.lokksmith.Lokksmith
import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.jwt.JwtDecoder
import dev.lokksmith.client.token.JwtToIdTokenMapper
import kotlinx.serialization.json.Json

/**
 * Utility class for migrating existing authentication tokens from another library into a Lokksmith
 * [Client] instance, enabling seamless user migration without requiring re-authentication.
 *
 * This class is intended for one-time use per [Client] during migration scenarios, such as
 * switching from a different authentication library to Lokksmith. It should not be used for regular
 * token management or refresh operations.
 *
 * @see setTokens
 * @see isMigrated
 * @see setMigrated
 * @see Lokksmith.migration
 */
public class Migration
internal constructor(
    serializer: Json,
    private val jwtDecoder: JwtDecoder = JwtDecoder(serializer),
    private val jwtToIdTokenMapper: JwtToIdTokenMapper = JwtToIdTokenMapper(),
) {

    /**
     * Injects externally obtained authentication tokens into the specified [client] for migration
     * purposes.
     *
     * All tokens must be provided together; any existing tokens in the client will be overwritten.
     *
     * After calling this method, the [client] will use the provided tokens for authentication and
     * authorization. This operation is intended for one-time use during migration and should not be
     * used for regular token updates or refreshes.
     *
     * @param client The [Client] instance to receive the migrated tokens.
     * @param accessToken The access token string to set.
     * @param accessTokenExpiresAt The expiration timestamp (epoch seconds) for the access token, or
     *   `null` if not applicable.
     * @param refreshToken The refresh token string to set, or `null` if not available.
     * @param refreshTokenExpiresAt The expiration timestamp (epoch seconds) for the refresh token,
     *   or `null` if not applicable.
     * @param idToken The ID token string to set (JWT-encoded).
     * @param setMigratedFlag Sets the migrated flag on this client instance. Any further migration
     *   attempt will throw an exception.
     * @throws IllegalStateException if the client was already migrated
     * @throws IllegalArgumentException if the ID token cannot be decoded or mapped.
     */
    public suspend fun setTokens(
        client: Client,
        accessToken: String,
        accessTokenExpiresAt: Long?,
        refreshToken: String?,
        refreshTokenExpiresAt: Long?,
        idToken: String,
        setMigratedFlag: Boolean = true,
    ) {
        val jwt = jwtDecoder.decode(idToken)
        val idToken = jwtToIdTokenMapper(jwt, idToken)
        val client = (client as InternalClient)

        if (client.snapshots.value.migrated) {
            throw IllegalStateException("Client was already migrated")
        }

        client.updateSnapshot {
            copy(
                tokens =
                    Tokens(
                        accessToken =
                            Tokens.AccessToken(
                                token = accessToken,
                                expiresAt = accessTokenExpiresAt,
                            ),
                        refreshToken =
                            refreshToken?.let {
                                Tokens.RefreshToken(
                                    token = refreshToken,
                                    expiresAt = refreshTokenExpiresAt,
                                )
                            },
                        idToken = idToken,
                    )
            )
        }

        if (setMigratedFlag) {
            setMigrated(client)
        }
    }

    /**
     * Returns `true` if this client was already migrated. Calling [setTokens] on a migrated client
     * will throw an exception.
     */
    public fun isMigrated(client: Client): Boolean =
        (client as InternalClient).snapshots.value.migrated

    /**
     * Applies the migrated flag to the client. This is usually done automatically by [setTokens]
     * unless `setMigratedFlag` was set to `false`.
     */
    public suspend fun setMigrated(client: Client, migrated: Boolean = true) {
        (client as InternalClient).updateSnapshot { copy(migrated = migrated) }
    }
}

/** Provides an instance of [Migration]. */
public val Lokksmith.migration: Migration
    get() = Migration(container.serializer)
