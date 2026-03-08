package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        Path compiledResource = Path.of("target/classes/update.json");
        Path backup = compiledResource.resolveSibling("update.json.bak-test");
        Files.move(compiledResource, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            assertEquals("N/A", VersionManager.getCurrentVersion());
            assertEquals("N/A", VersionManager.getReleaseUrl());
            assertEquals("N/A", VersionManager.getReleaseDescription());
        } finally {
            Files.move(backup, compiledResource, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
