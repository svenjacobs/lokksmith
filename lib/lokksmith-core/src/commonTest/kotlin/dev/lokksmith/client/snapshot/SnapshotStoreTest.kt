package dev.lokksmith.client.snapshot

import dev.lokksmith.client.Client
import dev.lokksmith.client.Key
import dev.lokksmith.client.asId
import dev.lokksmith.client.asKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotStoreTest {

    private lateinit var store: SnapshotStore
    private lateinit var persistence: PersistenceFake

    @BeforeTest
    fun before() {
        persistence = PersistenceFake()
        store = SnapshotStoreImpl(
            persistence = persistence,
            serializer = Json,
        )
    }

    @Test
    fun `observe() should observe key for changes`() = runTest {
        val key = "key".asKey()

        assertEquals(null, store.observe(key).firstOrNull())

        val snapshot = newSnapshot(key)
        store.set(key, snapshot)

        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `get() should get Snapshot for key`() = runTest {
        val key = "key".asKey()

        assertEquals(null, store.observe(key).firstOrNull())

        val snapshot = newSnapshot(key)
        persistence.memory.value = mutableMapOf(
            key.value to Json.encodeToString(snapshot),
        )

        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `getForState() should get Snapshot for state`() = runTest {
        val key = "key".asKey()
        val state = "Ly5GJLkj"

        assertEquals(null, store.getForState(state))

        val snapshot = newSnapshot(key, state)
        persistence.memory.value = mutableMapOf(
            key.value to Json.encodeToString(snapshot),
        )

        assertEquals(snapshot, store.getForState(state))
    }

    @Test
    fun `set() should set Snapshot for key`() = runTest {
        val key = "key".asKey()

        assertEquals(null, store.observe(key).firstOrNull())

        val snapshot = newSnapshot(key)
        store.set(key, snapshot)

        assertEquals(snapshot, store.observe(key).firstOrNull())
    }

    @Test
    fun `delete() should delete Snapshot for key`() = runTest {
        val key = "key".asKey()

        val snapshot = newSnapshot(key)
        store.set(key, snapshot)

        assertEquals(snapshot, store.observe(key).firstOrNull())

        store.delete(key)

        assertEquals(null, store.observe(key).firstOrNull())
    }

    @Test
    fun `exists() should check for existence`() = runTest {
        val key = "key".asKey()

        assertFalse(store.exists(key))

        val snapshot = newSnapshot(key)
        store.set(key, snapshot)

        assertTrue(store.exists(key))
    }
}

class PersistenceFake(
    initialData: Map<String, String> = emptyMap(),
) : InternalSnapshotStore.Persistence {

    val memory = MutableStateFlow(initialData)

    override val data: Flow<Map<String, String>> =
        memory.asStateFlow()

    override fun observe(key: Key): Flow<String?> =
        memory.map { it[key.value] }

    override suspend fun get(key: Key): String? =
        observe(key).firstOrNull()

    override suspend fun set(key: Key, snapshot: String) {
        memory.update { data ->
            data.toMutableMap().apply {
                set(key.value, snapshot)
            }
        }
    }

    override suspend fun delete(key: Key) {
        memory.update { data ->
            data.toMutableMap().apply {
                remove(key.value)
            }
        }
    }

    override suspend fun contains(key: Key): Boolean =
        memory.value.contains(key.value)
}

private fun newSnapshot(
    key: Key,
    state: String? = null,
): Snapshot {
    val ephemeralFlowState = state?.let {
        Snapshot.EphemeralAuthorizationCodeFlowState(
            state = state,
            redirectUri = "redirectUri",
            codeVerifier = null,
            responseUri = null,
        )
    }

    return Snapshot(
        key = key,
        id = key.value.asId(),
        metadata = Client.Metadata(
            issuer = "issuer",
            authorizationEndpoint = "authorizationEndpoint",
            tokenEndpoint = "tokenEndpoint",
        ),
        options = Client.Options(),
        ephemeralFlowState = ephemeralFlowState,
    )
}
