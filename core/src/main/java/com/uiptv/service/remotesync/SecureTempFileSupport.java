package com.uiptv.service.remotesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryFlag;
import java.util.EnumSet;
import java.util.List;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public final class SecureTempFileSupport {
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

    public static Path createTempFile(String prefix, String suffix) throws IOException {
        Path tempDirectory = ensureAppTempDirectory();
        try {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS);
            return Files.createTempFile(tempDirectory, prefix, suffix, permissions);
        } catch (UnsupportedOperationException _) {
            Path path = Files.createTempFile(tempDirectory, prefix, suffix);
            try {
                ensureOwnerOnlyFileAccess(path);
                return path;
            } catch (IOException | RuntimeException failure) {
                try { Files.deleteIfExists(path); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
    }

    public static Path createTempDirectory(String prefix) throws IOException {
        Path tempDirectory = ensureAppTempDirectory();
        try {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS);
            return Files.createTempDirectory(tempDirectory, prefix, permissions);
        } catch (UnsupportedOperationException _) {
            Path path = Files.createTempDirectory(tempDirectory, prefix);
            try {
                ensureOwnerOnlyDirectoryAccess(path);
                return path;
            } catch (IOException | RuntimeException failure) {
                try { Files.deleteIfExists(path); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
    }

    private static Path ensureAppTempDirectory() throws IOException {
        return ensureAppTempDirectory(APP_TEMP_DIRECTORY);
    }

    static Path ensureAppTempDirectory(Path directory) throws IOException {
        try {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS);
            Files.createDirectories(directory, permissions);
            ensureOwnerOnlyDirectoryAccess(directory);
            return directory;
        } catch (UnsupportedOperationException _) {
            Files.createDirectories(directory);
            ensureOwnerOnlyDirectoryAccess(directory);
            return directory;
        } catch (FileAlreadyExistsException _) {
            ensureOwnerOnlyDirectoryAccess(directory);
            return directory;
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
        Path path = file.toPath();
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        ensureOwnerOnlyAcl(view, executable);
    }

    static void ensureOwnerOnlyAcl(AclFileAttributeView view, boolean executable) throws IOException {
        if (view == null) {
            throw new IOException("Owner-only temp access requires POSIX permissions or ACL support");
        }
        var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(view.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (executable) builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        List<AclEntry> ownerOnly = List.of(builder.build());
        view.setAcl(ownerOnly);
        if (!view.getAcl().equals(ownerOnly)) {
            throw new IOException("Unable to verify owner-only temp access");
        }
    }

}
