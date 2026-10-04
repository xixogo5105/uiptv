package com.uiptv.widget;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Configuration;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "Show bookmarks and account counts" appearance switch.
 *
 * <p>The reported bug was that counts stayed hidden after the switch was turned back on and saved,
 * and only reappeared after restarting the application. Hiding the badges must therefore not throw
 * away the cached counts, otherwise re-enabling the switch has nothing to restore.</p>
 */
class AppHeaderNavigationBadgeCountsTest extends DbBackedUiTest {

    private static final int EXPECTED_BOOKMARKS = 2;
    private static final int EXPECTED_ACCOUNTS = 1;
    private static final long BADGE_WAIT_TIMEOUT_MS = 5_000L;

    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @BeforeEach
    void seedCatalog() {
        AccountService.getInstance().save(new Account(
                "acc-badge", "user", "pass", "http://unused", "00:11:22:33:44:55",
                null, null, null, null, null, AccountType.M3U8_URL, null,
                "http://unused/list.m3u8", false));
        BookmarkService bookmarkService = BookmarkService.getInstance();
        bookmarkService.save(new Bookmark("acc-badge", "Sports", "ch-1", "Channel One", "cmd", "http://portal", "cat-1"));
        bookmarkService.save(new Bookmark("acc-badge", "Sports", "ch-2", "Channel Two", "cmd", "http://portal", "cat-1"));
    }

    @AfterEach
    void resetSharedState() {
        AppNavigationController.reset();
        AppHeaderNavigation.setCachedBadgeCountsForTest(-1, -1);
    }

    @Test
    void reEnablingCountsAfterDisableShowsThemWithoutAppRestart() throws Exception {
        persistBadgeCountDisplay(false);

        AppHeaderNavigation navigation = FxTestSupport.runOnFxThread(
                () -> new AppHeaderNavigation(new Label("UIPTV")));
        refreshBadges(navigation);
        assertBadges(navigation, false);

        // Turning the switch back on and saving must restore the counts immediately.
        persistBadgeCountDisplay(true);
        refreshBadges(navigation);

        assertTrue(waitForBadgesVisible(navigation),
                "Counts must reappear as soon as the setting is saved, without an app restart");
        assertBadges(navigation, true);
    }

    @Test
    void countsAppearImmediatelyWhenEnabledFromStartup() throws Exception {
        persistBadgeCountDisplay(true);

        AppHeaderNavigation navigation = FxTestSupport.runOnFxThread(
                () -> new AppHeaderNavigation(new Label("UIPTV")));

        assertTrue(waitForBadgesVisible(navigation), "Counts must show on startup when the setting is enabled");
        assertBadges(navigation, true);
    }

    @Test
    void hiddenCountsAreRestoredWithoutWaitingForDatabaseReread() throws Exception {
        persistBadgeCountDisplay(true);

        AppHeaderNavigation navigation = FxTestSupport.runOnFxThread(
                () -> new AppHeaderNavigation(new Label("UIPTV")));
        assertTrue(waitForBadgesVisible(navigation));

        persistBadgeCountDisplay(false);
        refreshBadges(navigation);
        assertBadges(navigation, false);

        // Give any reload that was already in flight time to finish; it must not resurrect the
        // badges that the user just switched off.
        Thread.sleep(250L);
        FxTestSupport.waitForFxEvents();
        assertBadges(navigation, false);

        // Re-enabling must repaint from the counts already in memory; it must not depend on the
        // asynchronous database reload having completed first.
        persistBadgeCountDisplay(true);
        refreshBadges(navigation);

        assertBadges(navigation, true);
    }

    private static void refreshBadges(AppHeaderNavigation navigation) throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            navigation.refreshBadgeCounts();
            return null;
        });
    }

    private static void persistBadgeCountDisplay(boolean enabled) {
        ConfigurationService configurationService = ConfigurationService.getInstance();
        Configuration configuration = configurationService.read();
        configuration.setShowBookmarkAndAccountCounts(enabled);
        configurationService.save(configuration);
    }

    private static boolean waitForBadgesVisible(AppHeaderNavigation navigation) throws Exception {
        return waitUntil(() -> FxTestSupport.runOnFxThread(() ->
                navigation.getBookmarksBadge().isVisible() && navigation.getAccountsBadge().isVisible()));
    }

    private static boolean waitUntil(CheckedBooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + BADGE_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            FxTestSupport.waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25L);
        }
        return condition.getAsBoolean();
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    private static void assertBadges(AppHeaderNavigation navigation, boolean expectVisible) throws Exception {
        Label bookmarksBadge = FxTestSupport.runOnFxThread(navigation::getBookmarksBadge);
        Label accountsBadge = FxTestSupport.runOnFxThread(navigation::getAccountsBadge);

        if (!expectVisible) {
            assertFalse(FxTestSupport.runOnFxThread(bookmarksBadge::isVisible),
                    "Bookmarks badge must stay hidden while the setting is off");
            assertFalse(FxTestSupport.runOnFxThread(accountsBadge::isVisible),
                    "Account badge must stay hidden while the setting is off");
            return;
        }

        assertTrue(FxTestSupport.runOnFxThread(bookmarksBadge::isVisible), "Bookmarks badge must be visible");
        assertTrue(FxTestSupport.runOnFxThread(accountsBadge::isVisible), "Account badge must be visible");
        assertEquals(String.valueOf(EXPECTED_BOOKMARKS), FxTestSupport.runOnFxThread(bookmarksBadge::getText));
        assertEquals(String.valueOf(EXPECTED_ACCOUNTS), FxTestSupport.runOnFxThread(accountsBadge::getText));
    }
}
