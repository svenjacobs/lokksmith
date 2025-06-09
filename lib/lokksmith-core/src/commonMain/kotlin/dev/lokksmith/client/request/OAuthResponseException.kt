package dev.lokksmith.client.request

import dev.lokksmith.LokksmithException

public class OAuthResponseException internal constructor(
    public val error: OAuthError,
    public val errorDescription: String? = null,
    public val errorUri: String? = null,
    public val statusCode: Int? = null,
) : LokksmithException(
    message = toString(
        error = error,
        errorDescription = errorDescription,
        errorUri = errorUri,
        statusCode = statusCode,
    )
) {

    internal constructor(
        error: String,
        errorDescription: String? = null,
        errorUri: String? = null,
        statusCode: Int? = null,
    ) : this(
        error = error.toOAuthError(),
        errorDescription = errorDescription,
        errorUri = errorUri,
        statusCode = statusCode,
    )

    override fun toString(): String = toString(
        error = error,
        errorDescription = errorDescription,
        errorUri = errorUri,
        statusCode = statusCode,
    )
}

private fun toString(
    error: OAuthError,
    errorDescription: String? = null,
    errorUri: String? = null,
    statusCode: Int? = null,
): String = StringBuilder("OAuthResponseException(error=\"${error.code}")
    .apply {
        if (!errorDescription.isNullOrBlank()) append(", errorDescription=\"$errorDescription\"")
        if (!errorUri.isNullOrBlank()) append(", errorUri=\"$errorUri\"")
        if (statusCode != null) append(", statusCode=$statusCode")
    }
    .append(")")
    .toString()
