package com.uiptv.service.remotesync

import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

internal object SecureTempFileSupport {
    private const val READ_PERMISSION = "read"
    private const val WRITE_PERMISSION = "write"
    private const val EXECUTE_PERMISSION = "execute"
    private val ownerOnlyDirectoryPermissions = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    )
    private val ownerOnlyFilePermissions = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    )
    private val appTempDirectory: Path = Path.of(System.getProperty("user.home", "."), ".uiptv", "tmp")

    @JvmStatic
    @Throws(IOException::class)
    fun createTempFile(prefix: String, suffix: String): Path {
        val tempDirectory = ensureAppTempDirectory()
        return try {
            val permissions: FileAttribute<Set<PosixFilePermission>> =
                PosixFilePermissions.asFileAttribute(ownerOnlyFilePermissions)
            Files.createTempFile(tempDirectory, prefix, suffix, permissions)
        } catch (_: UnsupportedOperationException) {
            val path = Files.createTempFile(tempDirectory, prefix, suffix)
            ensureOwnerOnlyFileAccess(path)
            path
        }
    }

    @Throws(IOException::class)
    private fun ensureAppTempDirectory(): Path {
        return try {
            val permissions: FileAttribute<Set<PosixFilePermission>> =
                PosixFilePermissions.asFileAttribute(ownerOnlyDirectoryPermissions)
            Files.createDirectories(appTempDirectory, permissions)
        } catch (_: UnsupportedOperationException) {
            Files.createDirectories(appTempDirectory)
            ensureOwnerOnlyDirectoryAccess(appTempDirectory)
            appTempDirectory
        } catch (_: FileAlreadyExistsException) {
            ensureOwnerOnlyDirectoryAccess(appTempDirectory)
            appTempDirectory
        }
    }

    @Throws(IOException::class)
    private fun ensureOwnerOnlyDirectoryAccess(path: Path) {
        try {
            Files.setPosixFilePermissions(path, ownerOnlyDirectoryPermissions)
        } catch (_: UnsupportedOperationException) {
            ensureBestEffortOwnerOnlyDirectoryAccess(path)
        }
    }

    @Throws(IOException::class)
    private fun ensureOwnerOnlyFileAccess(path: Path) {
        try {
            Files.setPosixFilePermissions(path, ownerOnlyFilePermissions)
        } catch (_: UnsupportedOperationException) {
            ensureBestEffortOwnerOnlyFileAccess(path)
        }
    }

    @Throws(IOException::class)
    private fun ensureBestEffortOwnerOnlyDirectoryAccess(path: Path) {
        val file = path.toFile()
        applyBestEffortOwnerOnlyAccess(file, true)
        if (!Files.isDirectory(path)) {
            throw IOException("Unable to prepare temp directory $path")
        }
    }

    @Throws(IOException::class)
    private fun ensureBestEffortOwnerOnlyFileAccess(path: Path) {
        val file = path.toFile()
        applyBestEffortOwnerOnlyAccess(file, false)
        if (!Files.isRegularFile(path)) {
            throw IOException("Unable to prepare temp file $path")
        }
    }

    @Throws(IOException::class)
    private fun applyBestEffortOwnerOnlyAccess(file: File, executable: Boolean) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)

        val ownerReadable = file.setReadable(true, true)
        val ownerWritable = file.setWritable(true, true)
        val ownerExecutable = !executable || file.setExecutable(true, true)

        if (!ownerReadable || !ownerWritable || !ownerExecutable) {
            val dosView = Files.getFileAttributeView(file.toPath(), DosFileAttributeView::class.java)
            if (dosView != null) {
                return
            }
            val permission = if (!ownerReadable) READ_PERMISSION else if (!ownerWritable) WRITE_PERMISSION else EXECUTE_PERMISSION
            throw IOException("Unable to set $permission permissions for " + file.toPath())
        }
    }
}
