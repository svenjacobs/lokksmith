/*
 * Copyright 2026 Sven Jacobs
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
package dev.lokksmith.crypto

import dev.lokksmith.PlatformContext
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64
import kotlinx.browser.localStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Web [KeyEnvelope]. The key is a random AES-256 key kept in the browser's `localStorage`.
 *
 * A key in `localStorage` is obfuscation, not strong protection. Any script on the same origin can
 * read it, so this does not defend against XSS.
 */
internal actual class KeyEnvelope
actual constructor(
    platformContext: PlatformContext,
    alias: String,
) {

    private val provider = CryptographyProvider.Default
    private val random = CryptographyRandom.Default
    private val storageKey = "lokksmith.kek.$alias"

    private val mutex = Mutex()
    private var cipher: IvAuthenticatedCipher? = null

    actual suspend fun encrypt(dek: ByteArray): ByteArray =
        cipher(createIfMissing = true)!!.encrypt(dek)

    actual suspend fun decrypt(wrapped: ByteArray): ByteArray? =
        cipher(createIfMissing = false)?.let { runCatching { it.decrypt(wrapped) }.getOrNull() }

    private suspend fun cipher(createIfMissing: Boolean): IvAuthenticatedCipher? {
        cipher?.let {
            return it
        }
        return mutex.withLock {
            cipher
                ?: run {
                    val kek = loadKek() ?: if (createIfMissing) createKek() else return@run null
                    buildCipher(kek).also { cipher = it }
                }
        }
    }

    private suspend fun buildCipher(kek: ByteArray): IvAuthenticatedCipher =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, kek).cipher()

    /** Reads the KEK from `localStorage`, or null if it is missing or not valid Base64. */
    private fun loadKek(): ByteArray? =
        localStorage.getItem(storageKey)?.let { runCatching { Base64.decode(it) }.getOrNull() }

    private fun createKek(): ByteArray {
        val kek = random.nextBytes(KEK_SIZE_BYTES)
        localStorage.setItem(storageKey, Base64.encode(kek))
        return kek
    }

    private companion object {
        const val KEK_SIZE_BYTES = 32 // AES-256
    }
}
