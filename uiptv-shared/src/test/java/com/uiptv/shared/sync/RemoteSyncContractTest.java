package com.uiptv.shared.sync;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void requestNormalizesOptionalFieldsAndRequiresDirection() {
        RemoteSyncRequest request = new RemoteSyncRequest(RemoteSyncDirection.IMPORT_FROM_REMOTE, null, null, null);

        assertEquals(RemoteSyncDirection.IMPORT_FROM_REMOTE, request.direction());
        assertEquals("", request.verificationCode());
        assertEquals("", request.requesterName());
        assertFalse(request.options().syncConfiguration());
        assertFalse(request.options().syncExternalPlayerPaths());
        assertEquals(ConfigurationSyncProfile.DESKTOP_FULL, request.options().configurationProfile());

        assertThrows(IllegalArgumentException.class, () -> new RemoteSyncRequest(null, "123456", "Phone", null));
    }

    @Test
    void sessionStateNormalizesDefaultsAndRequiresDirection() {
        RemoteSyncSessionState state = new RemoteSyncSessionState(
                null,
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("", state.sessionId());
        assertEquals(RemoteSyncDirection.EXPORT_TO_REMOTE, state.direction());
        assertEquals(RemoteSyncStatus.FAILED, state.status());
        assertEquals("", state.verificationCode());
        assertEquals("", state.requesterName());
        assertEquals("", state.requesterAddress());
        assertEquals("", state.message());
        assertFalse(state.options().syncConfiguration());
        assertFalse(state.options().syncExternalPlayerPaths());

        assertThrows(IllegalArgumentException.class,
                () -> new RemoteSyncSessionState("sync-1", null, RemoteSyncStatus.APPROVED,
                        "123456", "Phone", "192.0.2.1", null, "ready"));
    }

    @Test
    void executionResultNormalizesNullMessage() {
        RemoteSyncExecutionResult result = new RemoteSyncExecutionResult(
                new DatabaseSyncReport(List.of(), true, false, false),
                null
        );

        assertEquals("", result.message());
        assertTrue(result.report().configurationRequested());
    }
}
