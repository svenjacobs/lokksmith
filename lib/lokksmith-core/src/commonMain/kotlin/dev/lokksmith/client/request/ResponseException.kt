package dev.lokksmith.client.request

import dev.lokksmith.LokksmithException

public open class ResponseException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
    public val reason: Reason? = null,
) : LokksmithException(message, cause) {

    public enum class Reason {
        UrlParsing,
        StateMismatch,
        InvalidResponse,
        HttpError,
    }
}