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
package dev.lokksmith.internal

import dev.lokksmith.client.request.parameter.Parameter
import io.ktor.http.Url

public fun getRedirectUri(requestUrl: String): Url {
    val url = Url(requestUrl)
    val redirectUri =
        checkNotNull(
            url.parameters[Parameter.REDIRECT_URI]
                ?: url.parameters[Parameter.POST_LOGOUT_REDIRECT_URI]
        ) {
            "Could not determine redirect URI"
        }
    return Url(redirectUri)
}

public fun getRedirectScheme(requestUrl: String): String {
    val redirectUri = getRedirectUri(requestUrl)
    return redirectUri.protocol.name
}
