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
package dev.lokksmith

import dev.lokksmith.client.Client
import dev.lokksmith.client.ClientImpl
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.migrate
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Manages persistable [Client] instances.
 *
 * Clients are identified by a unique internal key, distinct from the OAuth client ID. This design
 * supports multiple environment-specific configurations (e.g. production and testing) that
 * reference the same OAuth client ID while remaining independently addressable within the system.
 *
 * ## Singleton
 *
 * It is **strongly** recommended to use only a single instance of [Lokksmith] in your application
 * and utilize that **singleton** reference for getting or creating [Client] instances. If you
 * absolutely must use multiple instances, make sure that each one uses a unique persistence file
 * specified via [Options.persistenceFileBaseName]. Any other configuration may lead to undefined or
 * erroneous behaviour.
 *
 * Use the platform-specific `createLokksmith()` function to create an instance.
 *
 * @see get
 * @see create
 * @see getOrCreate
 * @see exists
 * @see delete
 */
public class Lokksmith
internal constructor(
    /**
     * Internal dependency container for this [Lokksmith] instance.
     *
     * Provides access to core services such as persistence, HTTP client, serialization, and
     * coroutine scope. This property is intended for internal use and is only public to allow
     * access from other Lokksmith modules. Application code must not interact with the container
     * directly.
     */
    public val container: Container
) {

    internal constructor(
        platformContext: PlatformContext,
        options: Options = Options(),
    ) : this(container = ContainerImpl(platformContext, options))

    public data class Options(
        /**
         * Name of [androidx.datastore.core.DataStore] file for client state persistence. Must be
         * unique per [Lokksmith] instance.
         */
        val persistenceFileBaseName: String = "lokksmith_clients",

        /**
         * Coroutine scope that is used to launch various coroutines in the context of this
         * [Lokksmith] instance.
         */
        val coroutineScope: CoroutineScope =
            CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("Lokksmith")),

        /**
         * The User-Agent string sent with HTTP requests.
         *
         * When set to a non-empty string uses the specified User-Agent for all HTTP network
         * requests, else uses default User-Agent.
         *
         * Set to null to disable User-Agent altogether.
         */
        val userAgent: String? = "",

        /**
         * Ktor's HTTP client engine to be used.
         *
         * By default, it's `OkHttp` on Android and `Darwin` on iOS. This property allows to plug in
         * a configured engine or an entirely different engine if required.
         */
        val httpClientEngine: HttpClientEngine = platformHttpClientEngine,

        /**
         * Provides the current [kotlin.time.Instant] used throughout this [Lokksmith] instance.
         *
         * The current time is consulted at several points during token lifecycle management, such
         * as checking whether an access token or ID token has expired. By default,
         * [DateProviders.Default] is used, which delegates to [kotlin.time.Clock.System.now].
         *
         * Override this property when your application has access to a more reliable or
         * network-synchronized time source that should take precedence over the device clock.
         *
         * @see DateProvider
         * @see DateProviders
         */
        val dateProvider: DateProvider = DateProviders.Default,
    ) {
        init {
            require(persistenceFileBaseName.isNotBlank()) {
                "persistenceFileBaseName must not be blank"
            }
        }
    }

    /**
     * Returns persisted client with the given [key] or `null` if no client for that key exists.
     *
     * Calling [get] multiple times with the same key returns new [Client] instances that share
     * synchronized state. However, it's recommended to use a single instance per unique key.
     *
     * @param key Key of new client
     * @param options Options for configuring the behaviour of the client
     */
    public suspend fun get(key: String, options: Client.Options = Client.Options()): Client? {
        if (!exists(key)) return null
        val key = key.asKey()
        return ClientImpl.create(
                key = key,
                options = options,
                coroutineScope = container.coroutineScope,
                snapshotStore = container.snapshotStore,
                provider = container.clientProviderFactory(),
            )
            .migrate()
    }

    /**
     * Returns the persisted client with the given [key], or creates a new instance using [builder]
     * if one does not exist.
     *
     * Calling [getOrCreate] multiple times with the same key returns new [Client] instances that
     * share synchronized state. However, it's recommended to use a single instance per unique key.
     *
     * @param key Key of new client
     * @param options Options for configuring the behaviour of the client
     */
    public suspend fun getOrCreate(
        key: String,
        options: Client.Options = Client.Options(),
        builder: CreateContext.() -> Unit,
    ): Client =
        try {
            get(key, options) ?: create(key, options, builder)
        } catch (_: ClientAlreadyExistsException) {
            // We're catching the exception here because both get() and create() are suspending
            // functions. A suspension point between those two calls could lead to race conditions.
            // In case the client was already created in the meantime we will return it here.
            get(key, options)!!
        }

    /** Returns `true` if a client for the given [key] exists. */
    public suspend fun exists(key: String): Boolean = container.snapshotStore.exists(key.asKey())

    /**
     * Creates a new client with the given [key] either by static configuration or discovery using
     * [builder] for initial configuration.
     *
     * @param key Key of new client
     * @param options Options for configuring the behaviour of the client
     * @throws ClientAlreadyExistsException if client with key already exists
     */
    public suspend fun create(
        key: String,
        options: Client.Options = Client.Options(),
        builder: CreateContext.() -> Unit,
    ): Client {
        if (exists(key))
            throw ClientAlreadyExistsException("client with key \"$key\" already exists")

        val context =
            CreateContext().apply {
                builder()
                validate()
            }

        val key = key.asKey()
        val id = context.props.id!!.asId() // id cannot be null at this point
        val metadata =
            context.props.discoveryUrl?.let { url -> container.metadataDiscoveryRequest(url) }
                ?: context.props.metadata!! // metadata cannot be null at this point

        // Create initial snapshot
        container.snapshotStore.set(
            key = key,
            snapshot = Snapshot(key = key, id = id, metadata = metadata),
        )

        return ClientImpl.create(
            key = key,
            options = options,
            coroutineScope = container.coroutineScope,
            snapshotStore = container.snapshotStore,
            provider = container.clientProviderFactory(),
        )
    }

    /**
     * Deletes client with the given [key] or returns `false` if client doesn't exist.
     *
     * Don't use a [Client] instance for given key after it has been deleted. Doing so might result
     * in undefined and erroneous behaviour.
     */
    public suspend fun delete(key: String): Boolean = container.snapshotStore.delete(key.asKey())

    /**
     * Releases all resources held by this Lokksmith instance and performs necessary cleanup.
     *
     * After calling this method, the Lokksmith instance and all [Clients][Client] produced by it
     * become nonfunctional and must not be used. Any ongoing operations may be cancelled, and
     * further method calls may result in undefined behaviour.
     *
     * This method should be called when the Lokksmith instance is no longer needed to avoid
     * resource leaks.
     */
    public fun dispose() {
        container.coroutineScope.cancel()
    }
}

internal suspend fun Lokksmith.getInternal(key: String): InternalClient =
    checkNotNull(get(key)) { "client with key \"$key\" not found" } as InternalClient

/** @see Lokksmith.Options.httpClientEngine */
internal expect val platformHttpClientEngine: HttpClientEngine
