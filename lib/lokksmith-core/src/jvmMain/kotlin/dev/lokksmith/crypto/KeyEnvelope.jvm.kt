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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
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

    // Volatile: the fast path in cipher() reads this without holding the mutex, which would
    // otherwise be allowed to observe a published reference whose contents are not yet visible.
    @Volatile private var cipher: IvAuthenticatedCipher? = null

    actual suspend fun encrypt(dek: ByteArray): ByteArray =
        cipher(createIfMissing = true)!!.encrypt(dek)

    actual suspend fun decrypt(wrapped: ByteArray): ByteArray? =
        cipher(createIfMissing = false)?.let {
            try {
                it.decrypt(wrapped)
            } catch (e: CancellationException) {
                // Not a decryption failure. Reporting it as one would make the caller
                // regenerate the DEK and overwrite the wrapped copy, losing every snapshot.
                throw e
            } catch (e: Throwable) {
                // Wrong key or tampered ciphertext. Unlike the Android envelope, this is pure
                // software over bytes already in memory, so a failure here cannot be transient.
                null
            }
        }

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

    /**
     * Reads the persisted KEK, or null if it is absent or not a whole key. A read error propagates.
     *
     * A file of the wrong length counts as absent so that a new key is generated: snapshots under
     * the old one are lost, but the store recovers. Returning the truncated bytes instead would
     * fail every later operation with an error indistinguishable from a transient one, which never
     * clears and leaves no remedy but deleting the application's data by hand.
     */
    private fun loadKek(): ByteArray? =
        if (kekFile.exists()) kekFile.readBytes().takeIf { it.size == KEK_SIZE_BYTES } else null

    private fun createKek(): ByteArray {
        ensureSecureDirectory(dataDirectory.toPath())
        deleteStaleTemporaryFiles()
        val kek = random.nextBytes(KEK_SIZE_BYTES)
        // Write to a temporary file and move it into place, so the key file is never observed in a
        // partially written state. Files.createTempFile restricts the temporary file to the owner
        // where POSIX permissions apply, so the key is not briefly world-readable.
        val temporaryPath = Files.createTempFile(dataDirectory.toPath(), KEK_FILE_PREFIX, ".tmp")
        try {
            Files.write(temporaryPath, kek)
            moveIntoPlace(from = temporaryPath, to = kekFile.toPath())
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
        restrictToOwner(kekFile.toPath())
        return kek
    }

    private fun moveIntoPlace(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Deletes temporary files an earlier interrupted [createKek] may have left behind.
     *
     * Such a file holds a raw key, though with the same owner-only permissions and in the same
     * directory as the key file itself, so this bounds accumulation rather than closing an
     * exposure.
     */
    private fun deleteStaleTemporaryFiles() {
        dataDirectory
            .listFiles { file ->
                file.name.startsWith(KEK_FILE_PREFIX) && file.name.endsWith(".tmp")
            }
            ?.forEach { it.delete() }
    }

    /**
     * Restricts [path] to the owner where the platform supports POSIX permissions.
     *
     * Belt-and-braces: [Files.createTempFile] already creates the temporary file owner-only where
     * POSIX applies, and moving it into place carries those permissions over, replacing whatever a
     * pre-existing key file had. This makes the guarantee explicit and independent of that. On
     * platforms without POSIX support, notably Windows, the parent directory's access control from
     * [ensureSecureDirectory] is inherited instead.
     */
    private fun restrictToOwner(path: Path) {
        if (!path.fileSystem.supportedFileAttributeViews().contains("posix")) return
        Files.setPosixFilePermissions(
            path,
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    }

    private companion object {
        const val KEK_SIZE_BYTES = 32 // AES-256
        const val KEK_FILE_PREFIX = "lokksmith-kek"
    }
}
