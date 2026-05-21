package com.uiptv.service.remotesync;

public record RemoteSyncOptions(boolean syncConfiguration,
                                boolean syncExternalPlayerPaths,
                                ConfigurationSyncProfile configurationProfile,
                                boolean archiveTransfer,
                                boolean encryptedTransfer) {
    public RemoteSyncOptions(boolean syncConfiguration, boolean syncExternalPlayerPaths) {
        this(syncConfiguration, syncExternalPlayerPaths, ConfigurationSyncProfile.DESKTOP_FULL);
    }

    public RemoteSyncOptions(boolean syncConfiguration,
                             boolean syncExternalPlayerPaths,
                             ConfigurationSyncProfile configurationProfile) {
        this(syncConfiguration, syncExternalPlayerPaths, configurationProfile, true, true);
    }

    public RemoteSyncOptions {
        if (configurationProfile == null) {
            configurationProfile = ConfigurationSyncProfile.DESKTOP_FULL;
        }
    }

    public static RemoteSyncOptions legacyRawTransfer(boolean syncConfiguration,
                                                      boolean syncExternalPlayerPaths,
                                                      ConfigurationSyncProfile configurationProfile) {
        return new RemoteSyncOptions(syncConfiguration, syncExternalPlayerPaths, configurationProfile, false, false);
    }
}
