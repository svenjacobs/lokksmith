/*
 * Copyright 2025 Sven Jacobs
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
package dev.lokksmith

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class ContainerDesktopTest {

    @Test
    fun `ensureSecureDirectory creates missing directory with owner-only POSIX perms`() {
        assumeTrue("requires POSIX file attribute view", isPosix)
        val parent = createTempDirectory("lokksmith-test-")
        val target = parent.resolve("data")
        try {
            ensureSecureDirectory(target)

            assertTrue(Files.isDirectory(target))
            assertEquals(OWNER_ONLY_POSIX, Files.getPosixFilePermissions(target))
        } finally {
            deleteRecursively(parent)
        }
    }

    @Test
    fun `ensureSecureDirectory tightens existing directory perms to owner-only`() {
        assumeTrue("requires POSIX file attribute view", isPosix)
        val target = createTempDirectory("lokksmith-test-")
        try {
            // Loosen perms beyond owner-only and verify they get tightened.
            Files.setPosixFilePermissions(
                target,
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ,
                ),
            )

            ensureSecureDirectory(target)

            assertEquals(OWNER_ONLY_POSIX, Files.getPosixFilePermissions(target))
        } finally {
            deleteRecursively(target)
        }
    }

    @Test
    fun `ensureSecureDirectory creates intermediate parents`() {
        assumeTrue("requires POSIX file attribute view", isPosix)
        val root = createTempDirectory("lokksmith-test-")
        val target = root.resolve("a/b/c")
        try {
            ensureSecureDirectory(target)

            assertTrue(Files.isDirectory(target))
            assertEquals(OWNER_ONLY_POSIX, Files.getPosixFilePermissions(target))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `ensureWindowsOwnerOnlyDirectory creates the directory`() {
        val parent = createTempDirectory("lokksmith-test-")
        val target = parent.resolve("nested/data")
        try {
            ensureWindowsOwnerOnlyDirectory(target)

            assertTrue(Files.isDirectory(target))
        } finally {
            deleteRecursively(parent)
        }
    }

    @Test
    fun `ensureWindowsOwnerOnlyDirectory applies owner-only ACL on Windows`() {
        assumeTrue("requires AclFileAttributeView (Windows)", supportsAcl)
        val parent = createTempDirectory("lokksmith-test-")
        val target = parent.resolve("data")
        try {
            ensureWindowsOwnerOnlyDirectory(target)

            val view =
                Files.getFileAttributeView(target, AclFileAttributeView::class.java)
                    ?: error("AclFileAttributeView unexpectedly null")
            val acl = view.acl
            assertEquals(1, acl.size, "expected exactly one ACL entry")
            val entry = acl.single()
            assertEquals(AclEntryType.ALLOW, entry.type())
            assertEquals(EnumSet.allOf(AclEntryPermission::class.java), entry.permissions())
        } finally {
            deleteRecursively(parent)
        }
    }

    private val isPosix: Boolean
        get() = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private val supportsAcl: Boolean
        get() = FileSystems.getDefault().supportedFileAttributeViews().contains("acl")

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }

    companion object {
        private val OWNER_ONLY_POSIX =
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            )
    }
}
