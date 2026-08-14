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
import dev.lokksmith.client.asKey
import dev.lokksmith.client.snapshot.InternalSnapshotStore.Persistence
import dev.lokksmith.crypto.KeyEnvelope
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Encrypts and decrypts a serialized [Snapshot].
 *
 * Every operation is bound to the entry the value is stored under. That identifier is the store's
 * key for the snapshot ([Key.value]) and is authenticated but not encrypted, so a ciphertext cannot
 * be moved to a different entry without failing to decrypt.
 */
internal interface SnapshotCipher {

    /**
     * Whether this cipher actually encrypts. When `false`, values are written and read as plaintext
     * JSON and callers must not treat an unreadable value as evidence of key loss or tampering.
     */
    val isEncrypting: Boolean

    suspend fun encrypt(entryId: String, plaintext: String): String

    /**
     * @throws UndecryptableSnapshotException if [value] cannot be decrypted under the current key.
     * @throws Exception if the key material itself could not be obtained, for example because the
     *   platform secure store is transiently unavailable. Callers must propagate this rather than
     *   treat the snapshot as absent.
     */
    suspend fun decrypt(entryId: String, value: String): String
}

/**
 * A stored value cannot be decrypted under the current key: the key was lost or replaced, the value
 * belongs to a different entry, or it was tampered with. Distinct from a failure to obtain the key
 * in the first place, which is potentially transient and must not be confused with this.
 */
internal class UndecryptableSnapshotException(cause: Throwable) :
    Exception("snapshot cannot be decrypted with the current key", cause)

/**
 * Pass-through [SnapshotCipher] used when encryption is disabled: values are written and read
 * as-is.
 *
 * [EncryptingPersistence] never calls [decrypt] on this cipher; it checks [isEncrypting] instead,
 * so that a value left over from an encrypted run is reported as absent rather than surfaced as
 * garbage.
 */
internal object PlaintextSnapshotCipher : SnapshotCipher {

    override val isEncrypting: Boolean = false

    override suspend fun encrypt(entryId: String, plaintext: String): String = plaintext

    override suspend fun decrypt(entryId: String, value: String): String = value
}

/**
 * AES-GCM cipher for snapshots. The data-encryption key (DEK) comes from [dekProvider] and is
 * decoded once and cached. Every encryption uses a fresh random IV, so the stored value is
 * `Base64(IV || ciphertext || tag)`, with the entry identifier as associated data. The GCM tag
 * makes [decrypt] fail on tampering, on a wrong key, and on a value written for a different entry.
 */
