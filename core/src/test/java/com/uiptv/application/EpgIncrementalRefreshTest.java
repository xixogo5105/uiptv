package com.uiptv.application;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class EpgIncrementalRefreshTest extends DbBackedTest {
    @BeforeEach void enable() {
        EpgCacheRefreshService.stop();
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(true); config.setGlobalEpgSource("");
        ConfigurationService.getInstance().save(config);
    }
    @AfterEach void stop() { EpgCacheRefreshService.stop(); }
    private Account account() {
        var account=new Account(); account.setDbId("progress-account"); account.setType(AccountType.M3U8_LOCAL); return account;
    }
    private Channel channel() { var channel=new Channel(); channel.setChannelId("station"); channel.setName("Station"); return channel; }
    private String xml() {
        var formatter=DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC);
        return "<tv><programme channel='station' start='"+formatter.format(Instant.now().minusSeconds(60))+"' stop='"+formatter.format(Instant.now().plusSeconds(3600))+"'><title>Ready programme</title></programme></tv>";
    }
    @Test void readyAccountFeedIsVisibleWhileGlobalFallbackIsStillDownloading() throws Exception {
        var account=account(); var channel=channel();
        var source=tempDir.resolve("ready.xml"); Files.writeString(source,xml()); account.setEpg(source.toString());
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/slow",exchange -> {
            entered.countDown();
            try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException _) { Thread.currentThread().interrupt(); }
            byte[] data="<tv/>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
        }); server.start();
        var config=ConfigurationService.getInstance().read(); config.setGlobalEpgSource("http://127.0.0.1:"+server.getAddress().getPort()+"/slow"); ConfigurationService.getInstance().save(config);
        var worker=Executors.newSingleThreadExecutor();
        try {
            var task=worker.submit(() -> new EpgCacheRefreshService().refreshAccount(account,List.of(channel)));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals("Ready programme",new EpgCacheApplicationService().summaries(account,List.of(channel)).get(channel).getFirst().title());
            release.countDown(); task.get(5,TimeUnit.SECONDS);
            new EpgCacheRefreshService().refreshAccount(account,List.of(channel));
        } finally { release.countDown(); worker.shutdownNow(); server.stop(0); }
    }
}
