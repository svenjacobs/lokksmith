package dev.lokksmith.demo

actual class ClientPersistence actual constructor() {

    actual fun save(client: PersistedClient) {}

    actual fun load(): PersistedClient? = null
}
