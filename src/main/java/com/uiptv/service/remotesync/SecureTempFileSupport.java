package com.uiptv.service.remotesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class SecureTempFileSupport {
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );
    private static final Path APP_TEMP_DIRECTORY = Path.of(
            System.getProperty("user.home", "."),
            ".uiptv",
            "tmp"
    );

    private SecureTempFileSupport() {
    }

    static Path createTempFile(String prefix, String suffix) throws IOException {
        Path tempDirectory = ensureAppTempDirectory();
        try {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS);
            return Files.createTempFile(tempDirectory, prefix, suffix, permissions);
        } catch (UnsupportedOperationException _) {
            Path path = Files.createTempFile(tempDirectory, prefix, suffix);
            ensureOwnerOnlyFileAccess(path);
            return path;
        }
    }

    private static Path ensureAppTempDirectory() throws IOException {
        try {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS);
            return Files.createDirectories(APP_TEMP_DIRECTORY, permissions);
        } catch (UnsupportedOperationException _) {
            Files.createDirectories(APP_TEMP_DIRECTORY);
            ensureOwnerOnlyDirectoryAccess(APP_TEMP_DIRECTORY);
            return APP_TEMP_DIRECTORY;
        } catch (FileAlreadyExistsException _) {
            ensureOwnerOnlyDirectoryAccess(APP_TEMP_DIRECTORY);
            return APP_TEMP_DIRECTORY;
        }
    }

    private static void ensureOwnerOnlyDirectoryAccess(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY_DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException _) {
            File file = path.toFile();
            requirePermissionChange(file.setReadable(false, false), "read", path);
            requirePermissionChange(file.setWritable(false, false), "write", path);
            requirePermissionChange(file.setExecutable(false, false), "execute", path);
            requirePermissionChange(file.setReadable(true, true), "read", path);
            requirePermissionChange(file.setWritable(true, true), "write", path);
            requirePermissionChange(file.setExecutable(true, true), "execute", path);
        }
    }

    private static void ensureOwnerOnlyFileAccess(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException _) {
            File file = path.toFile();
            requirePermissionChange(file.setReadable(false, false), "read", path);
            requirePermissionChange(file.setWritable(false, false), "write", path);
            requirePermissionChange(file.setExecutable(false, false), "execute", path);
            requirePermissionChange(file.setReadable(true, true), "read", path);
            requirePermissionChange(file.setWritable(true, true), "write", path);
        }
    }

    private static void requirePermissionChange(boolean changed, String permission, Path path) throws IOException {
        if (!changed) {
            throw new IOException("Unable to set " + permission + " permissions for " + path);
        }
    }
}
