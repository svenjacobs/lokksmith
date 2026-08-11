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
@file:OptIn(ExperimentalForeignApi::class)

package dev.lokksmith.crypto

import dev.lokksmith.PlatformContext
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/** iOS [KeyEnvelope]. The key is a random AES-256 key kept in the Keychain. */
internal actual class KeyEnvelope
actual constructor(
    platformContext: PlatformContext,
    private val alias: String,
) {

    private val provider = CryptographyProvider.Default
    private val random = CryptographyRandom.Default

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

    private fun loadKek(): ByteArray? = memScoped {
        val query =
            keychainQuery(
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        // Only "not found" means a new key should be created. Anything else (e.g. Keychain locked
        // before first unlock) must throw. Silently regenerating would orphan the wrapped DEK and
        // make every stored snapshot unreadable.
        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "Keychain read failed with status $status" }
        @Suppress("UNCHECKED_CAST") val data = result.value as CFDataRef?
        val bytes = data?.toByteArray()
        data?.let { CFRelease(it) }
        bytes
    }

    private fun createKek(): ByteArray {
        val kek = random.nextBytes(KEK_SIZE_BYTES)
        val data = kek.toCFData()
        val query =
            keychainQuery(
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
        val status = SecItemAdd(query, null)
        CFRelease(query)
        data?.let { CFRelease(it) }
        return when (status) {
            errSecSuccess -> kek
            // The item already exists but the load above missed it. Re-read the stored key instead
            // of returning this one, which was never saved.
            errSecDuplicateItem -> loadKek() ?: error("Keychain item exists but is unreadable")
            else -> error("Keychain write failed with status $status")
        }
    }

    /**
     * Builds a Keychain query dictionary for this envelope's generic-password item, plus any
     * [extra] key/value pairs. Caller owns the returned reference and must [CFRelease] it.
     */
    private fun keychainQuery(vararg extra: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef {
        val account = alias.toCFString()
        val service = SERVICE.toCFString()
        val dict =
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )!!
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, service)
        CFDictionaryAddValue(dict, kSecAttrAccount, account)
        extra.forEach { (key, value) -> CFDictionaryAddValue(dict, key, value) }
        // The dictionary retains the keys/values; release the local references.
        account?.let { CFRelease(it) }
        service?.let { CFRelease(it) }
        return dict
    }

    private fun String.toCFString(): CFStringRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)

    private fun ByteArray.toCFData(): CFDataRef? = usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), size.convert())
    }

    private fun CFDataRef.toByteArray(): ByteArray {
        val length = CFDataGetLength(this).toInt()
        val out = ByteArray(length)
        val bytes = CFDataGetBytePtr(this)
        if (length > 0 && bytes != null) {
            out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length.convert()) }
        }
        return out
    }

    private companion object {
        const val KEK_SIZE_BYTES = 32 // AES-256
        const val SERVICE = "dev.lokksmith.kek"
    }
}
