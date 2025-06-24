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
package dev.lokksmith.client.request.flow

import dev.lokksmith.client.request.parameter.Parameter.KNOWN_PARAMETERS
import io.ktor.http.URLBuilder

internal fun URLBuilder.addOptionalParameter(key: String, value: Any?) {
    if (value == null) return
    parameters[key] = value.toString()
}

internal fun URLBuilder.addOptionalParameter(key: String, values: Collection<*>) {
    if (values.isEmpty()) return
    parameters[key] = values.joinToString(" ")
}

internal fun URLBuilder.addAdditionalParameters(params: Map<String, String>) {
    params.forEach { (key, value) ->
        if (KNOWN_PARAMETERS.contains(key.lowercase())) {
            throw IllegalArgumentException("Parameter \"$key\" is a known OAuth/OIDC parameter")
        }

        if (parameters.contains(key)) {
            throw IllegalArgumentException("Parameter \"$key\" already exists")
        }

        parameters[key] = value
    }
}
