package com.uiptv.db;

import com.uiptv.application.EpgProgramme;
import com.uiptv.application.EpgCacheApplicationService;
import com.uiptv.application.EpgTerritory;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EpgTerritoryMappingTest extends DbBackedTest {
    private final EpgCacheDb db = new EpgCacheDb();

    private EpgProgramme programme(String title, long now) {
        return new EpgProgramme(title, "", now - 1_000, now + 60_000);
    }

    private void source(String id, String country, String guide, String title, long now) throws Exception {
        db.replaceSource(id, Map.of(guide, List.of(programme(title, now))), Map.of("example sports", guide), Map.of(), now, now + 60_000);
        try (Connection connection = SQLConnection.connect(); var insert = connection.prepareStatement("INSERT INTO epg_shared_source VALUES(?,?,?,?)")) {
            insert.setString(1, id); insert.setInt(2, 40); insert.setString(3, country); insert.setString(4, ""); insert.executeUpdate();
        }
    }

    @Test
    void persistsOnlyTheSourceMatchingAnExplicitChannelTerritory() throws Exception {
        long now = System.currentTimeMillis();
        source("regional-in", "IN", "example.sports.in", "India schedule", now);
        source("regional-gb", "GB", "example.sports.gb", "United Kingdom schedule", now);
        var identity = new EpgCacheDb.Identity("account", "provider", "territory-signature");
        var match = new EpgCacheDb.Match(identity, "", "Example Sports (UK-HD)", "", false,
                List.of("regional-in", "regional-gb"), "");

        assertTrue(db.mapChannels(List.of(match), now));
        assertEquals("United Kingdom schedule", db.schedule(identity, now - 2_000, now + 60_000).getFirst().title());
    }

    @Test
    void refusesToGuessWhenTheUnqualifiedNameHasDifferentRegionalSchedules() throws Exception {
        long now = System.currentTimeMillis();
        source("regional-in", "IN", "example.sports.in", "India schedule", now);
        source("regional-gb", "GB", "example.sports.gb", "United Kingdom schedule", now);
        var identity = new EpgCacheDb.Identity("account", "provider", "ambiguous-signature");
        var match = new EpgCacheDb.Match(identity, "", "Example Sports", "", false,
                List.of("regional-in", "regional-gb"), "");

        assertFalse(db.mapChannels(List.of(match), now));
        assertTrue(db.schedule(identity, now - 2_000, now + 60_000).isEmpty());
    }

    @Test
    void languagePrefixDoesNotSelectAnIndianSchedule() throws Exception {
        long now = System.currentTimeMillis();
        source("regional-in", "IN", "example.sports.in", "India schedule", now);
        source("regional-gb", "GB", "example.sports.gb", "United Kingdom schedule", now);
        var identity = new EpgCacheDb.Identity("account", "provider", "language-signature");
        var match = new EpgCacheDb.Match(identity, "", "HINDI | Example Sports", "", false,
                List.of("regional-in", "regional-gb"), "");

        assertFalse(db.mapChannels(List.of(match), now));
        assertTrue(db.schedule(identity, now - 2_000, now + 60_000).isEmpty());
    }

    @Test
    void territoryRegistryFingerprintIsStableAndAvailableToCacheSignatures() {
        assertFalse(EpgTerritory.fingerprintValue().isBlank());
        assertEquals(EpgTerritory.fingerprintValue(), EpgTerritory.fingerprintValue());
    }

    @Test
    void exactGuideIdentityOverridesConflictingNativeScheduleWithoutChangingUtcTimes() throws Exception {
        long now = System.currentTimeMillis();
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true);
        config.setEpgMappingsJson("{}");
        ConfigurationService.getInstance().save(config);

        var account = new Account();
        account.setDbId("native-account");
        account.setType(AccountType.STALKER_PORTAL);
        account.setAction(Account.AccountAction.itv);
        account.setTimezone("Europe/London");
        var channel = new Channel();
        channel.setChannelId("155970");
        channel.setName("HINDI| STAR GOLD SELECT FHD");
        channel.setExtraJson("{\"xmltv_id\":\"StarGoldSelect.in\"}");

        db.replaceSource("native", Map.of(channel.getChannelId(), List.of(
                programme("Wrong native schedule", now))), Map.of(), Map.of(), now, now + 60_000);
        var identity = EpgCacheApplicationService.identity(account, channel, config);
        assertTrue(db.mapChannels(List.of(new EpgCacheDb.Match(identity, channel.getChannelId(), channel.getName(), "",
                false, List.of("native"), "native")), now));

        db.replaceSource("shared-in", Map.of("STAR.GOLD.SELECT.in", List.of(
                programme("Chhapaak", now))),
                Map.of("star gold select", "STAR.GOLD.SELECT.in"), Map.of(), now, now + 60_000);
        try (Connection connection = SQLConnection.connect(); var insert = connection.prepareStatement("INSERT INTO epg_shared_source VALUES(?,?,?,?)")) {
            insert.setString(1, "shared-in");
            insert.setInt(2, 40);
            insert.setString(3, "IN");
            insert.setString(4, "");
            insert.executeUpdate();
        }
        SharedEpgCacheDb.invalidate();

        var summaries = new EpgCacheApplicationService().summaries(account, List.of(channel)).get(channel);
        assertEquals("Chhapaak", summaries.getFirst().title());
        assertEquals(now - 1_000, summaries.getFirst().start());
        assertEquals("Chhapaak", new EpgCacheApplicationService().schedule(account, channel).getFirst().title());
    }

    @Test
    void placeholderGuideIdentityFallsBackToTheCountryAwareSharedName() throws Exception {
        long now = System.currentTimeMillis();
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true);
        config.setEpgMappingsJson("{}");
        ConfigurationService.getInstance().save(config);

        var account = new Account();
        account.setDbId("placeholder-account");
        account.setType(AccountType.STALKER_PORTAL);
        account.setAction(Account.AccountAction.itv);
        account.setTimezone("Europe/London");
        var channel = new Channel();
        channel.setChannelId("74720");
        channel.setName("STAR GOLD THRILLS");
        channel.setExtraJson("{\"xmltv_id\":\"DEFAULT\"}");

        db.replaceSource("native-placeholder", Map.of(channel.getChannelId(), List.of(
                programme("No information available", now))), Map.of(), Map.of(), now, now + 60_000);
        var identity = EpgCacheApplicationService.identity(account, channel, config);
        assertTrue(db.mapChannels(List.of(new EpgCacheDb.Match(identity, channel.getChannelId(), channel.getName(), "",
                false, List.of("native-placeholder"), "native-placeholder")), now));

        db.replaceSource("shared-in", Map.of("STAR.GOLD.THRILLS.in", List.of(
                programme("Resident Evil: The Final Chapter", now))),
                Map.of("star gold thrills", "STAR.GOLD.THRILLS.in"), Map.of(), now, now + 60_000);
        try (Connection connection = SQLConnection.connect(); var insert = connection.prepareStatement("INSERT INTO epg_shared_source VALUES(?,?,?,?)")) {
            insert.setString(1, "shared-in");
            insert.setInt(2, 40);
            insert.setString(3, "IN");
            insert.setString(4, "");
            insert.executeUpdate();
        }
        SharedEpgCacheDb.invalidate();

        assertEquals("Resident Evil: The Final Chapter",
                new EpgCacheApplicationService().summaries(account, List.of(channel)).get(channel).getFirst().title());
    }

    @Test
    void stalkerGuideIdEchoFallsBackToTheSharedChannelName() throws Exception {
        long now = System.currentTimeMillis();
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true);
        config.setEpgMappingsJson("{}");
        ConfigurationService.getInstance().save(config);

        var account = new Account();
        account.setDbId("echo-account");
        account.setType(AccountType.STALKER_PORTAL);
        account.setAction(Account.AccountAction.itv);
        account.setTimezone("Europe/London");
        var channel = new Channel();
        channel.setChannelId("310");
        channel.setName("Star Gold HD");
        channel.setExtraJson("{\"xmltv_id\":\"310\"}");

        db.replaceSource("native-echo", Map.of(channel.getChannelId(), List.of(
                programme("Movie", now))), Map.of(), Map.of(), now, now + 60_000);
        var identity = EpgCacheApplicationService.identity(account, channel, config);
        assertTrue(db.mapChannels(List.of(new EpgCacheDb.Match(identity, channel.getChannelId(), channel.getName(), "",
                false, List.of("native-echo"), "native-echo")), now));

        db.replaceSource("shared-in", Map.of("STAR.GOLD.HD.in", List.of(
                programme("Son of Sardaar 2", now))),
                Map.of("star gold hd", "STAR.GOLD.HD.in"), Map.of(), now, now + 60_000);
        try (Connection connection = SQLConnection.connect(); var insert = connection.prepareStatement("INSERT INTO epg_shared_source VALUES(?,?,?,?)")) {
            insert.setString(1, "shared-in");
            insert.setInt(2, 40);
            insert.setString(3, "IN");
            insert.setString(4, "");
            insert.executeUpdate();
        }
        SharedEpgCacheDb.invalidate();

        assertEquals("Son of Sardaar 2",
                new EpgCacheApplicationService().summaries(account, List.of(channel)).get(channel).getFirst().title());
    }

    @Test
    void m3uGenericNativeProgrammeYieldsToMeaningfulSharedProgramme() throws Exception {
        long now = System.currentTimeMillis();
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true);
        config.setEpgMappingsJson("{}");
        ConfigurationService.getInstance().save(config);

        var account = new Account();
        account.setDbId("m3u-account");
        account.setType(AccountType.M3U8_URL);
        account.setAction(Account.AccountAction.itv);
        account.setTimezone("Europe/London");
        var channel = new Channel();
        channel.setChannelId("IN39000187Z");
        channel.setName("B4U Kadak");
        channel.setExtraJson("");

        db.replaceSource("m3u-xmltv", Map.of(channel.getChannelId(), List.of(
                programme("Movie", now))), Map.of(), Map.of(), now, now + 60_000);
        var identity = EpgCacheApplicationService.identity(account, channel, config);
        assertTrue(db.mapChannels(List.of(new EpgCacheDb.Match(identity, channel.getChannelId(), channel.getName(), "",
                false, List.of("m3u-xmltv"), "")), now));

        db.replaceSource("shared-in", Map.of("B4U.KADAK.in", List.of(
                programme("Ahankaar", now))), Map.of("b4u kadak", "B4U.KADAK.in"), Map.of(), now, now + 60_000);
        try (Connection connection = SQLConnection.connect(); var insert = connection.prepareStatement("INSERT INTO epg_shared_source VALUES(?,?,?,?)")) {
            insert.setString(1, "shared-in");
            insert.setInt(2, 40);
            insert.setString(3, "IN");
            insert.setString(4, "");
            insert.executeUpdate();
        }
        SharedEpgCacheDb.invalidate();

        assertEquals("Ahankaar",
                new EpgCacheApplicationService().summaries(account, List.of(channel)).get(channel).getFirst().title());
    }
}