internal class AesGcmSnapshotCipher(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    private val dekProvider: suspend () -> ByteArray,
) : SnapshotCipher {

    private val mutex = Mutex()

    // Volatile: the fast path below reads this without holding the mutex, which would otherwise be
    // allowed to observe a published reference whose contents are not yet visible.
    @Volatile private var key: AES.GCM.Key? = null

    override val isEncrypting: Boolean = true

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

    override suspend fun encrypt(entryId: String, plaintext: String): String =
        Base64.encode(
            key().cipher().encrypt(plaintext.encodeToByteArray(), entryId.encodeToByteArray())
        )

    override suspend fun decrypt(entryId: String, value: String): String {
        // Resolve the key outside the guarded region below. Obtaining it can fail transiently, and
        // that must propagate rather than be reported as undecryptable data.
        val cipher = key().cipher()
        return try {
            cipher.decrypt(Base64.decode(value), entryId.encodeToByteArray()).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable rather than Exception on purpose: a WebCrypto failure on Wasm arrives as a
            // bare kotlin.Throwable, so narrowing this lets a wrong-key case escape as an error
            // instead of being reported as an unreadable snapshot.
            throw UndecryptableSnapshotException(e)
        }
    }
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

    // Volatile: see AesGcmSnapshotCipher.key.
    @Volatile private var dek: ByteArray? = null

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
 * Records whether the snapshot store has been converted to ciphertext.
 *
 * Once set, a value that cannot be decrypted is unambiguously key loss or tampering, which is what
 * lets [EncryptingPersistence] stop accepting plaintext.
 */
internal interface SnapshotMigrationState {

    suspend fun isMigrated(): Boolean

    suspend fun markMigrated()

    /**
     * Records that the store is no longer converted, which is the case once anything has been
     * written to it as plaintext. Turning encryption back on then converts it again.
     */
    suspend fun clearMigrated()
}

/**
 * [SnapshotMigrationState] kept alongside the wrapped DEK, separate from the snapshots.
 *
 * The marker is a string rather than the boolean it reads as, because on Web the backing store is
 * `LocalStoragePreferenceDataStore`, which holds the whole preference map as a JSON `Map<String,
 * String>` and supports string values only. Everything else this library persists — snapshots and
 * the wrapped DEK — is already a string, so keeping that the single value type lets the Web store
 * stay as small as it is.
 */
internal class DataStoreSnapshotMigrationState(private val dataStore: DataStore<Preferences>) :
    SnapshotMigrationState {

    override suspend fun isMigrated(): Boolean = dataStore.data.first()[MigratedKey] == MIGRATED

    override suspend fun markMigrated() {
        dataStore.edit { it[MigratedKey] = MIGRATED }
    }

    override suspend fun clearMigrated() {
        dataStore.edit { it.remove(MigratedKey) }
    }

    private companion object {
        val MigratedKey = stringPreferencesKey("lokksmith.snapshot.encrypted")
        const val MIGRATED = "true"
    }
}

/**
 * A [Persistence] that encrypts on write and decrypts on read via [cipher].
 *
 * On first access the store is converted once: any entry still held as plaintext JSON, whether
 * written before encryption existed or while it was disabled, is re-encrypted, and [migrationState]
 * records that it happened. The sweep runs before the first read or write rather than at
 * construction, so platform key material is not created just because the object graph was built. No
 * plaintext survives the first access either way.
 *
 * A read then has three outcomes:
 * - Decryption succeeds: the plaintext is returned.
 * - Decryption fails, for example after key loss or tampering: the entry is treated as absent
 *   rather than surfaced as an error. Only before the store has been converted is a plaintext-JSON
 *   value accepted as-is, which is the sole window in which one can legitimately exist.
 * - The key material could not be obtained at all, which is potentially transient: the error
 *   propagates. Reporting "absent" here would let a caller conclude the user is signed out and
 *   overwrite a still-valid snapshot.
 */
internal class EncryptingPersistence(
    private val delegate: Persistence,
    private val cipher: SnapshotCipher,
    private val migrationState: SnapshotMigrationState,
) : Persistence {

    private val migrationMutex = Mutex()

    @Volatile private var migrated = false

    override val data: Flow<Map<String, String>>
        get() = flow {
            ensureMigrated()
            emitAll(
                delegate.data.map { stored ->
                    val result = LinkedHashMap<String, String>(stored.size)
                    for ((entryId, value) in stored) {
                        readable(entryId, value)?.let { result[entryId] = it }
                    }
                    result
                }
            )
        }

    override fun observe(key: Key): Flow<String?> = flow {
        ensureMigrated()
        emitAll(delegate.observe(key).map { value -> value?.let { readable(key.value, it) } })
    }

    override suspend fun get(key: Key): String? {
        ensureMigrated()
        return delegate.get(key)?.let { readable(key.value, it) }
    }

    override suspend fun set(key: Key, snapshot: String) {
        ensureMigrated()
        delegate.set(key, cipher.encrypt(key.value, snapshot))
    }

    /**
     * Deletion works on physical presence, unlike [contains], which reflects readability: an entry
     * that can no longer be decrypted must still be removable.
     *
     * Deliberately takes the migration lock instead of calling [ensureMigrated]: it must be ordered
     * against a sweep in progress, which would otherwise write back plaintext it had already read
     * and resurrect the entry, but it must not depend on key material. Running the conversion here
     * would let a transient secure-store failure make an unreadable entry undeletable, which is the
     * one thing this method has to keep working.
     */
    override suspend fun delete(key: Key): Boolean = migrationMutex.withLock {
        delegate.delete(key)
    }

    override suspend fun contains(key: Key): Boolean = get(key) != null

    /**
     * Converts any plaintext entry to ciphertext, once per store.
     *
     * Nothing is recorded unless the sweep completes: if the platform secure store is transiently
     * unavailable the error propagates and plaintext stays acceptable, so a later attempt can still
     * read and convert it. A store that holds no plaintext is marked without creating key material.
     *
     * With encryption disabled the marker is cleared instead, because everything written from here
     * on is plaintext and the store is therefore no longer converted. Leaving a marker from an
     * earlier encrypted run in place would make a later run with encryption enabled skip the sweep
     * and reject that plaintext, so it would neither be readable nor ever be encrypted. Clearing
     * gives up nothing: while encryption is off there is no integrity guarantee to weaken, and the
     * sweep restores the marker as soon as it is on again.
     */
    private suspend fun ensureMigrated() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            if (!cipher.isEncrypting) {
                if (migrationState.isMigrated()) migrationState.clearMigrated()
            } else if (!migrationState.isMigrated()) {
                for ((entryId, value) in delegate.data.first()) {
                    if (!looksLikePlaintextJson(value)) continue
                    delegate.set(entryId.asKey(), cipher.encrypt(entryId, value))
                }
                migrationState.markMigrated()
            }
            migrated = true
        }
    }

    // Reached only after ensureMigrated(), so a plaintext value is no longer a legitimate state
    // here: conversion happens inside the sweep, never on the read path. An undecryptable value is
    // therefore key loss or tampering, and is never handed back.
    private suspend fun readable(entryId: String, stored: String): String? {
        if (!cipher.isEncrypting) return stored.takeIf(::looksLikePlaintextJson)
        return try {
            cipher.decrypt(entryId, stored)
        } catch (e: UndecryptableSnapshotException) {
            null
        }
    }
}

private fun looksLikePlaintextJson(value: String): Boolean = value.trimStart().startsWith('{')
