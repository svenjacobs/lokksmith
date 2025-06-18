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

import io.ktor.http.URLBuilder

/**
 * Strategy interface for handling and verifying OpenID Connect parameters such as "state" and "nonce".
 *
 * Implementations of this interface define how these parameters are added to authorization requests
 * and how their values are verified in responses, as required by the OpenID Connect specification.
 *
 * Use [Default] to add and verify a specific key-value parameter (e.g., "state" or "nonce"), or [None]
 * if the parameter is not required or verification should always succeed.
 */
internal sealed interface VerifierStrategy {

    /**
     * Adds the relevant parameter (e.g., "state" or "nonce") to the given [URLBuilder].
     *
     * TODO: Use case for context parameters :)
     *
     * @param builder The [URLBuilder] to which the parameter will be added.
     */
    fun addParameter(builder: URLBuilder)

    /**
     * Verifies the actual value (from the response) against the expected value.
     *
     * @param actualValue The value to verify, typically extracted from the response URI.
     * @return `true` if the value matches the expected value, `false` otherwise.
     */
    fun verify(actualValue: String?): Boolean

    /**
     * Default implementation for adding and verifying a specific key-value parameter.
     *
     * @property key The parameter key (e.g., "state" or "nonce").
     * @property value The expected parameter value.
     */
    class Default(
        val key: String,
        val value: String,
    ) : VerifierStrategy {

        override fun addParameter(builder: URLBuilder) {
            builder.parameters[key] = value
        }

        override fun verify(actualValue: String?) =
            actualValue == value
    }

    /**
     * Implementation for cases where no parameter is required or verification should always succeed.
     */
    object None : VerifierStrategy {

        override fun addParameter(builder: URLBuilder) {
        }

        override fun verify(actualValue: String?) = true
    }

    companion object {

        /**
         * Factory method to create a [VerifierStrategy] for a given key and value.
         *
         * @param key The parameter key (e.g., "state" or "nonce").
         * @param value The parameter value, or `null` if the parameter is not required.
         * @return A [VerifierStrategy] instance appropriate for the given parameters.
         */
        fun forKeyValue(key: String, value: String?): VerifierStrategy =
            if (value == null) None else Default(key, value)
    }
}
