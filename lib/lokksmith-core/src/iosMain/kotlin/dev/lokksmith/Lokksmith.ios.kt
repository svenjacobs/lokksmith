package dev.lokksmith

import dev.lokksmith.Lokksmith.Options
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

public fun createLokksmith(
    options: Options = Options(),
): Lokksmith = Lokksmith(
    platformContext = PlatformContext,
    options = options,
)

internal actual val platformHttpClientEngine: HttpClientEngine
    get() = Darwin.create()
