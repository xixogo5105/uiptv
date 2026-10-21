package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import com.uiptv.widget.PlayMenuButton;
import com.uiptv.widget.ResponsiveCardGrid;
import com.uiptv.widget.PlayingCardIndicator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.PickResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelListUIContextMenuTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void disposeStopsGuideClockAndPendingRefreshes() throws Exception {
        runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            Field supportField = ChannelListUI.class.getDeclaredField("cardEpg"); supportField.setAccessible(true);
            Object support = supportField.get(ui);
            Field clockField = CardEpgSupport.class.getDeclaredField("clock"); clockField.setAccessible(true);
            var clock = (javafx.animation.Timeline) clockField.get(support);
            Field pulseField = CardEpgSupport.class.getDeclaredField("pulse"); pulseField.setAccessible(true);
            var pulse = (javafx.animation.PauseTransition) pulseField.get(support);
            clock.play(); pulse.play();
            ui.dispose();
            assertEquals(javafx.animation.Animation.Status.STOPPED, clock.getStatus());
            assertEquals(javafx.animation.Animation.Status.STOPPED, pulse.getStatus());
            Field executorField = ChannelListUI.class.getDeclaredField("refreshExecutor"); executorField.setAccessible(true);
            assertTrue(((java.util.concurrent.ExecutorService) executorField.get(ui)).isShutdown());
            return null;
        });
    }

    @Test
    void liveChannelContextMenuShowsPlayersBeforeBookmarkMenu() throws Exception {
        List<String> menuOrder = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            ChannelListUI.ChannelItem item = channelItem();
            ContextMenu menu = createChannelContextMenu(ui, item);
            return menu.getItems().stream()
                    .map(ChannelListUIContextMenuTest::menuItemText)
                    .toList();
        });

        assertEquals(I18n.tr("autoEmbeddedPlayer"), menuOrder.get(0));
        assertEquals(I18n.tr("configDefaultWebBrowserPlayer"), menuOrder.get(1));
        assertEquals(I18n.tr("autoPlayer1"), menuOrder.get(2));
        assertEquals(I18n.tr("autoPlayer2"), menuOrder.get(3));
        assertEquals(I18n.tr("autoPlayer3"), menuOrder.get(4));
        assertEquals("<separator>", menuOrder.get(5));
        assertEquals(I18n.tr("autoBookmark"), menuOrder.get(6));
    }

    @Test
    void seriesContextMenuDoesNotOfferBookmarkMenu() throws Exception {
        List<String> menuOrder = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Series", "series", Account.AccountAction.series);
            ContextMenu menu = createChannelContextMenu(ui, seriesItemWithoutCommand());
            return menu.getItems().stream()
                    .map(ChannelListUIContextMenuTest::menuItemText)
                    .toList();
        });

        assertEquals(List.of(I18n.tr("autoViewEpisodes"), I18n.tr("autoWatchingNow")), menuOrder);
        assertFalse(menuOrder.contains(I18n.tr("autoBookmark")));
    }

    @Test
    void seriesContextMenuShowsRemoveWatchingNowWhenWatched() throws Exception {
        List<String> menuOrder = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Series", "series", Account.AccountAction.series);
            ChannelListUI.ChannelItem item = seriesItemWithoutCommand();
            item.getChannel().setWatched(true);
            ContextMenu menu = createChannelContextMenu(ui, item);
            return menu.getItems().stream()
                    .map(ChannelListUIContextMenuTest::menuItemText)
                    .toList();
        });

        assertEquals(List.of(I18n.tr("autoViewEpisodes"), I18n.tr("autoRemoveWatchingNow")), menuOrder);
        assertFalse(menuOrder.contains(I18n.tr("autoBookmark")));
    }

    @Test
    void vodContextMenuUsesWatchingNowInsteadOfBookmarkMenu() throws Exception {
        List<String> menuOrder = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Movies", "movies", Account.AccountAction.vod);
            ContextMenu menu = createChannelContextMenu(ui, channelItem());
            return menu.getItems().stream()
                    .map(ChannelListUIContextMenuTest::menuItemText)
                    .toList();
        });

        assertTrue(menuOrder.contains(I18n.tr("autoWatchingNow")));
        assertFalse(menuOrder.contains(I18n.tr("autoBookmark")));
    }

    @Test
    void plainTextChannelCardKeepsDrmBadgeVisible() throws Exception {
        BadgeSnapshot badge = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            ChannelListUI.ChannelItem item = channelItem();
            item.getChannel().setDrmType("clearkey");
            Region card = createPlainTextChannelCard(ui, item);
            Label label = findLabelByStyle(card, "drm-badge");
            return label == null ? null : new BadgeSnapshot(label.getText(), label.isVisible(), label.isManaged());
        });

        assertNotNull(badge);
        assertEquals(I18n.tr("autoDrm"), badge.text());
        assertTrue(badge.visible());
        assertTrue(badge.managed());
    }

    @Test
    void channelCardShowsBookmarkMarkerWhenBookmarked() throws Exception {
        boolean markerPresent = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            setBooleanField(ui, "thumbnailsEnabled", true);
            Region card = createChannelCard(ui, channelItem(true));
            return findNodeByStyle(card, "channel-bookmark-icon") != null;
        });

        assertTrue(markerPresent);
    }

    @Test
    void hoverMenuPrecedesBookmarkInEveryChannelCardLayout() throws Exception {
        boolean correctlyOrdered = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            ChannelListUI.ChannelItem item = channelItem(true);

            Region thumbnailCard = createChannelCard(ui, item);
            Node thumbnailAction = findNodeByType(thumbnailCard, PlayMenuButton.class);
            Node thumbnailBookmark = findNodeByStyle(thumbnailCard, "channel-bookmark-icon");
            HBox titleActions = thumbnailAction == null || !(thumbnailAction.getParent() instanceof HBox parent)
                    ? null : parent;
            ResponsiveCardGrid<ChannelListUI.ChannelItem> grid = new ResponsiveCardGrid<>(ignored -> thumbnailCard, true);
            grid.setItems(FXCollections.observableArrayList(item));
            Scene scene = new Scene(grid, 420, 180);
            ((PlayMenuButton) thumbnailAction).reveal();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            Node recordIndicator = findNodeByStyle(thumbnailCard, "playing-card-indicator");
            if (recordIndicator != null) {
                recordIndicator.setManaged(true);
                recordIndicator.setVisible(true);
            }
            scene.getRoot().layout();

            HBox plainCard = (HBox) createPlainTextChannelCard(ui, item);
            Node plainAction = findNodeByType(plainCard, PlayMenuButton.class);
            Node plainBookmark = findNodeByStyle(plainCard, "channel-bookmark-icon");

            HBox drawerRow = (HBox) createDrawerChannelRow(ui, item);
            Node drawerAction = findNodeByType(drawerRow, PlayMenuButton.class);
            Node drawerBookmark = findNodeByStyle(drawerRow, "channel-bookmark-icon");
            HBox drawerBadges = drawerBookmark == null ? null : (HBox) drawerBookmark.getParent();
            return titleActions != null
                    && titleActions.getChildren().indexOf(thumbnailAction) < titleActions.getChildren().indexOf(thumbnailBookmark)
                    && recordIndicator != null
                    && titleActions.getChildren().indexOf(thumbnailAction) < titleActions.getChildren().indexOf(recordIndicator)
                    && titleActions.getChildren().indexOf(recordIndicator) < titleActions.getChildren().indexOf(thumbnailBookmark)
                    && horizontallyOrdered(thumbnailAction, recordIndicator, thumbnailBookmark)
                    && verticallyCentered(thumbnailAction, recordIndicator, thumbnailBookmark)
                    && plainAction.getParent() instanceof HBox plainActions
                    && plainActions.getStyleClass().contains("channel-card-title-actions")
                    && plainActions.getChildren().indexOf(plainAction) < plainActions.getChildren().indexOf(plainBookmark)
                    && drawerBadges != null
                    && drawerBadges.getChildren().indexOf(drawerAction) < drawerBadges.getChildren().indexOf(drawerBookmark);
        });

        assertTrue(correctlyOrdered);
    }

    @Test
    void channelCardHidesRepeatedSubtitle() throws Exception {
        LabelSnapshot subtitle = runOnFxThread(() -> {
            Account account = new Account();
            account.setAccountName("Sports");
            ChannelListUI ui = new ChannelListUI(account, "Sports", "sports", Account.AccountAction.itv);
            setBooleanField(ui, "thumbnailsEnabled", true);
            Region card = createChannelCard(ui, channelItem());
            Label label = findLabelByStyle(card, "bookmark-channel-account");
            return label == null ? null : new LabelSnapshot(label.getText(), label.isVisible(), label.isManaged());
        });

        assertNotNull(subtitle);
        assertEquals("", subtitle.text());
        assertFalse(subtitle.visible());
        assertFalse(subtitle.managed());
    }

    @Test
    void plainTextChannelCardShowsBookmarkMarkerWhenBookmarked() throws Exception {
        boolean markerPresent = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            Region card = createPlainTextChannelCard(ui, channelItem(true));
            return findNodeByStyle(card, "channel-bookmark-icon") != null;
        });

        assertTrue(markerPresent);
    }

    @Test
    void plainTextChannelCardPlacesBroadcastAtTheTrailingEdge() throws Exception {
        boolean correctlyPlaced = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            ChannelListUI.ChannelItem item = channelItem(true);
            Region card = createPlainTextChannelCard(ui, item);
            ResponsiveCardGrid<ChannelListUI.ChannelItem> grid = new ResponsiveCardGrid<>(_ -> card, true);
            grid.setItems(FXCollections.observableArrayList(item));
            Scene scene = new Scene(grid, 420, 180);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            HBox row = (HBox) card;
            Label title = findLabelByStyle(card, "bookmark-channel-title");
            PlayingCardIndicator indicator = findNodeByStyle(card, "playing-card-indicator") instanceof PlayingCardIndicator found
                    ? found
                    : null;
            Node bookmark = findNodeByStyle(card, "channel-bookmark-icon");
            return row.getStyleClass().contains("playing-indicator-inline-row")
                    && title != null
                    && indicator != null
                    && row.getChildren().indexOf(findNodeByStyle(card, "channel-card-title-actions")) > row.getChildren().indexOf(title)
                    && findNodeByStyle(card, "channel-card-title-actions") instanceof HBox trailingActions
                    && trailingActions.getChildren().indexOf(indicator) < trailingActions.getChildren().indexOf(bookmark)
                    && bookmark != null
                    && trailingActions.getChildren().indexOf(indicator) < trailingActions.getChildren().indexOf(bookmark);
        });

        assertTrue(correctlyPlaced);
    }

    @Test
    void plainTextChannelCardSpacesBookmarkFromHoverMenuAction() throws Exception {
        double spacing = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            Region card = createPlainTextChannelCard(ui, channelItem(true));
            Node action = findNodeByType(card, PlayMenuButton.class);
            assertNotNull(action);
            return action.getParent() instanceof HBox actions ? actions.getSpacing() : 0.0;
        });

        assertEquals(5, spacing, 0.01);
    }

    @Test
    void drawerChannelRowDoesNotRepeatCategoryNameAsMetadata() throws Exception {
        LabelSnapshot meta = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            Region row = createDrawerChannelRow(ui, channelItem());
            Label label = findLabelByStyle(row, "account-drawer-channel-meta");
            return label == null ? null : new LabelSnapshot(label.getText(), label.isVisible(), label.isManaged());
        });

        assertNotNull(meta);
        assertEquals("", meta.text());
        assertFalse(meta.visible());
        assertFalse(meta.managed());
    }

    @Test
    void drawerChannelRowShowsBookmarkMarkerWhenBookmarked() throws Exception {
        boolean markerPresent = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            Region row = createDrawerChannelRow(ui, channelItem(true));
            return findNodeByStyle(row, "channel-bookmark-icon") != null;
        });

        assertTrue(markerPresent);
    }

    @Test
    void thumbnailDrawerChannelRowOrdersActionsBeforeBroadcastAndBookmark() throws Exception {
        boolean correctlyPlaced = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            setBooleanField(ui, "thumbnailsEnabled", true);
            ui.setMediaDrawerMode(true);
            ChannelListUI.ChannelItem item = channelItem(true);
            ResponsiveCardGrid<ChannelListUI.ChannelItem> grid = channelGrid(ui);
            // Reproduce the real card decoration: programme details wrap the original header
            // before the grid inserts its playing indicator.
            Region decoratedCard = createDrawerChannelRow(ui, item);
            CardEpgSupport support = new CardEpgSupport(ui);
            Method attach = CardEpgSupport.class.getDeclaredMethod("attach", Region.class, java.util.function.Supplier.class);
            attach.setAccessible(true);
            attach.invoke(support, decoratedCard, (java.util.function.Supplier<com.uiptv.application.LiveGuideApplicationService.Row>) () -> null);
            grid = new ResponsiveCardGrid<>(ignored -> decoratedCard, true);
            grid.setItems(FXCollections.observableArrayList(item));
            Scene scene = new Scene(grid, 420, 180);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Node card = findNodeByStyle(grid, "account-drawer-channel-row");
            Label title = findLabelByStyle(card, "account-drawer-channel-title");
            PlayingCardIndicator indicator = findNodeByStyle(card, "playing-card-indicator") instanceof PlayingCardIndicator found
                    ? found
                    : null;
            Node bookmark = findNodeByStyle(card, "channel-bookmark-icon");
            PlayMenuButton action = (PlayMenuButton) findNodeByType(card, PlayMenuButton.class);
            action.reveal();
            indicator.setManaged(true);
            indicator.setVisible(true);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return title != null
                    && indicator != null
                    && bookmark != null
                    && bookmark.getParent() instanceof HBox badges
                    && badges.getChildren().indexOf(bookmark) >= 0
                    && indicator.getParent() == badges
                    && badges.getChildren().indexOf(action) < badges.getChildren().indexOf(indicator)
                    && badges.getChildren().indexOf(indicator) < badges.getChildren().indexOf(bookmark)
                    && horizontallyOrdered(action, indicator, bookmark)
                    && verticallyCentered(action, indicator, bookmark)
                    && Math.abs(bookmark.localToScene(bookmark.getBoundsInLocal()).getMaxX()
                            - decoratedCard.localToScene(decoratedCard.getBoundsInLocal()).getMaxX()
                            + decoratedCard.getInsets().getRight()) < 1;
        });

        assertTrue(correctlyPlaced);
    }

    @Test
    void thumbnailDrawerChannelRowIncludesHoverMenuAction() throws Exception {
        boolean revealedOnHover = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            setBooleanField(ui, "thumbnailsEnabled", true);
            ui.setMediaDrawerMode(true);
            ChannelListUI.ChannelItem item = channelItem();
            ResponsiveCardGrid<ChannelListUI.ChannelItem> grid = channelGrid(ui);
            grid.setItems(FXCollections.observableArrayList(item));
            Scene scene = new Scene(grid, 420, 180);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Node card = findNodeByStyle(grid, "account-drawer-channel-row");
            Node actionNode = findNodeByType(card, PlayMenuButton.class);
            if (!(actionNode instanceof PlayMenuButton action)
                    || !action.getParent().getStyleClass().contains("channel-card-title-actions")) {
                return false;
            }
            assertFalse(action.isMaterialised());
            dispatchMouseEvent(card, MouseEvent.MOUSE_ENTERED);
            boolean revealed = action.isRevealed();
            dispatchMouseEvent(card, MouseEvent.MOUSE_EXITED);
            assertFalse(action.isRevealed());
            return revealed;
        });

        assertTrue(revealedOnHover);
    }

    @Test
    void drawerChannelRowCentersTitleBesideThumbnail() throws Exception {
        boolean centered = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            setBooleanField(ui, "thumbnailsEnabled", true);
            Region row = createDrawerChannelRow(ui, channelItem());
            Label title = findLabelByStyle(row, "account-drawer-channel-title");
            return title != null
                    && title.getParent() instanceof HBox titleRow
                    && titleRow.getStyleClass().contains("playing-indicator-inline-row")
                    && titleRow.getParent() instanceof VBox text
                    && text.getAlignment() == javafx.geometry.Pos.CENTER_LEFT;
        });

        assertTrue(centered);
    }

    @Test
    void drawerChannelCardRestoresThumbnailAfterPlainTextToggle() throws Exception {
        ThumbnailToggleSnapshot snapshot = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            ui.setMediaDrawerMode(true);
            invokeApplyThumbnailMode(ui, false);
            boolean plainHasThumbnail = findNodeByStyle(createChannelCard(ui, channelItem()), "channel-logo-view") != null;
            invokeApplyThumbnailMode(ui, true);
            boolean restoredHasThumbnail = findNodeByStyle(createChannelCard(ui, channelItem()), "channel-logo-view") != null;
            return new ThumbnailToggleSnapshot(plainHasThumbnail, restoredHasThumbnail);
        });

        assertFalse(snapshot.plainHasThumbnail());
        assertTrue(snapshot.restoredHasThumbnail());
    }

    @Test
    void channelScrollPaneRoutesArrowKeysToGridSelection() throws Exception {
        List<String> selectedNames = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Sports", "sports", Account.AccountAction.itv);
            ResponsiveCardGrid<ChannelListUI.ChannelItem> grid = channelGrid(ui);
            ScrollPane scrollPane = channelGridScroll(ui);
            grid.setItems(FXCollections.observableArrayList(channelItem("one"), channelItem("two"), channelItem("three")));

            Event.fireEvent(scrollPane, keyPressed(KeyCode.DOWN));
            return grid.getSelectedItems().stream()
                    .map(ChannelListUI.ChannelItem::getChannelName)
                    .toList();
        });

        assertEquals(List.of("two"), selectedNames);
    }

    @Test
    void vodChannelCardShowsWatchingNowChipWhenSaved() throws Exception {
        LabelSnapshot chip = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Movies", "movies", Account.AccountAction.vod);
            setBooleanField(ui, "thumbnailsEnabled", true);
            Region card = createChannelCard(ui, channelItem(true));
            Label label = findLabelByStyle(card, "channel-bookmark-chip");
            return label == null ? null : new LabelSnapshot(label.getText(), label.isVisible(), label.isManaged());
        });

        assertNotNull(chip);
        assertEquals(I18n.tr("autoWatchingNow"), chip.text());
        assertTrue(chip.visible());
        assertTrue(chip.managed());
    }

    @Test
    void seriesChannelCardDoesNotShowBookmarkChipForLegacyBookmarkState() throws Exception {
        boolean markerPresent = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Series", "series", Account.AccountAction.series);
            setBooleanField(ui, "thumbnailsEnabled", true);
            Region card = createChannelCard(ui, channelItem(true));
            return findNodeByStyle(card, "channel-bookmark-chip") != null;
        });

        assertFalse(markerPresent);
    }

    @Test
    void seriesChannelListRegistersWatchStateListenerForImmediateProgressUpdates() throws Exception {
        ListenerSnapshot snapshot = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Series", "series", Account.AccountAction.series);
            invokeNoArg(ui, "registerBookmarkListener");
            boolean registered = booleanField(ui, "seriesWatchStateListenerRegistered");
            invokeNoArg(ui, "unregisterBookmarkListener");
            boolean unregistered = !booleanField(ui, "seriesWatchStateListenerRegistered");
            return new ListenerSnapshot(registered, unregistered);
        });

        assertTrue(snapshot.registered());
        assertTrue(snapshot.unregistered());
    }

    @Test
    void mediaChannelListsDoNotRegisterBookmarkChangeListener() throws Exception {
        List<Boolean> bookmarkRegistrations = runOnFxThread(() -> {
            ChannelListUI vodUi = new ChannelListUI(new Account(), "Movies", "movies", Account.AccountAction.vod);
            ChannelListUI seriesUi = new ChannelListUI(new Account(), "Series", "series", Account.AccountAction.series);
            try {
                invokeNoArg(vodUi, "registerBookmarkListener");
                invokeNoArg(seriesUi, "registerBookmarkListener");
                return List.of(
                        booleanField(vodUi, "bookmarkListenerRegistered"),
                        booleanField(seriesUi, "bookmarkListenerRegistered")
                );
            } finally {
                invokeNoArg(vodUi, "unregisterBookmarkListener");
                invokeNoArg(seriesUi, "unregisterBookmarkListener");
            }
        });

        assertEquals(List.of(false, false), bookmarkRegistrations);
    }

    private static ContextMenu createChannelContextMenu(ChannelListUI ui, ChannelListUI.ChannelItem item) throws Exception {
        Method method = ChannelListUI.class.getDeclaredMethod(
                "createChannelContextMenu",
                ChannelListUI.ChannelItem.class,
                List.class,
                javafx.scene.Node.class
        );
        method.setAccessible(true);
        return (ContextMenu) method.invoke(ui, item, List.of(item), new Label("owner"));
    }

    private static Region createChannelCard(ChannelListUI ui, ChannelListUI.ChannelItem item) throws Exception {
        Method method = ChannelListUI.class.getDeclaredMethod("createChannelCard", ChannelListUI.ChannelItem.class);
        method.setAccessible(true);
        return (Region) method.invoke(ui, item);
    }

    private static Region createPlainTextChannelCard(ChannelListUI ui, ChannelListUI.ChannelItem item) throws Exception {
        Method method = ChannelListUI.class.getDeclaredMethod("createPlainTextChannelCard", ChannelListUI.ChannelItem.class);
        method.setAccessible(true);
        return (Region) method.invoke(ui, item);
    }

    private static Region createDrawerChannelRow(ChannelListUI ui, ChannelListUI.ChannelItem item) throws Exception {
        Method method = ChannelListUI.class.getDeclaredMethod("createDrawerChannelRow", ChannelListUI.ChannelItem.class);
        method.setAccessible(true);
        return (Region) method.invoke(ui, item);
    }

    private static void invokeApplyThumbnailMode(ChannelListUI ui, boolean enabled) throws Exception {
        Method method = ChannelListUI.class.getDeclaredMethod("applyThumbnailMode", boolean.class);
        method.setAccessible(true);
        method.invoke(ui, enabled);
    }

    @SuppressWarnings("unchecked")
    private static ResponsiveCardGrid<ChannelListUI.ChannelItem> channelGrid(ChannelListUI ui) throws Exception {
        Field field = ChannelListUI.class.getDeclaredField("channelGrid");
        field.setAccessible(true);
        return (ResponsiveCardGrid<ChannelListUI.ChannelItem>) field.get(ui);
    }

    private static ScrollPane channelGridScroll(ChannelListUI ui) throws Exception {
        Field field = ChannelListUI.class.getDeclaredField("channelGridScroll");
        field.setAccessible(true);
        return (ScrollPane) field.get(ui);
    }

    private static void invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static boolean booleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static ChannelListUI.ChannelItem channelItem() {
        return channelItem(false);
    }

    private static ChannelListUI.ChannelItem channelItem(boolean bookmarked) {
        return channelItem("BT Sport", bookmarked);
    }

    private static ChannelListUI.ChannelItem channelItem(String name) {
        return channelItem(name, false);
    }

    private static ChannelListUI.ChannelItem channelItem(String name, boolean bookmarked) {
        return channelItem(name, bookmarked, "http://example.test/stream.m3u8");
    }

    private static ChannelListUI.ChannelItem seriesItemWithoutCommand() {
        return channelItem("Friends", false, "");
    }

    private static ChannelListUI.ChannelItem channelItem(String name, boolean bookmarked, String cmd) {
        Channel channel = new Channel();
        channel.setChannelId("ch-1");
        channel.setName(name);
        channel.setCmd(cmd);
        return new ChannelListUI.ChannelItem(
                new SimpleStringProperty(channel.getName()),
                new SimpleStringProperty(channel.getChannelId()),
                new SimpleStringProperty(channel.getCmd()),
                bookmarked,
                new SimpleStringProperty(""),
                channel
        );
    }

    private static KeyEvent keyPressed(KeyCode keyCode) {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                keyCode,
                false,
                false,
                false,
                false
        );
    }

    private static Label findLabelByStyle(Node node, String styleClass) {
        if (node instanceof Label label && label.getStyleClass().contains(styleClass)) {
            return label;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label match = findLabelByStyle(child, styleClass);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Node findNodeByStyle(Node node, String styleClass) {
        if (node != null && node.getStyleClass().contains(styleClass)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = findNodeByStyle(child, styleClass);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Node findNodeByType(Node node, Class<? extends Node> type) {
        if (node != null && type.isInstance(node)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = findNodeByType(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static boolean verticallyCentered(Node... nodes) {
        if (nodes.length == 0 || nodes[0] == null || nodes[0].getParent() == null) {
            return false;
        }
        double top = nodes[0].getBoundsInParent().getCenterY();
        for (int i = 1; i < nodes.length; i++) {
            if (nodes[i] == null || nodes[i].getParent() != nodes[0].getParent()
                    || Math.abs(nodes[i].getBoundsInParent().getCenterY() - top) > 0.5) {
                return false;
            }
        }
        return true;
    }

    private static boolean horizontallyOrdered(Node... nodes) {
        if (nodes.length == 0 || nodes[0] == null || nodes[0].getParent() == null) {
            return false;
        }
        double rightEdge = nodes[0].getBoundsInParent().getMaxX();
        for (int i = 1; i < nodes.length; i++) {
            if (nodes[i] == null || nodes[i].getParent() != nodes[0].getParent()
                    || nodes[i].getBoundsInParent().getMinX() < rightEdge - 0.5) {
                return false;
            }
            rightEdge = nodes[i].getBoundsInParent().getMaxX();
        }
        return true;
    }

    private static void dispatchMouseEvent(Node target, EventType<MouseEvent> type) {
        Event.fireEvent(target, new MouseEvent(
                type,
                0,
                0,
                0,
                0,
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        ));
    }

    private static String menuItemText(MenuItem item) {
        if (item instanceof SeparatorMenuItem) {
            return "<separator>";
        }
        return item.getText();
    }

    private record BadgeSnapshot(String text, boolean visible, boolean managed) {
    }

    private record LabelSnapshot(String text, boolean visible, boolean managed) {
    }

    private record ListenerSnapshot(boolean registered, boolean unregistered) {
    }

    private record ThumbnailToggleSnapshot(boolean plainHasThumbnail, boolean restoredHasThumbnail) {
    }
}
