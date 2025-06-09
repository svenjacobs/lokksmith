package dev.lokksmith.client.request.token

import dev.lokksmith.LokksmithException

public class TokenValidationException internal constructor(cause: Throwable? = null) :
    LokksmithException(cause = cause)