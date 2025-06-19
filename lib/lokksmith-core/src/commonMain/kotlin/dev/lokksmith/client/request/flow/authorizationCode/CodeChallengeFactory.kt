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
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Factory for generating PKCE (Proof Key for Code Exchange) code challenges.
 *
 * This class creates a code challenge from a code verifier using the specified cryptographic hash
 * algorithm, as required by the OAuth 2.0 PKCE extension.
 *
 * @property algorithmId The cryptographic algorithm identifier used for hashing the code verifier.
 */
internal class CodeChallengeFactory(private val algorithmId: CryptographyAlgorithmId<Digest>) {

    /**
     * Generates a Base64 URL-safe encoded code challenge for the given [codeVerifier].
     *
     * The code challenge is produced by hashing the [codeVerifier] using the configured algorithm
     * (e.g., SHA-256), then encoding the result using Base64 URL-safe encoding without padding.
     *
     * @param codeVerifier The original code verifier string.
     * @return The code challenge as a Base64 URL-safe encoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend operator fun invoke(codeVerifier: String): String {
        val hasher = CryptographyProvider.Companion.Default.get(algorithmId).hasher()
        val hash = hasher.hash(codeVerifier.encodeToByteArray())
        return Base64.Default.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(hash)
    }

    companion object {

        /**
         * Creates a [CodeChallengeFactory] for the specified [CodeChallengeMethod].
         *
         * @param method The PKCE code challenge method (e.g., SHA-256).
         * @return A [CodeChallengeFactory] configured for the given method.
         */
        fun forMethod(method: CodeChallengeMethod): CodeChallengeFactory {
            val id =
                when (method) {
                    CodeChallengeMethod.SHA256 -> SHA256
                }
            return CodeChallengeFactory(id)
        }
    }
}
