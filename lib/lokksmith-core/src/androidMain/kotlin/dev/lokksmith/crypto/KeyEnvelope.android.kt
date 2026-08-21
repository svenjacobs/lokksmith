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
import java.security.UnrecoverableEntryException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
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
        val cipher =
            try {
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, getKek() ?: createKek())
                }
            } catch (e: KeyPermanentlyInvalidatedException) {
                // The stored KEK exists but is no longer usable; replace it and retry. A Cipher
                // that threw from init() is not reliably reusable across providers, so start
                // over with a fresh instance rather than re-initializing this one.
                keyStore.deleteEntry(keyAlias)
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, createKek())
                }
            }
        val ciphertext = cipher.doFinal(dek)
        return cipher.iv + ciphertext
    }

    /**
     * Unwraps [wrapped], returning null only when the result is definitively unrecoverable.
     *
     * Everything else propagates. A keystore operation can fail while the KEK is perfectly intact —
     * the keystore daemon restarting, direct-boot, StrongBox under load — and reporting that as
     * unrecoverable would make the caller generate a new DEK and overwrite the wrapped one, which
     * destroys every existing snapshot irreversibly.
     */
    actual suspend fun decrypt(wrapped: ByteArray): ByteArray? {
        val kek = getKek() ?: return null
        // Too short to hold an IV plus a tag, so it cannot be a value this class produced.
        if (wrapped.size <= GCM_IV_LENGTH) return null
        return try {
            val iv = wrapped.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = wrapped.copyOfRange(GCM_IV_LENGTH, wrapped.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: KeyPermanentlyInvalidatedException) {
            null // The key is gone: lock screen or biometric enrollment changed.
        } catch (e: BadPaddingException) {
            // Wrong key, or the wrapped DEK was tampered with. Catching the supertype of
            // AEADBadTagException on purpose: a GCM tag mismatch does not surface as the more
            // specific type on every provider, and mistaking that for a transient fault would leave
            // the store permanently unreadable with no path to recovery.
            null
        } catch (e: IllegalBlockSizeException) {
            // Ambiguous: on some API levels this is how an invalidated key surfaces, but it is also
            // thrown for transient keystore faults. Only the former is unrecoverable.
            if (e.cause is KeyPermanentlyInvalidatedException) null else throw e
        }
    }

    /**
     * Returns the stored KEK, or null if the alias holds no usable secret key. A keystore error
     * throws.
     *
     * [UnrecoverableEntryException] (and its subclass `UnrecoverableKeyException`) counts as absent
     * rather than as an error: it is how some API levels report an entry whose key material is gone
     * or corrupt, which no retry can fix. Propagating it as if it were transient would fail every
     * read forever, with no remedy but clearing the application's data by hand.
     */
    private fun getKek(): SecretKey? =
        try {
            (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: UnrecoverableEntryException) {
            null
        }

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
