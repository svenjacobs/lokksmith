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
package dev.lokksmith.client.request.flow.authorizationCode

import dev.lokksmith.client.request.parameter.CodeChallengeMethod
import dev.lokksmith.client.request.parameter.Parameter
import io.ktor.http.URLBuilder

/**
 * Strategy interface for handling PKCE (Proof Key for Code Exchange) code challenge parameters.
 *
 * Implementations of this interface define how code challenge parameters are added to an
 * authorization request URL as well as how to verify them.
 *
 * Use [Default] to add PKCE parameters, or [None] if PKCE is not used.
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc7636">Proof Key for Code Exchange by OAuth Public Clients</a>
 */
internal sealed interface CodeChallengeStrategy {

    /**
     * Adds PKCE code challenge parameters to the given [URLBuilder].
     *
     * TODO: Use case for context parameters :)
     *
     * @param builder The [URLBuilder] to which PKCE parameters will be added.
     */
    fun addParameters(builder: URLBuilder)

    /**
     * Default implementation that adds the code challenge and method parameters.
     *
     * @property method The PKCE code challenge method (e.g., SHA-256).
     * @property codeChallenge The generated code challenge string.
     */
    class Default(
        val method: CodeChallengeMethod,
        val codeChallenge: String,
    ) : CodeChallengeStrategy {

        override fun addParameters(builder: URLBuilder) {
            builder.parameters[Parameter.CODE_CHALLENGE_METHOD] = method.toString()
            builder.encodedParameters[Parameter.CODE_CHALLENGE] = codeChallenge
        }
    }

    /**
     * Implementation for cases where no PKCE code challenge is required.
     */
    object None : CodeChallengeStrategy {

        override fun addParameters(builder: URLBuilder) {
        }
    }

    companion object {

        /**
         * Factory method to create a [CodeChallengeStrategy] based on the provided method and verifier.
         *
         * @param method The PKCE code challenge method, or null if PKCE is not used.
         * @param codeVerifier The code verifier string, or null if PKCE is not used.
         * @return A [CodeChallengeStrategy] instance appropriate for the given parameters.
         */
        suspend fun create(
            method: CodeChallengeMethod?,
            codeVerifier: String?,
        ): CodeChallengeStrategy = when {
            method == null || codeVerifier == null -> None
            else -> {
                val codeChallenge = CodeChallengeFactory.forMethod(method)(codeVerifier)
                Default(method, codeChallenge)
            }
        }
    }
}
