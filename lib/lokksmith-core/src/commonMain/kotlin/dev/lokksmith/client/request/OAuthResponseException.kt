/*
 * Copyright 2025 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.lokksmith.client.request

import dev.lokksmith.LokksmithException

public class OAuthResponseException
internal constructor(
    public val error: OAuthError,
    public val errorDescription: String? = null,
    public val errorUri: String? = null,
    public val statusCode: Int? = null,
) :
    LokksmithException(
        message =
            toString(
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

    override fun toString(): String =
        toString(
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
): String =
    StringBuilder("OAuthResponseException(error=\"${error.code}\"")
        .apply {
            if (!errorDescription.isNullOrBlank())
                append(", errorDescription=\"$errorDescription\"")
            if (!errorUri.isNullOrBlank()) append(", errorUri=\"$errorUri\"")
            if (statusCode != null) append(", statusCode=$statusCode")
        }
        .append(")")
        .toString()
