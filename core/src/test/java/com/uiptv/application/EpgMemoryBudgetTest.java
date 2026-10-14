package com.uiptv.application;

import com.uiptv.service.DbBackedTest;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EpgMemoryBudgetTest extends DbBackedTest {
    private Account account() {
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true);
        ConfigurationService.getInstance().save(config);
        Account account = new Account();
        account.setType(AccountType.M3U8_LOCAL);
        account.setAction(Account.AccountAction.itv);
        return account;
    }

    @Test void loadingASlowFeedDoesNotBlockMemoryOnlyCatalogReads() throws Exception {
        Account account = account();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/guide", exchange -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] xml = "<tv/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, xml.length);
            exchange.getResponseBody().write(xml);
            exchange.close();
        });
        server.start();
        account.setEpg("http://127.0.0.1:" + server.getAddress().getPort() + "/guide");
        Channel channel = new Channel(); channel.setChannelId("one"); channel.setName("One");
        EpgApplicationService service = new EpgApplicationService(); service.clearCache();
        Thread worker = Thread.ofVirtual().start(() -> service.cachedSchedule(account, channel));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var result = java.util.concurrent.CompletableFuture.runAsync(() -> service.enrichMemory(account, List.of(channel)));
            result.get(1, TimeUnit.SECONDS);
            assertNull(channel.getEpgNow());
        } finally {
            release.countDown(); worker.join(5000); server.stop(0); service.clearCache();
        }
    }

    @Test void compactStorageNormalizesUnsortedSchedulesAcrossChunks() throws Exception {
        var now = Instant.now();
        var formatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC);
        var xml = new StringBuilder("<tv>");
        for (int i = 599; i >= 0; i--) {
            xml.append("<programme channel='station' start='").append(formatter.format(now.plusSeconds(i * 60L)))
                    .append("'");
            if (i == 599) xml.append(" stop='").append(formatter.format(now.plusSeconds(600 * 60L))).append("'");
            xml.append("><title>Show ").append(i).append("</title><desc>").append("é".repeat(2048)).append("</desc></programme>");
        }
        xml.append("</tv>");
        var parsed = XmltvGuide.read(new java.io.ByteArrayInputStream(xml.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), now);
        assertTrue(parsed.forChannel("station", "", false).isEmpty(), "Reading a feed must not expand schedules eagerly");
        var schedule = parsed.forChannel("station", "");
        assertEquals(600, schedule.size());
        assertEquals("Show 0", schedule.getFirst().title());
        assertEquals("Show 599", schedule.getLast().title());
        assertEquals(60_000, schedule.getFirst().stop() - schedule.getFirst().start());
        assertEquals("é".repeat(2048), schedule.getFirst().description());
        assertTrue(parsed.estimatedBytes() < 1024 * 1024);
    }

    @Test void repeatedLargeFeedsStayCompactWithoutExpandingEveryChannel() throws Exception {
        Account account = account();
        EpgApplicationService service = new EpgApplicationService(); service.clearCache();
        var formatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC);
        String start = formatter.format(Instant.now().minusSeconds(60));
        String stop = formatter.format(Instant.now().plusSeconds(3600));
        StringBuilder xml = new StringBuilder("<tv>");
        for (int i = 0; i < 3000; i++) xml.append("<programme channel='c").append(i)
                .append("' start='").append(start).append("' stop='").append(stop)
                .append("'><title>Programme</title><desc>").append("x".repeat(2048)).append(i).append("</desc></programme>");
        xml.append("</tv>");
        Channel channel = new Channel(); channel.setChannelId("c0"); channel.setName("One");
        String first = null;
        try {
            for (int i = 0; i < 8; i++) {
                var source = tempDir.resolve("large-" + i + ".xml");
                Files.writeString(source, xml);
                if (first == null) first = source.toString();
                account.setEpg(source.toString());
                assertFalse(service.cachedSchedule(account, channel).isEmpty());
            }
            account.setEpg(first);
            assertFalse(service.memorySchedule(account, channel).isEmpty(), "Compressed feeds should fit without discarding useful regional sources");
        } finally { service.clearCache(); }
    }
}
