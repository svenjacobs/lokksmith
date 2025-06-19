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

import dev.whyoleg.cryptography.random.CryptographyRandom

internal class Random(private val random: CryptographyRandom = CryptographyRandom.Default) {

    fun randomAsciiString(length: Int): String {
        require(length > 0) { "length must be greater than 0" }
        return randomString(length, asciiChars)
    }

    /**
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7636.html#section-4.1">PKCE 4.1 "Client
     *   Creates a Code Verifier"</a>
     */
    fun randomCodeVerifier(length: Int): String {
        require(length in 43..128) { "length must be between 43 and 128" }
        return randomString(length, codeVerifierChars)
    }

    private fun randomString(length: Int, chars: List<Char>) =
        (0 until length).map { chars[random.nextInt(chars.size)] }.joinToString("")

    internal companion object {
        internal val asciiChars = ('0'..'9') + ('A'..'Z') + ('a'..'z')
        internal val codeVerifierChars = asciiChars + '-' + '.' + '_' + '~'
    }
}
