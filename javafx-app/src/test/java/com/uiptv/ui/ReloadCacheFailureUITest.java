package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.CacheService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReloadCacheFailureUITest extends DbBackedUiTest {
    @BeforeAll static void initialize() throws Exception { FxTestSupport.initJavaFx(); }

    @Test void categoryFallbackDoesNotMarkAccountBadBeforeFallbackRuns() throws Exception {
        var ui = runOnFxThread(ReloadCacheInline::new);
        var account = new Account(); account.setType(com.uiptv.util.AccountType.XTREME_API);
        try {
            for (String methodName : new String[]{"extractGlobalFailureReason", "extractIssueReason"}) {
                var method = ReloadCacheInline.class.getDeclaredMethod(methodName, Account.class, String.class);
                method.setAccessible(true);
                assertNull(method.invoke(ui, account, "Global Xtreme channel lookup failed (IOException). Falling back to category fetch."));
                assertNotNull(method.invoke(ui, account, "Global Xtreme channel lookup failed. Keeping existing cache."));
            }
        } finally {
            runOnFxThread(() -> {
                var dispose = ReloadCacheInline.class.getDeclaredMethod("disposeInline");
                dispose.setAccessible(true); dispose.invoke(ui); return null;
            });
        }
    }

    @Test void exhaustedHeapMarksAccountFailedAndStopsTheRun() throws Exception {
        var ui = runOnFxThread(ReloadCacheInline::new);
        CacheService service = mock(CacheService.class);
        var account = new Account();
        account.setDbId("memory-failure"); account.setAccountName("Memory failure fixture");
        account.setType(com.uiptv.util.AccountType.STALKER_PORTAL);
        account.setAction(Account.AccountAction.itv);
        doThrow(new OutOfMemoryError("Java heap space")).when(service).reloadCacheWithPhases(eq(account), any());
        Field serviceField = ReloadCacheInline.class.getDeclaredField("cacheService");
        serviceField.setAccessible(true); serviceField.set(ui, service);
        var reload = ReloadCacheInline.class.getDeclaredMethod("reloadSingleAccount", Account.class);
        reload.setAccessible(true);
        try {
            Object result = reload.invoke(ui, account);
            Field failed = result.getClass().getDeclaredField("failed"); failed.setAccessible(true);
            assertTrue(failed.getBoolean(result));
            Field stopped = ReloadCacheInline.class.getDeclaredField("stopRequested"); stopped.setAccessible(true);
            assertTrue(stopped.getBoolean(ui), "Memory exhaustion must stop rather than silently lose the worker");
            assertEquals(Account.AccountAction.itv, account.getAction());
        } finally {
            runOnFxThread(() -> {
                var dispose = ReloadCacheInline.class.getDeclaredMethod("disposeInline");
                dispose.setAccessible(true); dispose.invoke(ui); return null;
            });
        }
    }
}
