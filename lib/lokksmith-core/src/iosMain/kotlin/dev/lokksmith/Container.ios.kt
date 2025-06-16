package dev.lokksmith

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal actual object PlatformContext

internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> = createDataStore(fileName) { name ->
    // https://developer.android.com/kotlin/multiplatform/datastore#ios
    TODO()
}

internal actual val platformUserAgentSuffix = "iOS"
