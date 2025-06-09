package dev.lokksmith

import android.content.Context
import dev.lokksmith.Lokksmith.Options
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

public fun createLokksmith(
    context: Context,
    options: Options = Options(),
): Lokksmith = Lokksmith(
    platformContext = PlatformContext(context),
    options = options,
)

internal actual val platformHttpClientEngine: HttpClientEngine
    get() = OkHttp.create()
