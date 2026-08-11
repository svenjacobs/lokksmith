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
package dev.lokksmith.client.snapshot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lokksmith.client.Key
import dev.lokksmith.client.snapshot.InternalSnapshotStore.Persistence
import dev.lokksmith.crypto.KeyEnvelope
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Encrypts and decrypts a serialized [Snapshot]. */
internal interface SnapshotCipher {

    suspend fun encrypt(plaintext: String): String

    suspend fun decrypt(value: String): String
}

/**
 * Pass-through [SnapshotCipher] used when encryption is disabled: values are written as-is.
 *
 * [decrypt] always fails so that [EncryptingPersistence] falls back to its plaintext-JSON handling.
 * A valid snapshot is then returned unchanged, while any value left over from an encrypted run (not
 * plaintext JSON) is treated as absent rather than surfaced as garbage.
 */
internal object PlaintextSnapshotCipher : SnapshotCipher {

    override suspend fun encrypt(plaintext: String): String = plaintext

    override suspend fun decrypt(value: String): String =
        throw UnsupportedOperationException("encryption is disabled")
}

/**
 * AES-GCM cipher for snapshots. The data-encryption key (DEK) comes from [dekProvider] and is
 * decoded once and cached. Every encryption uses a fresh random IV, so the stored value is
 * `Base64(IV || ciphertext || tag)`. The GCM tag makes [decrypt] fail on tampering or a wrong key.
 * [EncryptingPersistence] relies on that failure to tell encrypted data apart from legacy
 * plaintext.
 */
internal class AesGcmSnapshotCipher(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    private val dekProvider: suspend () -> ByteArray,
) : SnapshotCipher {

    private val mutex = Mutex()
    private var key: AES.GCM.Key? = null

    private suspend fun key(): AES.GCM.Key =
        key
            ?: mutex.withLock {
                key
                    ?: provider
                        .get(AES.GCM)
                        .keyDecoder()
                        .decodeFromByteArray(AES.Key.Format.RAW, dekProvider())
                        .also { key = it }
            }

    override suspend fun encrypt(plaintext: String): String =
        Base64.encode(key().cipher().encrypt(plaintext.encodeToByteArray()))

    override suspend fun decrypt(value: String): String =
        key().cipher().decrypt(Base64.decode(value)).decodeToString()
}

/**
 * The store-wide data-encryption key (DEK), via envelope encryption.
 *
 * The DEK is generated once, wrapped with the platform key via [envelope], and the wrapped copy
 * kept in [wrappedStore]. Later runs load and unwrap it. If unwrapping fails, for example because
 * the platform key is gone, a new DEK is generated. Snapshots under the old one become unreadable
 * and are treated as absent, requiring the user to sign in again.
 */
internal class EnvelopeDekProvider(
    private val envelope: KeyEnvelope,
    private val wrappedStore: DataStore<Preferences>,
    private val random: CryptographyRandom = CryptographyRandom.Default,
) {

    private val mutex = Mutex()
    private var dek: ByteArray? = null

    suspend fun getOrCreateDek(): ByteArray =
        dek ?: mutex.withLock { dek ?: load().also { dek = it } }

    private suspend fun load(): ByteArray {
        val stored = wrappedStore.data.first()[WrappedDekKey]
        val wrapped = stored?.let { runCatching { Base64.decode(it) }.getOrNull() }
        if (wrapped != null) {
            // A null result means the KEK is absent or the wrapped DEK is unrecoverable, so it is
            // regenerated below. A thrown error (secure store transiently unavailable) propagates
            // instead, so a still-valid wrapped DEK is never overwritten on a transient failure.
            envelope.decrypt(wrapped)?.let {
                return it
            }
        }
        val newDek = random.nextBytes(DEK_SIZE_BYTES)
        wrappedStore.edit { it[WrappedDekKey] = Base64.encode(envelope.encrypt(newDek)) }
        return newDek
    }

    private companion object {
        val WrappedDekKey = stringPreferencesKey("lokksmith.snapshot.wrappedDek")
        const val DEK_SIZE_BYTES = 32 // AES-256
    }
}

/**
 * A [Persistence] that encrypts on write and decrypts on read via [cipher].
 *
 * A read has three outcomes:
 * - Decryption succeeds: the plaintext is returned.
 * - Decryption fails but the value looks like legacy plaintext JSON: it is returned as-is and
 *   re-encrypted on the next write.
 * - Decryption fails and the value is not plaintext, for example after key loss: it is treated as
 *   absent rather than surfaced as an error.
 */
internal class EncryptingPersistence(
    private val delegate: Persistence,
    private val cipher: SnapshotCipher,
) : Persistence {

    override val data: Flow<Map<String, String>>
        get() =
            delegate.data.map { stored ->
                val result = LinkedHashMap<String, String>(stored.size)
                for ((key, value) in stored) readable(value)?.let { result[key] = it }
                result
            }

    override fun observe(key: Key): Flow<String?> =
        delegate.observe(key).map { value -> value?.let { readable(it) } }

    override suspend fun get(key: Key): String? = delegate.get(key)?.let { readable(it) }

    override suspend fun set(key: Key, snapshot: String) {
        delegate.set(key, cipher.encrypt(snapshot))
    }

    // Deletion works on physical presence, unlike [contains], which reflects readability: an entry
    // that can no longer be decrypted must still be removable.
    override suspend fun delete(key: Key): Boolean = delegate.delete(key)

    override suspend fun contains(key: Key): Boolean = get(key) != null

    private suspend fun readable(stored: String): String? =
        runCatching { cipher.decrypt(stored) }
            .getOrElse { if (stored.trimStart().startsWith('{')) stored else null }
}
