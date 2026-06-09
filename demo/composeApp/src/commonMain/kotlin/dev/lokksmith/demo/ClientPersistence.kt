package dev.lokksmith.demo

/**
 * Persists the demo's last selected client (its id and discovery URL) so it can be restored on
 * startup.
 *
 * This only does something on the Web, where a successful auth flow performs a full-page redirect
 * that restarts the app and clears all in-memory state. Restoring the client on startup lets the
 * freshly issued tokens be displayed without re-creating the client. On the other platforms the app
 * keeps its state across the auth flow, so the implementation is a no-op.
 */
expect class ClientPersistence() {

    fun save(client: PersistedClient)

    fun load(): PersistedClient?
}

data class PersistedClient(val clientId: String, val discoveryUrl: String)
