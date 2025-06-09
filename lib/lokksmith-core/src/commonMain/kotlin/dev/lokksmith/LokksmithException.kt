package dev.lokksmith

public open class LokksmithException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)