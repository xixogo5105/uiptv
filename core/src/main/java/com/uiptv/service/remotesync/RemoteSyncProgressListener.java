package com.uiptv.service.remotesync;

@FunctionalInterface
public interface RemoteSyncProgressListener {
    void onProgress(RemoteSyncProgressStep step, String detail);
}
