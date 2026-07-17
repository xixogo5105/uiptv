package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailEpisodesListUITest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void watchingNowDetailModeConstrainsEpisodeScrollHeight() throws Exception {
        EpisodeScrollSizing sizing = runOnFxThread(() -> {
            ThumbnailEpisodesListUI ui = new ThumbnailEpisodesListUI(testAccount(), "Series", "series-1", "category-1");
            ui.applyWatchingNowDetailStyling();
            contentStack(ui).resize(900, 480);
            updateWatchingNowEpisodeScrollHeight(ui);

            Region cardsFrame = cardsFrame(ui);
            ScrollPane cardsScroll = cardsScroll(ui);
            return new EpisodeScrollSizing(
                    cardsFrame.getPrefHeight(),
                    cardsFrame.getMaxHeight(),
                    cardsScroll.getPrefHeight(),
                    cardsScroll.getMaxHeight(),
                    cardsScroll.getPrefViewportHeight()
            );
        });

        assertTrue(sizing.framePrefHeight() > 140);
        assertTrue(sizing.framePrefHeight() < 480);
        assertEquals(sizing.framePrefHeight(), sizing.frameMaxHeight());
        assertEquals(sizing.framePrefHeight(), sizing.scrollPrefHeight());
        assertEquals(sizing.framePrefHeight(), sizing.scrollMaxHeight());
        assertEquals(sizing.framePrefHeight(), sizing.scrollPrefViewportHeight());
    }

    private static Account testAccount() {
        Account account = new Account();
        account.setDbId("account-1");
        account.setAccountName("Account");
        account.setType(AccountType.XTREME_API);
        return account;
    }

    private static StackPane contentStack(ThumbnailEpisodesListUI ui) throws Exception {
        Field field = BaseEpisodesListUI.class.getDeclaredField("contentStack");
        field.setAccessible(true);
        return (StackPane) field.get(ui);
    }

    private static Region cardsFrame(ThumbnailEpisodesListUI ui) throws Exception {
        Field field = ThumbnailEpisodesListUI.class.getDeclaredField("cardsFrame");
        field.setAccessible(true);
        return (Region) field.get(ui);
    }

    private static ScrollPane cardsScroll(ThumbnailEpisodesListUI ui) throws Exception {
        Field field = ThumbnailEpisodesListUI.class.getDeclaredField("cardsScroll");
        field.setAccessible(true);
        return (ScrollPane) field.get(ui);
    }

    private static void updateWatchingNowEpisodeScrollHeight(ThumbnailEpisodesListUI ui) throws Exception {
        Method method = ThumbnailEpisodesListUI.class.getDeclaredMethod("updateWatchingNowEpisodeScrollHeight");
        method.setAccessible(true);
        method.invoke(ui);
    }

    private record EpisodeScrollSizing(double framePrefHeight,
                                       double frameMaxHeight,
                                       double scrollPrefHeight,
                                       double scrollMaxHeight,
                                       double scrollPrefViewportHeight) {
    }
}
