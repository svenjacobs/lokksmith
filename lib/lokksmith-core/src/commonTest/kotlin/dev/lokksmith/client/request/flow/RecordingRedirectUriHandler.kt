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

/**
 * Test double that records calls and substitutes a fixed URI on resolve, mimicking how the desktop
 * loopback handler will swap the consumer-supplied URI for a bound `127.0.0.1` URL.
 */
internal class RecordingRedirectUriHandler(private val resolved: String) : RedirectUriHandler {

    val resolveCalls = mutableListOf<Pair<String, String>>()
    val releaseCalls = mutableListOf<String>()

    override suspend fun resolve(requestRedirectUri: String, state: String): String {
        resolveCalls += requestRedirectUri to state
        return resolved
    }

    override suspend fun release(state: String) {
        releaseCalls += state
    }
}
