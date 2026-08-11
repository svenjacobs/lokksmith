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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import dev.lokksmith.PlatformContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android [KeyEnvelope]. The key is a non-exportable AES-256 key in the Android Keystore,
 * hardware-backed on devices that support it.
 */
internal actual class KeyEnvelope
actual constructor(
    platformContext: PlatformContext,
    alias: String,
) {

    private val keyAlias = "lokksmith.kek.$alias"

    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    actual suspend fun encrypt(dek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getKek() ?: createKek())
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The stored KEK exists but is no longer usable; replace it and retry.
            keyStore.deleteEntry(keyAlias)
            cipher.init(Cipher.ENCRYPT_MODE, createKek())
        }
        val ciphertext = cipher.doFinal(dek)
        return cipher.iv + ciphertext
    }

    actual suspend fun decrypt(wrapped: ByteArray): ByteArray? {
        // A thrown keystore error while reading the KEK propagates (transient); only an absent key
        // returns null so the caller regenerates.
        val kek = getKek() ?: return null
        return runCatching {
                val iv = wrapped.copyOfRange(0, GCM_IV_LENGTH)
                val ciphertext = wrapped.copyOfRange(GCM_IV_LENGTH, wrapped.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                cipher.doFinal(ciphertext)
            }
            .getOrNull()
    }

    /**
     * Returns the stored KEK, or null if the alias holds no secret key. A keystore error throws.
     */
    private fun getKek(): SecretKey? =
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun createKek(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
