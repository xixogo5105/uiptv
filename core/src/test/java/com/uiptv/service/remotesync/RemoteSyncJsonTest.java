package com.uiptv.service.remotesync;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncJsonTest {
    @Test
    void requestJsonRoundTripsAndroidPortableConfigurationProfile() {
        RemoteSyncRequest request = new RemoteSyncRequest(
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                "1234",
                "UIPTV Android",
                new RemoteSyncOptions(true, false, ConfigurationSyncProfile.ANDROID_PORTABLE)
        );

        JSONObject json = RemoteSyncJson.toJson(request);
        RemoteSyncRequest parsed = RemoteSyncJson.toRequest(json);

        assertTrue(parsed.options().syncConfiguration());
        assertFalse(parsed.options().syncExternalPlayerPaths());
        assertEquals(ConfigurationSyncProfile.ANDROID_PORTABLE, parsed.options().configurationProfile());
        assertTrue(parsed.options().archiveTransfer());
        assertTrue(parsed.options().encryptedTransfer());
    }

    @Test
    void requestJsonDefaultsUnknownConfigurationProfileToDesktopFull() {
        RemoteSyncRequest parsed = RemoteSyncJson.toRequest(new JSONObject()
                .put("direction", RemoteSyncDirection.IMPORT_FROM_REMOTE.name())
                .put("verificationCode", "1234")
                .put("requesterName", "UIPTV Android")
                .put("syncConfiguration", true)
                .put("syncExternalPlayerPaths", false)
                .put("configurationProfile", "NOT_A_PROFILE"));

        assertEquals(ConfigurationSyncProfile.DESKTOP_FULL, parsed.options().configurationProfile());
        assertFalse(parsed.options().archiveTransfer());
        assertFalse(parsed.options().encryptedTransfer());
    }
}
