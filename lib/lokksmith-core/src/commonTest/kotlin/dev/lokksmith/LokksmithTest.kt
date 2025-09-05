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
import dev.lokksmith.client.TEST_INSTANT
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import dev.lokksmith.client.discovery.MetadataDiscoveryRequest
import dev.lokksmith.client.discovery.MetadataDiscoveryRequestImpl
import dev.lokksmith.client.snapshot.PersistenceFake
import dev.lokksmith.client.snapshot.Snapshot
import dev.lokksmith.client.snapshot.SnapshotStoreImpl
import dev.lokksmith.client.snapshot.SnapshotStoreSpy
import dev.lokksmith.client.snapshot.SnapshotStoreSpy.DeleteCall
import dev.lokksmith.client.snapshot.SnapshotStoreSpy.ExistsCall
import dev.lokksmith.client.snapshot.SnapshotStoreSpy.ObserveCall
import dev.lokksmith.client.snapshot.SnapshotStoreSpy.SetCall
import dev.lokksmith.discovery.MetadataDiscoveryRequestFake
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondBadRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class LokksmithTest {

    @Test
    fun `get should return existing client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
        )

        val client = assertNotNull(lokksmith.get(key.value))
        assertEquals("clientId".asId(), client.id)
        assertEquals(mockMetadata, client.metadata)

        assertEquals(1, snapshotStore.observeCalls.size)
        assertContains(
            snapshotStore.observeCalls,
            ObserveCall(key),
            "SnapshotStore.observe() not called",
        )
    }

    @Test
    fun `get should return null if client does not exist`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        assertNull(lokksmith.get("key"))
        assertTrue(snapshotStore.observeCalls.isEmpty(), "SnapshotStore.observe() was called")
    }

    @Test
    fun `getOrCreate should return existing client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
        )
        snapshotStore.setCalls.clear()

        val client =
            lokksmith.getOrCreate(key = key.value, options = Client.Options(leewaySeconds = 30)) {
                id = "clientId"
                discoveryUrl = "https://example.com/.well-known/openid-configuration"
            }
        assertEquals("clientId".asId(), client.id)
        assertEquals(mockMetadata, client.metadata)
        assertEquals(Client.Options(leewaySeconds = 30), client.options)

        assertEquals(1, snapshotStore.existsCalls.size)
        assertContains(
            snapshotStore.existsCalls,
            ExistsCall(key),
            "SnapshotStore.exists() not called",
        )
        assertEquals(1, snapshotStore.observeCalls.size)
        assertContains(
            snapshotStore.observeCalls,
            ObserveCall(key),
            "SnapshotStore.observe() not called",
        )
        assertTrue(snapshotStore.setCalls.isEmpty(), "SnapshotStore.set() was called")
    }

    @Test
    fun `getOrCreate should create new client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        val client =
            lokksmith.getOrCreate(key = key.value, options = Client.Options(leewaySeconds = 30)) {
                id = "clientId"
                discoveryUrl = "https://example.com/.well-known/openid-configuration"
            }
        assertEquals("clientId".asId(), client.id)
        assertEquals(mockMetadata, client.metadata)
        assertEquals(Client.Options(leewaySeconds = 30), client.options)

        assertEquals(2, snapshotStore.existsCalls.size)
        assertEquals(
            ExistsCall(key),
            snapshotStore.existsCalls.last(),
            "SnapshotStore.exists() not called",
        )
        assertEquals(1, snapshotStore.setCalls.size)
        assertContains(
            snapshotStore.setCalls,
            SetCall(
                key = key,
                snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
            ),
            "SnapshotStore.set() not called",
        )
        assertEquals(1, snapshotStore.observeCalls.size)
        assertContains(
            snapshotStore.observeCalls,
            ObserveCall(key),
            "SnapshotStore.observe() not called",
        )
    }

    @Test
    fun `exists should return true for existing client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
        )

        assertTrue(lokksmith.exists(key.value))

        assertEquals(1, snapshotStore.existsCalls.size)
        assertContains(
            snapshotStore.existsCalls,
            ExistsCall(key),
            "SnapshotStore.exists() not called",
        )
    }

    @Test
    fun `exists should return false for non-existing client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        assertFalse(lokksmith.exists(key.value))

        assertEquals(1, snapshotStore.existsCalls.size)
        assertContains(
            snapshotStore.existsCalls,
            ExistsCall(key),
            "SnapshotStore.exists() not called",
        )
    }

    @Test
    fun `create should create new client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        val client =
            lokksmith.create(
                key = key.value,
                options = Client.Options(leewaySeconds = 5, preemptiveRefreshSeconds = 30),
            ) {
                id = "clientId"
                discoveryUrl = "https://example.com/.well-known/openid-configuration"
            }
        assertEquals("clientId".asId(), client.id)
        assertEquals(mockMetadata, client.metadata)

        assertEquals(1, snapshotStore.existsCalls.size)
        assertContains(
            snapshotStore.existsCalls,
            ExistsCall(key),
            "SnapshotStore.exists() not called",
        )
        assertEquals(1, snapshotStore.setCalls.size)
        assertContains(
            snapshotStore.setCalls,
            SetCall(
                key = key,
                snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
            ),
            "SnapshotStore.set() not called",
        )
        assertEquals(1, snapshotStore.observeCalls.size)
        assertContains(
            snapshotStore.observeCalls,
            ObserveCall(key),
            "SnapshotStore.observe() not called",
        )
    }

    @Test
    fun `create should throw exception for existing client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
        )

        assertFailsWith<ClientAlreadyExistsException> {
            lokksmith.create(key.value) {
                id = "clientId"
                discoveryUrl = "https://example.com/.well-known/openid-configuration"
            }
        }

        assertContains(
            snapshotStore.existsCalls,
            ExistsCall(key),
            "SnapshotStore.exists() not called",
        )
    }

    @Test
    fun `delete should delete existing client`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        snapshotStore.set(
            key = key,
            snapshot = Snapshot(key = key, id = "clientId".asId(), metadata = mockMetadata),
        )

        assertTrue(lokksmith.delete(key.value))
        assertTrue((snapshotStore.subject.persistence as PersistenceFake).data.first().isEmpty())

        assertContains(
            snapshotStore.deleteCalls,
            DeleteCall(key),
            "SnapshotStore.delete() not called",
        )
    }

    @Test
    fun `delete should return false when client does not exist`() = runTest {
        val (lokksmith, snapshotStore) = createTestLokksmith()

        val key = "key".asKey()
        assertFalse(lokksmith.delete(key.value))

        assertContains(
            snapshotStore.deleteCalls,
            DeleteCall(key),
            "SnapshotStore.delete() not called",
        )
    }

    @Test
    fun `dispose should dispose instance`() = runTest {
        val (lokksmith) = createTestLokksmith()

        lokksmith.dispose()

        assertFalse(lokksmith.container.coroutineScope.isActive)
    }
}

internal data class TestContainer(
    override val coroutineScope: CoroutineScope,
    override val httpClient: HttpClient =
        createHttpClient(engine = MockEngine { respondBadRequest() }),
    override val snapshotStore: SnapshotStoreSpy =
        SnapshotStoreSpy(SnapshotStoreImpl(persistence = PersistenceFake(), serializer = Json)),
    override val metadataDiscoveryRequest: MetadataDiscoveryRequest =
        MetadataDiscoveryRequestImpl(httpClient),
    override val clientProviderFactory: () -> InternalClient.Provider = {
        ClientImpl.DefaultProvider(
            httpClient = httpClient,
            serializer = Json,
            instantProvider = { TEST_INSTANT },
        )
    },
) : Container {

    override val serializer = Json
}

internal val mockMetadata =
    Client.Metadata(
        issuer = "issuer",
        authorizationEndpoint = "https://example.com/authorizationEndpoint",
        tokenEndpoint = "https://example.com/tokenEndpoint",
        endSessionEndpoint = "https://example.com/endSessionEndpoint",
    )

internal fun TestScope.createTestLokksmith(
    onTestContainerCreated: TestScope.(TestContainer) -> TestContainer = { it }
): Pair<Lokksmith, SnapshotStoreSpy> {
    val container =
        TestContainer(
            coroutineScope = backgroundScope,
            metadataDiscoveryRequest = MetadataDiscoveryRequestFake { mockMetadata },
        )

    return onTestContainerCreated(container).let { container ->
        Lokksmith(container) to container.snapshotStore
    }
}
