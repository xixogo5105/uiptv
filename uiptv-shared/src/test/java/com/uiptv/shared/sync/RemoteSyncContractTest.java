package com.uiptv.shared.sync;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncContractTest {
    @Test
    void directionDocumentsTransferSide() {
        assertTrue(RemoteSyncDirection.EXPORT_TO_REMOTE.uploadsToRemote());
        assertFalse(RemoteSyncDirection.IMPORT_FROM_REMOTE.uploadsToRemote());
    }

    @Test
    void progressStepsMatchDesktopRemoteSyncFlow() {
        assertEquals(RemoteSyncProgressStep.PREPARING_DOWNLOAD, RemoteSyncProgressStep.valueOf("PREPARING_DOWNLOAD"));
    }

    @Test
    void androidOptionsCanRequestPortableConfigurationWithoutPlayerPaths() {
        RemoteSyncOptions options = new RemoteSyncOptions(true, false, ConfigurationSyncProfile.ANDROID_PORTABLE);

        assertTrue(options.syncConfiguration());
        assertFalse(options.syncExternalPlayerPaths());
        assertEquals(ConfigurationSyncProfile.ANDROID_PORTABLE, options.configurationProfile());
    }

    @Test
    void reportCountsRows() {
        DatabaseSyncReport report = new DatabaseSyncReport(
                List.of(new TableSyncResult("Account", 2), new TableSyncResult("Bookmark", 3)),
                false,
                false,
                false
        );

        assertEquals(5, report.totalRowsSynced());
    }
}
