package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.CacheService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test void disabledCacheIsReportedAsSkippedAndNeverConsideredProblematic() throws Exception {
        var ui = runOnFxThread(ReloadCacheInline::new);
        var account = new Account();
        account.setDbId("disabled-cache");
        account.setAccountName("Disabled cache fixture");
        account.setType(com.uiptv.util.AccountType.M3U8_LOCAL);
        account.setCacheEnabled(false);

        try {
            Method reload = ReloadCacheInline.class.getDeclaredMethod("reloadSingleAccount", Account.class);
            reload.setAccessible(true);
            Object result = reload.invoke(ui, account);
            Field skipped = result.getClass().getDeclaredField("skipped");
            skipped.setAccessible(true);
            Field failed = result.getClass().getDeclaredField("failed");
            failed.setAccessible(true);
            assertTrue(skipped.getBoolean(result));
            assertFalse(failed.getBoolean(result));

            Method buildSummary = ReloadCacheInline.class.getDeclaredMethod("buildSummaryStatus",
                    int.class, boolean.class, List.class, boolean.class, boolean.class);
            buildSummary.setAccessible(true);
            Object summary = buildSummary.invoke(ui, 0, false, List.of(), true, true);
            Field level = summary.getClass().getDeclaredField("level");
            level.setAccessible(true);
            assertEquals("SKIPPED", ((Enum<?>) level.get(summary)).name());

            Method collect = ReloadCacheInline.class.getDeclaredMethod("collectProblematicAccounts", List.class, Map.class);
            collect.setAccessible(true);
            Map<String, Object> summaries = new LinkedHashMap<>();
            summaries.put(account.getDbId(), summary);
            Map<?, ?> problematic = (Map<?, ?>) collect.invoke(ui, List.of(account), summaries);
            assertTrue(problematic.isEmpty(), "Skipped accounts must not be offered for deletion");
        } finally {
            runOnFxThread(() -> {
                var dispose = ReloadCacheInline.class.getDeclaredMethod("disposeInline");
                dispose.setAccessible(true); dispose.invoke(ui); return null;
            });
        }
    }

    @Test void skippedAccountPanelUsesNeutralStatusStyle() throws Exception {
        var account = new Account();
        account.setAccountName("Disabled cache fixture");
        account.setType(com.uiptv.util.AccountType.M3U8_LOCAL);
        Class<?> panelType = Class.forName("com.uiptv.ui.ReloadCacheInline$AccountLogPanel");
        var constructor = panelType.getDeclaredConstructor(Account.class);
        constructor.setAccessible(true);
        Object panel = runOnFxThread(() -> constructor.newInstance(account));
        try {
            Class<?> statusType = Class.forName("com.uiptv.ui.ReloadCacheInline$AccountRunStatus");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object skippedStatus = Enum.valueOf((Class<? extends Enum>) statusType.asSubclass(Enum.class), "SKIPPED");
            Method setStatus = panelType.getDeclaredMethod("setStatus", statusType, Integer.class);
            setStatus.setAccessible(true);
            runOnFxThread(() -> { setStatus.invoke(panel, skippedStatus, null); return null; });

            Field labelField = panelType.getDeclaredField("runStatusLabel");
            labelField.setAccessible(true);
            javafx.scene.control.Label label = (javafx.scene.control.Label) labelField.get(panel);
            assertTrue(runOnFxThread(() -> label.getStyleClass().contains("reload-status-skipped")));
            assertEquals(com.uiptv.util.I18n.tr("reloadSummaryLevelSkipped"), runOnFxThread(label::getText));
        } finally {
            Method dispose = panelType.getDeclaredMethod("dispose");
            dispose.setAccessible(true);
            runOnFxThread(() -> { dispose.invoke(panel); return null; });
        }
    }
}
