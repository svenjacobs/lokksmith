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
import dev.lokksmith.ensureSecureDirectory
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher
import dev.whyoleg.cryptography.random.CryptographyRandom
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desktop [KeyEnvelope]. The key is a random AES-256 key saved as a file in the user-private data
 * directory. There is no hardware isolation; the key is only as safe as the folder's file
 * permissions.
 */
internal actual class KeyEnvelope
actual constructor(
    platformContext: PlatformContext,
    alias: String,
) {

    private val provider = CryptographyProvider.Default
    private val random = CryptographyRandom.Default
    private val dataDirectory: File = platformContext.dataDirectory
    private val kekFile: File = dataDirectory.resolve("$alias.kek")

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
                    val kekBytes =
                        loadKek() ?: if (createIfMissing) createKek() else return@run null
                    buildCipher(kekBytes).also { cipher = it }
                }
        }
    }

    private suspend fun buildCipher(kek: ByteArray): IvAuthenticatedCipher =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, kek).cipher()

    /** Reads the persisted KEK, or null if the file does not exist. A read error propagates. */
    private fun loadKek(): ByteArray? = if (kekFile.exists()) kekFile.readBytes() else null

    private fun createKek(): ByteArray {
        ensureSecureDirectory(dataDirectory.toPath())
        val kek = random.nextBytes(KEK_SIZE_BYTES)
        // Create the file owner-only where the platform supports POSIX permissions.
        runCatching {
            Files.createFile(
                kekFile.toPath(),
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    )
                ),
            )
        }
        kekFile.writeBytes(kek)
        return kek
    }

    private companion object {
        const val KEK_SIZE_BYTES = 32 // AES-256
    }
}
