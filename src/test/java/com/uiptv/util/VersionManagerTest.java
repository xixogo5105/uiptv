package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VersionManagerTest {

    @Test
    void readsUpdateMetadataFromClasspathResource() {
        assertNotEquals("N/A", VersionManager.getCurrentVersion());
        assertNotEquals("N/A", VersionManager.getReleaseUrl());
        assertNotEquals("N/A", VersionManager.getReleaseDescription());
        assertFalse(VersionManager.getReleaseDescription().isBlank());
    }

    @Test
    void repeatedReadsStayConsistent() {
        assertEquals(VersionManager.getCurrentVersion(), VersionManager.getCurrentVersion());
        assertEquals(VersionManager.getReleaseUrl(), VersionManager.getReleaseUrl());
    }

    @Test
    void missingUpdateMetadataFallsBackToNa() throws Exception {
        ClassLoader shadowLoader = new ClassLoader(VersionManager.class.getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("update.json".equals(name)) {
                    return null;
                }
                return super.getResourceAsStream(name);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!"com.uiptv.util.VersionManager".equals(name)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream input = VersionManager.class.getClassLoader()
                            .getResourceAsStream("com/uiptv/util/VersionManager.class")) {
                        if (input == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException ex) {
                        throw new ClassNotFoundException(name, ex);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        };

        Class<?> shadowVersionManager = Class.forName("com.uiptv.util.VersionManager", true, shadowLoader);
        Method getCurrentVersion = shadowVersionManager.getMethod("getCurrentVersion");
        Method getReleaseUrl = shadowVersionManager.getMethod("getReleaseUrl");

        assertEquals("N/A", getCurrentVersion.invoke(null));
        assertEquals("N/A", getReleaseUrl.invoke(null));
    }
}
