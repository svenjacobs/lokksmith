package dev.lokksmith.client.request.token

import dev.lokksmith.LokksmithException

public open class TokenValidationException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : LokksmithException(
    message = message,
    cause = cause
)
