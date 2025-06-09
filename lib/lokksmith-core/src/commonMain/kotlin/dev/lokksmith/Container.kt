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

/**
 * IoC container providing dependencies
 */
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
            userAgent = options.userAgent,
        )
    }

    //<editor-fold desc="SnapshotStore">
    private val snapshotDataStore by lazy {
        createDataStore(
            fileName = "${options.persistenceFileBaseName.trim()}.preferences_pb",
            platformContext = platformContext,
        )
    }

    override val snapshotStore by lazy {
        SnapshotStoreImpl(
            dataStore = snapshotDataStore,
            serializer = serializer,
        )
    }
    //</editor-fold>

    override val metadataDiscoveryRequest by lazy { MetadataDiscoveryRequestImpl(httpClient) }

    override val clientProviderFactory: () -> InternalClient.Provider = {
        ClientImpl.DefaultProvider(
            httpClient = httpClient,
            serializer = serializer,
        )
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

internal fun createHttpClient(
    engine: HttpClientEngine,
    userAgent: String? = null,
) = HttpClient(engine) {
    install(ContentNegotiation) {
        json()
    }

    userAgent?.let { userAgent ->
        install(UserAgent) {
            agent = userAgent
        }
    }
}
