package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EpgNameRulesTest extends DbBackedTest {
    @Test void duplicateProviderChannelsAcrossCategoriesDoNotRollBackRemapping() throws Exception {
        long now=System.currentTimeMillis(); var db=new EpgCacheDb();
        db.replaceSource("source",Map.of("station",List.of(new EpgProgramme("Programme","",now-1000,now+60_000))),Map.of(),Map.of(),now,now+60_000);
        var oldId=new EpgCacheDb.Identity("account","provider","old-rules");
        var newId=new EpgCacheDb.Identity("account","provider","new-rules");
        db.mapChannels(List.of(new EpgCacheDb.Match(oldId,"station","station","station",false,List.of("source"),"")),now);
        var match=new EpgCacheDb.Match(newId,"station","station","station",false,List.of("source"),"");
        assertTrue(db.mapChannels(List.of(match,match),now,true));
        assertEquals("Programme",db.schedule(newId,now,now-1000,now+60_000).getFirst().title());
        assertTrue(db.schedule(oldId,now,now-1000,now+60_000).isEmpty());
        try(var c=SQLConnection.connect();var q=c.createStatement();var rows=q.executeQuery("SELECT COUNT(*) FROM epg_channel_mapping")) {
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
        }
    }

    @Test void databaseEditsChangeMatchesAndInvalidateSavedMappingsWithoutDownloading() throws Exception {
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(true);
        config.setGlobalEpgSource("https://example.invalid/guide.xml"); ConfigurationService.getInstance().save(config);
        var account=new Account(); account.setDbId("91"); account.setType(AccountType.M3U8_LOCAL); account.setAction(Account.AccountAction.itv);
        var channel=new Channel(); channel.setChannelId("playlist-provider-id"); channel.setName("Station [Trial]");
        String source=EpgCacheApplicationService.sourceId(config.getGlobalEpgSource());
        long now=System.currentTimeMillis(); var db=new EpgCacheDb();
        db.replaceSource(source,Map.of("guide-station",List.of(new EpgProgramme("Named programme","",now-60_000,now+3_600_000))),
                Map.of("station","guide-station"),Map.of(),now,now+3_600_000);
        var sources=List.of(config.getGlobalEpgSource());
        db.mapChannels(EpgCacheApplicationService.matches(account,List.of(channel),sources),now);
        assertTrue(new EpgCacheApplicationService().summaries(account,List.of(channel)).get(channel).isEmpty());
        try(var c=SQLConnection.connect(); var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO epg_name_exclusion VALUES('trial','annotation',1)");
        }
        db.mapChannels(EpgCacheApplicationService.matches(account,List.of(channel),sources),now,true);
        assertEquals("Named programme",new EpgCacheApplicationService().summaries(account,List.of(channel)).get(channel).getFirst().title());
        try(var c=SQLConnection.connect(); var s=c.createStatement()) {
            s.executeUpdate("UPDATE epg_name_exclusion SET enabled=0 WHERE term='trial'");
        }
        assertTrue(new EpgCacheApplicationService().summaries(account,List.of(channel)).get(channel).isEmpty(),"Rule changes must invalidate stale mappings immediately");
        db.mapChannels(EpgCacheApplicationService.matches(account,List.of(channel),sources),now,true);
        assertTrue(new EpgCacheApplicationService().summaries(account,List.of(channel)).get(channel).isEmpty());
        new EpgApplicationService().clearCache();
        try(var c=SQLConnection.connect(); var s=c.createStatement(); var rows=s.executeQuery("SELECT enabled FROM epg_name_exclusion WHERE term='trial'")) {
            assertTrue(rows.next()); assertEquals(0,rows.getInt(1),"Refreshing EPG must preserve user rules");
        }
    }
    @Test void seededLabelsPreserveMeaningfulNamesAndAmbiguity() {
        var rules=new EpgNameRulesDb().read();
        assertEquals("b4u movies",rules.display("B4U Movies (504p) [Not 24/7]"));
        assertEquals("station hd",rules.display("Station HD (HEVC) [Backup]"));
        assertEquals("station",rules.quality("Station HD (HEVC) [Backup]"));
        assertEquals(rules.quality("Nick HD+"),rules.quality("Nick HD +"));
        assertEquals("",rules.quality("##### MOVIES HEVC #####"));
        for(String name:List.of("WWE Raw","24/7 Movies","Station (West)","Station +1","Station (Home)","UK|Station","HDTV"))
            assertEquals(XmltvGuide.normalize(name),rules.display(name));
        assertNotEquals(rules.quality("Station"),rules.quality("Station +1"));
        assertNotEquals(rules.quality("Station"),rules.quality("Station West"));
        assertFalse(rules.aliases(Map.of("Station HD","hd","Station SD","sd"),true).containsKey("station"));
        var literal=new EpgNameRules(List.of(new EpgNameRules.Rule("a.*","annotation")));
        assertEquals("station",literal.display("Station [a.*]"));
        assertEquals("station [abc]",literal.display("Station [abc]"));
    }
    @Test void migrationUpgradesAndDoesNotOverwriteDisabledOrCustomRules() throws Exception {
        try(var c=SQLConnection.connect();var s=c.createStatement()) {
            s.executeUpdate("DROP TABLE epg_name_exclusion");
            s.executeUpdate("DELETE FROM schema_migrations WHERE name='0212_add_epg_name_exclusions.sql'");
            DatabasePatchesUtils.applyPatches(c);
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM epg_name_exclusion")) { assertTrue(rows.next()); assertEquals(179,rows.getInt(1)); }
            s.executeUpdate("UPDATE epg_name_exclusion SET enabled=0 WHERE term='576p'");
            s.executeUpdate("INSERT INTO epg_name_exclusion VALUES('custom','annotation',1)");
            s.executeUpdate("DELETE FROM schema_migrations WHERE name='0212_add_epg_name_exclusions.sql'");
            DatabasePatchesUtils.applyPatches(c);
            try(var rows=s.executeQuery("SELECT enabled FROM epg_name_exclusion WHERE term='576p'")) { while(rows.next()) assertEquals(0,rows.getInt(1)); }
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM epg_name_exclusion WHERE term='custom'")) { assertTrue(rows.next()); assertEquals(1,rows.getInt(1)); }
        }
    }
}
