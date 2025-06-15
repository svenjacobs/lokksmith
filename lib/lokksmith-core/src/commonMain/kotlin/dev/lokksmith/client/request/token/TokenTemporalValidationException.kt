package dev.lokksmith.client.request.token

/**
 * Thrown when validation of a temporal claim in a token fails.
 *
 * This exception indicates that a time-based property such as `exp` (expiration),
 * `iat` (issued at), or `nbf` (not before) in a token is invalid or does not satisfy
 * the expected constraints during validation.
 *
 * @see dev.lokksmith.client.Client.Options.leewaySeconds
 */
public class TokenTemporalValidationException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : TokenValidationException(
    message = message,
    cause = cause,
)
