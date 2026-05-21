package com.uiptv.application;

import com.uiptv.db.SQLConnection;
import com.uiptv.model.Configuration;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.SeriesWatchStateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigurationApplicationServiceTest extends DbBackedTest {

    @Test
    void configurationDatabaseAndServerFacadeMethods_delegateSafely() {
        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        Configuration configuration = new Configuration();
        configuration.setServerPort("9999");

        service.saveConfiguration(configuration);
        assertEquals("9999", service.readConfiguration().getServerPort());

        assertEquals(SQLConnection.getDatabasePath(), service.getDatabasePath());
        assertFalse(service.isServerRunning());
        service.stopServer();
    }

    @Test
    void clearWatchingNowState_removesSeriesAndVodState() {
        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                new com.uiptv.model.Account("cfg-watch", "u", "p", "http://test", null, null, null, null, null, null,
                        com.uiptv.util.AccountType.XTREME_API, null, "http://test", false),
                "cat", "series", "ep", "Episode", "1", "1"
        );

        service.clearWatchingNowState();

        assertEquals(0, SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount("").size());
    }
}
