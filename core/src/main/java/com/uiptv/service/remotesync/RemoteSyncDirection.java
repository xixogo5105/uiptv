package com.uiptv.service.remotesync;

public enum RemoteSyncDirection {
    EXPORT_TO_REMOTE,
    IMPORT_FROM_REMOTE;

    public boolean uploadsToRemote() {
        return this == EXPORT_TO_REMOTE;
    }
}
