package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseWatchingNowUITest {
    @BeforeAll
    static void setupFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void buildPanel_returnsPanelWhenEpisodesMissing_and_marksLoading() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try {
                // Create a UI instance (thumbnail mode to exercise poster/metadata paths)
                ThumbnailWatchingNowUI ui = new ThumbnailWatchingNowUI();

                // Build minimal Account and SeriesWatchState
                Account account = new Account("test-account", "user", "pass", "http://example.com/", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
                account.setDbId("acct-1");
                account.setAccountName("Test Account");

                SeriesWatchState state = new SeriesWatchState();
                state.setAccountId(account.getDbId());
                state.setCategoryId("cat-1");
                state.setSeriesId("series-1");
                state.setUpdatedAt(System.currentTimeMillis());

                // Construct WatchingNowSeriesResolver.SeriesRow via reflection (private constructor)
                Class<?> seriesRowClass = Class.forName("com.uiptv.service.WatchingNowSeriesResolver$SeriesRow");
                Constructor<?> ctor = seriesRowClass.getDeclaredConstructor(Account.class, SeriesWatchState.class, String.class, String.class, String.class, boolean.class);
                ctor.setAccessible(true);
                Object seriesRow = ctor.newInstance(account, state, "Series Title", "", "cat-db", true);

                // Invoke private buildPanel on BaseWatchingNowUI via reflection
                Class<?> baseClass = Class.forName("com.uiptv.ui.BaseWatchingNowUI");
                Method buildPanel = baseClass.getDeclaredMethod("buildPanel", seriesRowClass);
                buildPanel.setAccessible(true);
                Object panel = buildPanel.invoke(ui, seriesRow);

                // Assert panel was returned and episodeLoadingVisible set true
                Field loadingField = panel.getClass().getDeclaredField("episodeLoadingVisible");
                loadingField.setAccessible(true);
                boolean loading = loadingField.getBoolean(panel);
                assertTrue(loading, "Expected episodeLoadingVisible to be true when episodes are missing");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
