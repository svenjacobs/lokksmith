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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet

internal actual class PlatformContext(val dataDirectory: File)

internal actual fun createDataStore(
    fileName: String,
    platformContext: PlatformContext,
): DataStore<Preferences> {
    ensureSecureDirectory(platformContext.dataDirectory.toPath())
    return createDataStore(fileName) { name ->
        platformContext.dataDirectory.resolve(name).absolutePath
    }
}

internal actual val platformUserAgentSuffix = "Desktop"

/**
 * Creates [path] (and any missing parents) and ensures it is readable/writable only by the current
 * user.
 *
 * On POSIX systems the directory is created (or chmod'd) to mode `0700`. On non-POSIX systems
 * (Windows) an ACL is applied that grants all permissions to the current user only.
 *
 * Tokens persisted via DataStore inherit the parent directory's permissions on rename, so locking
 * the parent down protects the on-disk token store from other local users (RFC 9700 §2.1.1,
 * CWE-276).
 */
internal fun ensureSecureDirectory(path: Path) {
    if (FileSystems.getDefault().supportedFileAttributeViews().contains(POSIX_VIEW)) {
        ensurePosixOwnerOnlyDirectory(path)
    } else {
        ensureWindowsOwnerOnlyDirectory(path)
    }
}

private fun ensurePosixOwnerOnlyDirectory(path: Path) {
    val ownerOnly =
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )
    if (Files.exists(path)) {
        Files.setPosixFilePermissions(path, ownerOnly)
    } else {
        Files.createDirectories(path, PosixFilePermissions.asFileAttribute(ownerOnly))
    }
}

internal fun ensureWindowsOwnerOnlyDirectory(path: Path) {
    Files.createDirectories(path)
    val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
    val owner =
        path.fileSystem.userPrincipalLookupService.lookupPrincipalByName(
            System.getProperty(USER_NAME_PROPERTY)
        )
    val entry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
            .build()
    view.acl = listOf(entry)
}

private const val POSIX_VIEW = "posix"
private const val USER_NAME_PROPERTY = "user.name"
