package dev.lokksmith.demo

import kotlinx.browser.localStorage

actual class ClientPersistence actual constructor() {

    actual fun save(client: PersistedClient) {
        localStorage.setItem(KEY_CLIENT_ID, client.clientId)
        localStorage.setItem(KEY_DISCOVERY_URL, client.discoveryUrl)
    }

    actual fun load(): PersistedClient? {
        val clientId = localStorage.getItem(KEY_CLIENT_ID) ?: return null
        val discoveryUrl = localStorage.getItem(KEY_DISCOVERY_URL) ?: return null
        return PersistedClient(clientId = clientId, discoveryUrl = discoveryUrl)
    }

    private companion object {
        const val KEY_CLIENT_ID = "demo.clientId"
        const val KEY_DISCOVERY_URL = "demo.discoveryUrl"
    }
}
