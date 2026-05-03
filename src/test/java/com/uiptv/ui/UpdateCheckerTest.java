package com.uiptv.ui;

import com.uiptv.util.VersionManager;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void parseUpdateInfo_readsGithubReleasePayload() {
        JSONObject json = new JSONObject()
                .put("tag_name", "v0.1.11")
                .put("html_url", "https://github.com/xixogo5105/uiptv/releases/tag/v0.1.11")
                .put("body", "Release notes from GitHub.");

        UpdateInfo info = UpdateChecker.parseUpdateInfo(json);

        assertEquals("0.1.11", info.getVersion());
        assertEquals("https://github.com/xixogo5105/uiptv/releases/tag/v0.1.11", info.getUrl());
        assertEquals("Release notes from GitHub.", info.getDescription());
    }

    @Test
    void normalizeVersionValue_stripsLeadingV() {
        assertEquals("0.1.10", UpdateChecker.normalizeVersionValue("v0.1.10"));
        assertEquals("0.1.10", UpdateChecker.normalizeVersionValue("V0.1.10"));
    }

    @Test
    void isUpdateAvailable_comparesSemanticVersionParts() {
        String current = VersionManager.getCurrentVersion();
        String[] parts = current.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

        assertTrue(UpdateChecker.isUpdateAvailable(major + "." + minor + "." + (patch + 1)));
        assertFalse(UpdateChecker.isUpdateAvailable(current));
        assertFalse(UpdateChecker.isUpdateAvailable(major + "." + minor + "." + Math.max(0, patch - 1)));
    }
}
