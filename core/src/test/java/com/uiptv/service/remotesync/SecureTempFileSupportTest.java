package com.uiptv.service.remotesync;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private String invoke(String methodName, boolean permissionsCleared, boolean ownerReadable, boolean ownerWritable) throws Exception {
        Method method = SecureTempFileSupport.class.getDeclaredMethod(methodName, boolean.class, boolean.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(null, permissionsCleared, ownerReadable, ownerWritable);
    }
}
