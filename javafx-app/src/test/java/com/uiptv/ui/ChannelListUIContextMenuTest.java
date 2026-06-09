package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.Region;
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
    void mediaChannelCardShowsBookmarkChipWhenBookmarked() throws Exception {
        boolean markerPresent = runOnFxThread(() -> {
            ChannelListUI ui = new ChannelListUI(new Account(), "Movies", "movies", Account.AccountAction.vod);
            setBooleanField(ui, "thumbnailsEnabled", true);
            Region card = createChannelCard(ui, channelItem(true));
            return findNodeByStyle(card, "channel-bookmark-chip") != null;
        });

        assertTrue(markerPresent);
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
        Channel channel = new Channel();
        channel.setChannelId("ch-1");
        channel.setName("BT Sport");
        channel.setCmd("http://example.test/stream.m3u8");
        return new ChannelListUI.ChannelItem(
                new SimpleStringProperty(channel.getName()),
                new SimpleStringProperty(channel.getChannelId()),
                new SimpleStringProperty(channel.getCmd()),
                bookmarked,
                new SimpleStringProperty(""),
                channel
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
}
