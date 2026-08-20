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
import dev.lokksmith.createLokksmith
import dev.lokksmith.discoveryUrl
import dev.lokksmith.id
import dev.lokksmith.metadata
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Entry point for native Swift consumers. Manages persisted [LokksmithClient] instances.
 *
 * Create exactly one instance for the lifetime of the app and share it:
 * ```swift
 * let lokksmith = LokksmithManager()
 * let client = try await lokksmith.getOrCreateClient(
 *     key: "main",
 *     configuration: .discovery(
 *         clientId: "my-client-id",
 *         discoveryUrl: "https://example.com/.well-known/openid-configuration"
 *     )
 * )
 * ```
 *
 * Multiple instances must each use a distinct [persistenceFileBaseName]; any other configuration
 * leads to undefined behaviour.
 *
 * @param persistenceFileBaseName Base name of the file backing client state.
 * @param userAgent The `User-Agent` sent with HTTP requests. Empty uses the default; `null` sends
 *   none.
 */
public class LokksmithManager
public constructor(persistenceFileBaseName: String, userAgent: String?) {

    /** Creates a manager with the default persistence file name and `User-Agent`. */
    public constructor() : this(DEFAULT_PERSISTENCE_FILE_BASE_NAME, "")

    private val coroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("LokksmithSwift"))

    private val lokksmith: Lokksmith =
        createLokksmith(
            Lokksmith.Options(
                persistenceFileBaseName = persistenceFileBaseName,
                coroutineScope = coroutineScope,
                userAgent = userAgent,
            )
        )

    /** Migrates tokens from another OIDC library into a client. */
    public val migration: LokksmithMigration = LokksmithMigration(lokksmith)

    /**
     * Returns the client stored under [key], or `null` if there is none.
     *
     * @throws LokksmithFailure if the stored client could not be read.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun client(key: String): LokksmithClient? = mapFailures {
        lokksmith.get(key)?.let { wrap(it) }
    }

    /**
     * Creates a client under [key].
     *
     * With [LokksmithClientConfiguration.discovery] this performs a discovery request; with
     * [LokksmithClientConfiguration.metadata] it does not touch the network.
     *
     * @throws LokksmithFailure if a client already exists under [key], or creation failed.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun createClient(
        key: String,
        configuration: LokksmithClientConfiguration,
    ): LokksmithClient = mapFailures {
        wrap(lokksmith.create(key, builder = configuration.builder()))
    }

    /**
     * Returns the client stored under [key], creating it from [configuration] if there is none.
     *
     * @throws LokksmithFailure if the client could not be read or created.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun getOrCreateClient(
        key: String,
        configuration: LokksmithClientConfiguration,
    ): LokksmithClient = mapFailures {
        wrap(lokksmith.getOrCreate(key, builder = configuration.builder()))
    }

    /** `true` if a client is stored under [key]. */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun clientExists(key: String): Boolean = mapFailures { lokksmith.exists(key) }

    /**
     * Deletes the client stored under [key], including its tokens.
     *
     * @return `false` if there was no such client.
     */
    @Throws(LokksmithFailure::class, kotlinx.coroutines.CancellationException::class)
    public suspend fun deleteClient(key: String): Boolean = mapFailures { lokksmith.delete(key) }

    /**
     * Releases every resource held by this instance. The manager must not be used afterwards.
     *
     * Persisted state is unaffected.
     */
    public fun dispose() {
        lokksmith.dispose()
        coroutineScope.cancel()
    }

    internal fun wrap(client: dev.lokksmith.client.Client): LokksmithClient =
        LokksmithClient(lokksmith = lokksmith, client = client, coroutineScope = coroutineScope)

    private companion object {
        const val DEFAULT_PERSISTENCE_FILE_BASE_NAME = "lokksmith_clients"
    }
}

/**
 * Bridges [LokksmithClientConfiguration] to the Kotlin builder DSL, whose write-only extension
 * properties are not reachable from Swift.
 */
private fun LokksmithClientConfiguration.builder(): (dev.lokksmith.CreateContext) -> Unit {
    val configuredId = clientId
    val configuredDiscoveryUrl = discoveryUrl
    val configuredMetadata = metadata
    return { context ->
        context.id = configuredId
        configuredDiscoveryUrl?.let { context.discoveryUrl = it }
        configuredMetadata?.let { context.metadata = it.toCore() }
    }
}
