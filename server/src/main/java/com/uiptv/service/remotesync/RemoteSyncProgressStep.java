package com.uiptv.service.remotesync;

public enum RemoteSyncProgressStep {
    CONNECTING,
    WAITING_FOR_APPROVAL,
    CREATING_SNAPSHOT,
    UPLOADING,
    PREPARING_DOWNLOAD,
    DOWNLOADING,
    APPLYING_SYNC,
    COMPLETING_REMOTE,
    FINISHED
}
