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
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class KeyEnvelopeJvmTest {

    private fun envelope(dir: java.io.File, alias: String = "test") =
        KeyEnvelope(PlatformContext(dataDirectory = dir), alias)

    @Test
    fun `encrypt then decrypt round-trips the DEK`() = runTest {
        val dir = createTempDirectory().toFile()
        val envelope = envelope(dir)
        val dek = ByteArray(32) { it.toByte() }

        val encryptped = envelope.encrypt(dek)

        assertFalse(
            encryptped.contentEquals(dek),
            "encryptped output must not equal the plaintext DEK",
        )
        assertContentEquals(dek, envelope.decrypt(encryptped))
    }

    @Test
    fun `KEK persists across envelope instances`() = runTest {
        val dir = createTempDirectory().toFile()
        val dek = ByteArray(32) { (it * 7).toByte() }

        // encrypt with one instance, decrypt with a fresh instance backed by the same directory.
        val encryptped = envelope(dir).encrypt(dek)
        assertContentEquals(dek, envelope(dir).decrypt(encryptped))
    }

    @Test
    fun `KEK file is created in the data directory`() = runTest {
        val dir = createTempDirectory().toFile()
        envelope(dir, alias = "myalias").encrypt(ByteArray(32))

        assertTrue(dir.resolve("myalias.kek").exists(), "expected KEK file to be created")
    }

    @Test
    fun `decrypt returns null when the KEK is absent`() = runTest {
        val dir = createTempDirectory().toFile()
        val wrapped = envelope(dir).encrypt(ByteArray(32) { it.toByte() })
        assertTrue(dir.resolve("test.kek").delete())

        // A fresh envelope over the same directory: the KEK is gone, so decrypt signals regenerate.
        assertNull(envelope(dir).decrypt(wrapped))
    }

    @Test
    fun `decrypt propagates a KEK read error instead of returning null`() = runTest {
        val dir = createTempDirectory().toFile()
        val wrapped = envelope(dir).encrypt(ByteArray(32) { it.toByte() })

        // Make the KEK path exist but be unreadable (a directory): a transient-style read failure,
        // not absence. It must propagate, never be swallowed into a regenerate signal.
        val kekFile = dir.resolve("test.kek")
        assertTrue(kekFile.delete())
        assertTrue(kekFile.mkdir())

        assertFailsWith<Exception> { envelope(dir).decrypt(wrapped) }
    }
}
