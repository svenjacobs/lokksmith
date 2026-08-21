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
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

        val encrypted = envelope.encrypt(dek)

        assertFalse(
            encrypted.contentEquals(dek),
            "encrypted output must not equal the plaintext DEK",
        )
        assertContentEquals(dek, envelope.decrypt(encrypted))
    }

    @Test
    fun `KEK persists across envelope instances`() = runTest {
        val dir = createTempDirectory().toFile()
        val dek = ByteArray(32) { (it * 7).toByte() }

        // encrypt with one instance, decrypt with a fresh instance backed by the same directory.
        val encrypted = envelope(dir).encrypt(dek)
        assertContentEquals(dek, envelope(dir).decrypt(encrypted))
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

    @Test
    fun `a truncated KEK file is regenerated rather than bricking the envelope`() = runTest {
        val dir = createTempDirectory().toFile()
        val dek = ByteArray(32) { it.toByte() }
        val wrapped = envelope(dir).encrypt(dek)

        // What an interrupted write used to leave behind: the file exists but holds no whole key.
        // Treating that as a readable key would fail every later operation with an error that never
        // clears, leaving no remedy but deleting the application's data by hand.
        dir.resolve("test.kek").writeBytes(ByteArray(0))

        val fresh = envelope(dir)
        assertNull(fresh.decrypt(wrapped), "the old wrapped DEK is unrecoverable")

        // The envelope still works: a new KEK was generated and round-trips.
        assertContentEquals(dek, fresh.decrypt(fresh.encrypt(dek)))
        assertEquals(
            32,
            dir.resolve("test.kek").readBytes().size,
            "a whole key should have replaced the truncated file",
        )
    }

    @Test
    fun `the KEK file is restricted to the owner`() = runTest {
        val dir = createTempDirectory().toFile()
        envelope(dir).encrypt(ByteArray(32))

        val path = dir.resolve("test.kek").toPath()
        if (!path.fileSystem.supportedFileAttributeViews().contains("posix")) return@runTest
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(path),
        )
    }

    @Test
    fun `an existing KEK file with lax permissions is tightened`() = runTest {
        val dir = createTempDirectory().toFile()
        val kekFile = dir.resolve("test.kek")
        // A key file left behind by an earlier version, world-readable. Creating the file with
        // restrictive attributes cannot fix this case; the permissions must be applied to the file
        // that ends up in place.
        kekFile.writeBytes(ByteArray(0))
        val path = kekFile.toPath()
        if (!path.fileSystem.supportedFileAttributeViews().contains("posix")) return@runTest
        Files.setPosixFilePermissions(
            path,
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ,
            ),
        )

        // The truncated file counts as absent, so this regenerates and replaces it.
        envelope(dir).encrypt(ByteArray(32))

        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(path),
        )
    }

    @Test
    fun `no temporary files are left behind`() = runTest {
        val dir = createTempDirectory().toFile()
        envelope(dir).encrypt(ByteArray(32))

        assertContentEquals(
            listOf("test.kek"),
            dir.list()!!.sorted(),
            "the atomic write should leave only the key file",
        )
    }
}
