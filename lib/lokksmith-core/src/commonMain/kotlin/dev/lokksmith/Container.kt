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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.lokksmith.client.ClientImpl
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.discovery.MetadataDiscoveryRequest
import dev.lokksmith.client.discovery.MetadataDiscoveryRequestImpl
import dev.lokksmith.client.snapshot.SnapshotStore
import dev.lokksmith.client.snapshot.SnapshotStoreImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

internal expect class PlatformContext

/** IoC container providing dependencies */
public interface Container {
    public val coroutineScope: CoroutineScope
    public val httpClient: HttpClient
    public val snapshotStore: SnapshotStore
    public val metadataDiscoveryRequest: MetadataDiscoveryRequest
    public val serializer: Json
    public val clientProviderFactory: () -> InternalClient.Provider
}

internal class ContainerImpl(
    private val platformContext: PlatformContext,
    private val options: Lokksmith.Options,
) : Container {

    override val coroutineScope = options.coroutineScope

    override val serializer = Json

    override val httpClient by lazy {
        createHttpClient(
            engine = options.httpClientEngine,
            userAgent =
                when {
                    options.userAgent == null -> null
                    options.userAgent.isBlank() -> defaultUserAgent
                    else -> options.userAgent
                },
        )
    }

    // <editor-fold desc="SnapshotStore">
    private val snapshotDataStore by lazy {
        createDataStore(
            fileName = "${options.persistenceFileBaseName.trim()}.preferences_pb",
            platformContext = platformContext,
        )
    }

    override val snapshotStore by lazy {
        SnapshotStoreImpl(dataStore = snapshotDataStore, serializer = serializer)
    }
    // </editor-fold>

    override val metadataDiscoveryRequest by lazy { MetadataDiscoveryRequestImpl(httpClient) }

    override val clientProviderFactory: () -> InternalClient.Provider = {
        ClientImpl.DefaultProvider(httpClient = httpClient, serializer = serializer)
    }
}

internal fun createDataStore(
    fileName: String,
    producePath: (fileName: String) -> String,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { producePath(fileName).toPath(normalize = true) }

internal expect fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences>

internal fun createHttpClient(engine: HttpClientEngine, userAgent: String? = null) =
    HttpClient(engine) {
        install(ContentNegotiation) { json() }

        userAgent?.let { userAgent -> install(UserAgent) { agent = userAgent } }
    }

/** @see Lokksmith.Options.userAgent */
internal expect val platformUserAgentSuffix: String

/** @see Lokksmith.Options.userAgent */
private val defaultUserAgent: String
    get() = "Lokksmith/${BuildConfig.VERSION} ($platformUserAgentSuffix)"
