package com.uiptv.service.remotesync;

public record RemoteSyncOptions(boolean syncConfiguration,
                                boolean syncExternalPlayerPaths,
                                ConfigurationSyncProfile configurationProfile) {
    public RemoteSyncOptions(boolean syncConfiguration, boolean syncExternalPlayerPaths) {
        this(syncConfiguration, syncExternalPlayerPaths, ConfigurationSyncProfile.DESKTOP_FULL);
    }

    public RemoteSyncOptions {
        if (configurationProfile == null) {
            configurationProfile = ConfigurationSyncProfile.DESKTOP_FULL;
        }
    }
}
