package com.uiptv.service.remotesync;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecureTempFileSupportTest {

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
    void resolvePermissionFailure_reportsSpecificFailure() throws Exception {
        assertEquals("owner-only", invoke("resolvePermissionFailure", false, true, true));
        assertEquals("read", invoke("resolvePermissionFailure", true, false, true));
        assertEquals("write", invoke("resolvePermissionFailure", true, true, false));
        assertEquals("execute", invoke("resolvePermissionFailure", true, true, true));
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

    @Test
    void bestEffortAccessReportsPermissionFailures() throws Exception {
        Path path = Files.createTempFile("secure-temp-permission-", ".tmp");
        File file = mock(File.class);
        when(file.setReadable(false, false)).thenReturn(false);
        when(file.setWritable(false, false)).thenReturn(true);
        when(file.setExecutable(false, false)).thenReturn(true);
        when(file.setReadable(true, true)).thenReturn(true);
        when(file.setWritable(true, true)).thenReturn(true);
        when(file.setExecutable(true, true)).thenReturn(true);
        when(file.toPath()).thenReturn(path);

        try {
            IOException thrown = assertThrows(IOException.class,
                    () -> invokeApplyBestEffortOwnerOnlyAccess(file, true));
            assertTrue(thrown.getMessage().contains("owner-only"));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private String invoke(String methodName, boolean permissionsCleared, boolean ownerReadable, boolean ownerWritable) throws Exception {
        Method method = SecureTempFileSupport.class.getDeclaredMethod(methodName, boolean.class, boolean.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(null, permissionsCleared, ownerReadable, ownerWritable);
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

    private void invokeApplyBestEffortOwnerOnlyAccess(File file, boolean executable) throws Exception {
        Method method = SecureTempFileSupport.class.getDeclaredMethod("applyBestEffortOwnerOnlyAccess", File.class, boolean.class);
        method.setAccessible(true);
        try {
            method.invoke(null, file, executable);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
