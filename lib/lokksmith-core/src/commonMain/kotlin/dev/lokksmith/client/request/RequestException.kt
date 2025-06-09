package dev.lokksmith.client.request

import dev.lokksmith.LokksmithException

public class RequestException internal constructor(
    cause: Throwable,
    public val reason: Reason? = null,
) : LokksmithException(cause = cause) {

    public enum class Reason {
        UrlParsing,
        HttpError,
    }
}