package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerEpgCoverageTest extends DbBackedTest {
    @Test
    void invalidQueriesAndDisabledGuideReturnEmptyResults() {
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(false);
        ConfigurationService.getInstance().save(config);
        var service = new PlayerEpgApplicationService();
        assertTrue(service.lookupGuide(null).programmes().isEmpty());
        assertEquals("", service.lookup(new PlayerEpgApplicationService.Query("a", "b", "c", "")).channelName());
        assertTrue(service.lookupSchedule(new PlayerEpgApplicationService.Query("a", "b", "c", "")).isEmpty());
    }

    @Test
    void channelLookupResolvesDbIdsAndSummarizesCurrentNextAndMissingPrograms() {
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true);
        ConfigurationService.getInstance().save(config);
        Account account = new Account("player", "u", "p", "http://provider", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://provider", false);
        account.setAction(Account.AccountAction.itv);
        Channel channel = new Channel();
        channel.setChannelId("provider-channel");
        channel.setName("Sports One");

        long now = System.currentTimeMillis();
        List<EpgProgramme> programmes = List.of(
                new EpgProgramme("Current", "", now - 1_000, now + 10_000),
                new EpgProgramme("Next", "", now + 11_000, now + 20_000));
        try (var construction = mockConstruction(EpgApplicationService.class,
                (mock, context) -> when(mock.schedule(any(), any())).thenReturn(programmes))) {
            var service = new PlayerEpgApplicationService();
            var result = service.current(account, channel);
            assertEquals("Sports One", result.channelName());
            assertEquals("Current", result.now().title());
            assertEquals("Next", result.next().title());
            assertEquals("AVAILABLE", result.guideStatus());
            assertEquals(2, service.schedule(account, channel).size());
        }
        try (var construction = mockConstruction(EpgApplicationService.class,
                (mock, context) -> when(mock.schedule(any(), any())).thenReturn(List.of()))) {
            assertEquals("NO_MATCH", new PlayerEpgApplicationService().current(account, channel).guideStatus());
        }
    }
}
