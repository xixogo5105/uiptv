package com.uiptv.application;

import com.uiptv.service.ConfigurationService;

/** Desktop Settings and the gear shortcut share the same durable configuration flag. */
public class EpgSettingsApplicationService {
    public void setEnabled(boolean enabled) {
        synchronized (ConfigurationService.getInstance()) {
            var configuration = ConfigurationService.getInstance().read();
            configuration.setEpgEnabled(enabled);
            ConfigurationService.getInstance().save(configuration);
        }
    }

}
