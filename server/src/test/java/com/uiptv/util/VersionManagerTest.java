package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionManagerTest {

    @Test
    void readsBuildVersionFromClasspathResource() {
        assertNotEquals("N/A", VersionManager.getCurrentVersion());
        assertEquals("https://github.com/xixogo5105/uiptv/releases/latest", VersionManager.getReleaseUrl());
    }

    @Test
    void repeatedReadsStayConsistent() {
        assertEquals(VersionManager.getCurrentVersion(), VersionManager.getCurrentVersion());
        assertEquals(VersionManager.getReleaseUrl(), VersionManager.getReleaseUrl());
    }

    @Test
    void resolveVersion_skipsUnresolvedPlaceholderValues() {
        assertEquals("0.1.8", VersionManager.resolveVersion("${project.version}", null, "0.1.8"));
        assertEquals("N/A", VersionManager.resolveVersion("${project.version}", "", null));
        assertTrue(VersionManager.isResolvedVersionValue("0.1.10"));
        assertFalse(VersionManager.isResolvedVersionValue("${project.version}"));
    }

    @Test
    void missingVersionMetadataFallsBackToNa() throws Exception {
        ClassLoader shadowLoader = new ClassLoader(VersionManager.class.getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("app-version.properties".equals(name) || "META-INF/maven/com.spc/uiptv/pom.properties".equals(name)) {
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
        var releaseDescriptionField = shadowVersionManager.getField("RELEASE_DESCRIPTION");

        assertEquals(VersionManager.getCurrentVersion(), getCurrentVersion.invoke(null));
        assertEquals("https://github.com/xixogo5105/uiptv/releases/latest", getReleaseUrl.invoke(null));
        assertEquals("N/A", releaseDescriptionField.get(null));
    }
}
