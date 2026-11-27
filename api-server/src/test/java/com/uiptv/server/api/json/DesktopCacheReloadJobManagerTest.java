package com.uiptv.server.api.json;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.ReloadPhaseLoggerCallback;
import com.uiptv.testsupport.DbBackedTest;
import com.uiptv.util.AccountType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopCacheReloadJobManagerTest extends DbBackedTest {
    @Test
    void askPolicyPausesForDecisionThenContinuesTheActiveAccount() throws Exception {
        Account account = saveXtremeAccount("manager-ask-account", "http://same.test/xtreme");
        FakeCacheService cacheService = new FakeCacheService(true);
        DesktopCacheReloadJobManager manager = new DesktopCacheReloadJobManager(cacheService);

        DesktopCacheReloadJobManager.Job started = manager.start(List.of(account.getDbId()), "ask");
        String jobId = started.toJson().getString("jobId");
        JSONObject waiting = waitForStatus(manager, jobId, "waiting_for_decision");
        assertEquals("manager-ask-account", waiting.getJSONObject("pendingFailure").getString("accountName"));
        assertTrue(waiting.getJSONObject("pendingFailure").getBoolean("canIgnoreDomain"));
        assertTrue(manager.decide(jobId, "carryOn"));

        JSONObject completed = waitForStatus(manager, jobId, "completed");
        assertEquals("done", completed.getJSONArray("accounts").getJSONObject(0).getString("status"));
        assertEquals(1, cacheService.reloadCalls.get());
    }

    @Test
    void markBadAndIgnoreDomainSkipsLaterAccountsForTheSameProvider() throws Exception {
        Account first = saveXtremeAccount("same-domain-one", "http://same.test/xtreme");
        Account second = saveXtremeAccount("same-domain-two", "https://same.test/xtreme");
        FakeCacheService cacheService = new FakeCacheService(true);
        DesktopCacheReloadJobManager manager = new DesktopCacheReloadJobManager(cacheService);

        DesktopCacheReloadJobManager.Job started = manager.start(
                List.of(first.getDbId(), second.getDbId()), "markBadIgnoreDomain");
        JSONObject completed = waitForStatus(
                manager, started.toJson().getString("jobId"), "completed");

        assertEquals(1, cacheService.reloadCalls.get());
        assertEquals("failed", completed.getJSONArray("accounts").getJSONObject(0).getString("status"));
        assertEquals("skipped", completed.getJSONArray("accounts").getJSONObject(1).getString("status"));
    }

    private Account saveXtremeAccount(String name, String url) {
        Account account = new Account(
                name, "user", "private-password", url, null, null, null, null, null, null,
                AccountType.XTREME_API, null, url, false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private JSONObject waitForStatus(
            DesktopCacheReloadJobManager manager,
            String jobId,
            String targetStatus) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        JSONObject status = manager.get(jobId).toJson();
        while (System.nanoTime() < deadline) {
            status = manager.get(jobId).toJson();
            if (targetStatus.equals(status.getString("status"))) return status;
            Thread.sleep(5);
        }
        throw new AssertionError(
                "Timed out waiting for job status " + targetStatus + "; current=" + status);
    }

    private static final class FakeCacheService implements CacheService {
        private final AtomicInteger reloadCalls = new AtomicInteger();
        private final boolean emitGlobalFailure;

        private FakeCacheService(boolean emitGlobalFailure) {
            this.emitGlobalFailure = emitGlobalFailure;
        }

        @Override
        public void reloadCache(Account account, LoggerCallback logger) {
        }

        @Override
        public void reloadCacheWithPhases(Account account, ReloadPhaseLoggerCallback logger) throws IOException {
            reloadCalls.incrementAndGet();
            if (emitGlobalFailure) {
                logger.log("Global Xtreme channel lookup failed (IOException). Falling back to category fetch.",
                        Account.AccountAction.itv);
            }
            logger.log("Found Channels 5", Account.AccountAction.itv);
        }

        @Override
        public boolean verifyMacAddress(Account account, String macAddress) {
            return false;
        }

        @Override
        public int getChannelCountForAccount(String accountId) {
            return 0;
        }
    }
}