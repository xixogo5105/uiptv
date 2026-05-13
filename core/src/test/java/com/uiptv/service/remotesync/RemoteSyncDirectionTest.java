package com.uiptv.service.remotesync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncDirectionTest {

    @Test
    void uploadsToRemoteOnlyForExportDirection() {
        assertTrue(RemoteSyncDirection.EXPORT_TO_REMOTE.uploadsToRemote());
        assertFalse(RemoteSyncDirection.IMPORT_FROM_REMOTE.uploadsToRemote());
    }
}
