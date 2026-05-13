package com.uiptv.shared.sync;

public record RemoteSyncOptions(boolean syncConfiguration,
                                boolean syncExternalPlayerPaths,
                                ConfigurationSyncProfile configurationProfile) {
    public RemoteSyncOptions(boolean syncConfiguration, boolean syncExternalPlayerPaths) {
        this(syncConfiguration, syncExternalPlayerPaths, ConfigurationSyncProfile.DESKTOP_FULL);
    }
}
