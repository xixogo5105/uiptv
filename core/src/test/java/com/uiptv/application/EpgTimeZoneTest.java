package com.uiptv.application;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EpgTimeZoneTest extends DbBackedTest {
    @Test void absoluteTimesNeverReceiveAnAdditionalAccountOrDaylightSavingOffset() {
        for(String timestamp:List.of("2026-07-15T01:15:00Z","2026-01-15T01:15:00Z")) {
            long expected=Instant.parse(timestamp).toEpochMilli();
            for(String zone:List.of("Europe/London","Asia/Kolkata","America/New_York")) {
                assertEquals(expected,NativeEpgApplicationService.time(timestamp,ZoneId.of(zone)));
                assertEquals(expected,NativeEpgApplicationService.time(Long.toString(expected),ZoneId.of(zone)));
                assertEquals(expected,NativeEpgApplicationService.time(Long.toString(expected/1000),ZoneId.of(zone)));
            }
        }
        assertEquals(Instant.parse("2026-07-15T00:15:00Z").toEpochMilli(),NativeEpgApplicationService.time("2026-07-15 01:15:00+01:00",ZoneId.of("Asia/Kolkata")));
        assertEquals(Instant.parse("2026-07-15T00:15:00Z"),XmltvGuide.time("20260715011500 +0100"));
        assertEquals(Instant.parse("2026-01-15T01:15:00Z"),XmltvGuide.time("20260115011500 +0000"));
    }
    @Test void providerLocalDatesUseSourceZoneIncludingDstAndAcceptIsoLocalTime() {
        var london=ZoneId.of("Europe/London");
        assertEquals(Instant.parse("2026-07-15T00:15:00Z").toEpochMilli(),NativeEpgApplicationService.time("2026-07-15T01:15:00",london));
        assertEquals(Instant.parse("2026-01-15T01:15:00Z").toEpochMilli(),NativeEpgApplicationService.time("2026-01-15 01:15:00",london));
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),NativeEpgApplicationService.time("2026-10-25T01:30:00+01:00",london));
        assertEquals(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(),NativeEpgApplicationService.time("2026-10-25T01:30:00+00:00",london));
    }
    @Test void invalidPreferredTimestampsFallBackToValidDatesAndPortalEndTimes() {
        long start=System.currentTimeMillis()/1000*1000-60_000, stop=start+3_600_000;
        String json="{\"epg_listings\":[{\"title\":\"Actual movie\",\"start_timestamp\":0,\"start\":\""+Instant.ofEpochMilli(start)+"\",\"stop_timestamp\":\"null\",\"time_to\":"+stop/1000+"}]}";
        var result=NativeEpgApplicationService.parse(json,false,"Asia/Kolkata","station");
        assertEquals(1,result.size()); assertEquals(start,result.getFirst().start()); assertEquals(stop,result.getFirst().stop());
    }
    @Test void providerDeclaredTimezoneOverridesUtcFallbackForNaiveDates() {
        long start=System.currentTimeMillis()/1000*1000-60_000;
        var format=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));
        String json="{\"server_info\":{\"timezone\":\"Asia/Kolkata\"},\"epg_listings\":[{\"title\":\"Source programme\",\"start\":\""+format.format(Instant.ofEpochMilli(start))+"\",\"end\":\""+format.format(Instant.ofEpochMilli(start+3_600_000))+"\"}]}";
        var records=NativeEpgApplicationService.parse(json,false,"UTC","station");
        assertEquals(start,records.getFirst().start()); assertEquals(start+3_600_000,records.getFirst().stop());
    }
    @Test void accountTimezoneDoesNotAlterNativeRecordsOrCacheIdentity() {
        EpgCacheRefreshService.stop();
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(true); ConfigurationService.getInstance().save(config);
        var account=new Account(); account.setDbId("timezone-account"); account.setType(AccountType.XTREME_API); account.setTimezone("Europe/London");
        var channel=new Channel(); channel.setChannelId("station"); channel.setName("Station");
        long now=System.currentTimeMillis();
        try(var nativeService=mockConstruction(NativeEpgApplicationService.class,(service,context) -> when(service.schedule(any(),any())).thenReturn(List.of(new EpgProgramme("Current movie","",now-1000,now+3_600_000))))) {
            var cache=new EpgCacheApplicationService(); var refresh=new EpgCacheRefreshService();
            refresh.refreshAccount(account,List.of(channel)); assertFalse(cache.schedule(account,channel).isEmpty());
            String first=EpgCacheApplicationService.nativeSource(account,channel);
            account.setTimezone("Asia/Kolkata");
            assertEquals(first,EpgCacheApplicationService.nativeSource(account,channel));
            assertFalse(cache.schedule(account,channel).isEmpty(),"Account timezone must not change source timestamps");
            refresh.refreshAccount(account,List.of(channel)); assertFalse(cache.schedule(account,channel).isEmpty());
            assertEquals(1,nativeService.constructed().size());
        } finally { EpgCacheRefreshService.stop(); }
    }
}
