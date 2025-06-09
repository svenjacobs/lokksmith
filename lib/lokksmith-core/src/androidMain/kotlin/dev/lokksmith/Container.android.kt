package dev.lokksmith

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal actual class PlatformContext(
    val context: Context,
)

internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> = createDataStore(fileName) { name ->
    platformContext.context.filesDir.resolve(name).absolutePath
}
