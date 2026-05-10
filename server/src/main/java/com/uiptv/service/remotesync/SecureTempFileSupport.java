package com.uiptv.service.remotesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class SecureTempFileSupport {
    private static final String READ_PERMISSION = "read";
    private static final String WRITE_PERMISSION = "write";
    private static final String EXECUTE_PERMISSION = "execute";
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
            ensureBestEffortOwnerOnlyDirectoryAccess(path);
        }
    }

    private static void ensureOwnerOnlyFileAccess(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException _) {
            ensureBestEffortOwnerOnlyFileAccess(path);
        }
    }

    private static void ensureBestEffortOwnerOnlyDirectoryAccess(Path path) throws IOException {
        File file = path.toFile();
        applyBestEffortOwnerOnlyAccess(file, true);
        if (!Files.isDirectory(path)) {
            throw new IOException("Unable to prepare temp directory " + path);
        }
    }

    private static void ensureBestEffortOwnerOnlyFileAccess(Path path) throws IOException {
        File file = path.toFile();
        applyBestEffortOwnerOnlyAccess(file, false);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Unable to prepare temp file " + path);
        }
    }

    private static void applyBestEffortOwnerOnlyAccess(File file, boolean executable) throws IOException {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);

        boolean ownerReadable = file.setReadable(true, true);
        boolean ownerWritable = file.setWritable(true, true);
        boolean ownerExecutable = !executable || file.setExecutable(true, true);

        if (!ownerReadable || !ownerWritable || !ownerExecutable) {
            DosFileAttributeView dosView = Files.getFileAttributeView(file.toPath(), DosFileAttributeView.class);
            if (dosView != null) {
                return;
            }

            String permission = !ownerReadable ? READ_PERMISSION : !ownerWritable ? WRITE_PERMISSION : EXECUTE_PERMISSION;
            throw new IOException("Unable to set " + permission + " permissions for " + file.toPath());
        }
    }
}
