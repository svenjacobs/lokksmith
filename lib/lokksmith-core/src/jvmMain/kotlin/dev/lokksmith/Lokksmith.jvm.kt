package dev.lokksmith

import dev.lokksmith.Lokksmith.Options
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * @param persistenceFilePath The path where persistence files are created.
 *                            Must not contain a file name.
 */
public fun createLokksmith(
    options: Options = Options(),
    persistenceFilePath: String,
): Lokksmith = Lokksmith(
    platformContext = PlatformContext(persistenceFilePath = persistenceFilePath),
    options = options,
)

internal actual val platformHttpClientEngine: HttpClientEngine
    get() = OkHttp.create()
