package com.uiptv.application;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpgSettingsApplicationServiceTest extends DbBackedTest {
    @Test
    void enableSwitchPersistsAndCanBeDisabled() {
        EpgSettingsApplicationService service = new EpgSettingsApplicationService();

        service.setEnabled(true);
        Configuration saved = ConfigurationService.getInstance().read();
        assertTrue(saved.isEpgEnabled());

        service.setEnabled(false);
        saved = ConfigurationService.getInstance().read();
        assertFalse(saved.isEpgEnabled());
    }
}
