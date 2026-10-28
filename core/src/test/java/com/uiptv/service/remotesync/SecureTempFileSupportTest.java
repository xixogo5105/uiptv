package com.uiptv.service.remotesync;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureTempFileSupportTest {

    @Test
    void aclRestrictionsMustBeSupportedAppliedAndVerified() throws Exception {
        var view = org.mockito.Mockito.mock(java.nio.file.attribute.AclFileAttributeView.class);
        var owner = org.mockito.Mockito.mock(java.nio.file.attribute.UserPrincipal.class);
        org.mockito.Mockito.when(view.getOwner()).thenReturn(owner);
        java.util.concurrent.atomic.AtomicReference<java.util.List<java.nio.file.attribute.AclEntry>> entries =
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of());
        org.mockito.Mockito.doAnswer(invocation -> { entries.set(invocation.getArgument(0)); return null; })
                .when(view).setAcl(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.when(view.getAcl()).thenAnswer(invocation -> entries.get());
        SecureTempFileSupport.ensureOwnerOnlyAcl(view, true);
        assertEquals(1, entries.get().size());
        assertEquals(owner, entries.get().getFirst().principal());
        assertEquals(java.nio.file.attribute.AclEntryType.ALLOW, entries.get().getFirst().type());
        assertTrue(entries.get().getFirst().flags().contains(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT));
        org.mockito.Mockito.when(view.getAcl()).thenReturn(java.util.List.of());
        assertThrows(IOException.class, () -> SecureTempFileSupport.ensureOwnerOnlyAcl(view, false));
        org.mockito.Mockito.doThrow(new IOException("permission denied")).when(view).setAcl(org.mockito.ArgumentMatchers.anyList());
        assertThrows(IOException.class, () -> SecureTempFileSupport.ensureOwnerOnlyAcl(view, false));
        assertThrows(IOException.class, () -> SecureTempFileSupport.ensureOwnerOnlyAcl(null, false));
    }

    @Test
    void createTempFile_usesApplicationTempDirectory() throws Exception {
        Path tempFile = SecureTempFileSupport.createTempFile("sync-", ".tmp");

        assertTrue(Files.isRegularFile(tempFile));
        assertTrue(tempFile.getFileName().toString().startsWith("sync-"));
        assertTrue(tempFile.getFileName().toString().endsWith(".tmp"));
        assertTrue(tempFile.toString().contains(Path.of(".uiptv", "tmp").toString()));
        Files.deleteIfExists(tempFile);
    }

    @Test
    void createTempDirectory_usesApplicationTempDirectory() throws Exception {
        Path tempDirectory = SecureTempFileSupport.createTempDirectory("restore-");

        assertTrue(Files.isDirectory(tempDirectory));
        assertTrue(tempDirectory.getFileName().toString().startsWith("restore-"));
        assertTrue(tempDirectory.toString().contains(Path.of(".uiptv", "tmp").toString()));
        Files.deleteIfExists(tempDirectory);
    }

    @Test
    void existingApplicationDirectoryIsRestrictedAgain(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
        SecureTempFileSupport.ensureAppTempDirectory(directory);
        assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
    }

    @Test
    void ownerOnlyAccessHelpersAcceptRealDirectoryAndFile() throws Exception {
        Path directory = Files.createTempDirectory("secure-temp-dir-");
        Path file = Files.createTempFile(directory, "secure-temp-file-", ".tmp");
        try {
            invokeVoid("ensureOwnerOnlyDirectoryAccess", directory);
            invokeVoid("ensureOwnerOnlyFileAccess", file);

            assertTrue(Files.isDirectory(directory));
            assertTrue(Files.isRegularFile(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void bestEffortHelpersRejectWrongPathTypes() throws Exception {
        Path directory = Files.createTempDirectory("secure-temp-dir-");
        Path file = Files.createTempFile("secure-temp-file-", ".tmp");
        try {
            assertThrows(IOException.class, () -> invokeVoid("ensureBestEffortOwnerOnlyFileAccess", directory));
            assertThrows(IOException.class, () -> invokeVoid("ensureBestEffortOwnerOnlyDirectoryAccess", file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private void invokeVoid(String methodName, Path path) throws Exception {
        Method method = SecureTempFileSupport.class.getDeclaredMethod(methodName, Path.class);
        method.setAccessible(true);
        try {
            method.invoke(null, path);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

}
