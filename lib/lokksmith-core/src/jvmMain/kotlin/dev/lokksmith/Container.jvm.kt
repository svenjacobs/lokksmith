package dev.lokksmith

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.io.files.SystemPathSeparator

internal actual data class PlatformContext(
    internal val persistenceFilePath: String,
)

internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> = createDataStore(fileName) {
    "${platformContext.persistenceFilePath}$SystemPathSeparator$fileName"
}
