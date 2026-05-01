package com.uiptv.service.remotesync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class SecureTempFileSupport {
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE_PERMISSIONS =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private SecureTempFileSupport() {
    }

    static Path createTempFile(String prefix, String suffix) throws IOException {
        try {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS);
            return Files.createTempFile(prefix, suffix, permissions);
        } catch (UnsupportedOperationException _) {
            Path path = Files.createTempFile(prefix, suffix);
            limitFilePermissions(path);
            return path;
        }
    }

    private static void limitFilePermissions(Path path) {
        try {
            path.toFile().setReadable(false, false);
            path.toFile().setWritable(false, false);
            path.toFile().setExecutable(false, false);
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(true, true);
        } catch (SecurityException _) {
            // Best effort on non-POSIX file systems.
        }
    }
}
