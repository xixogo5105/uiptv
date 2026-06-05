package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static ChannelListUI.ChannelItem channelItem() {
        Channel channel = new Channel();
        channel.setChannelId("ch-1");
        channel.setName("BT Sport");
        channel.setCmd("http://example.test/stream.m3u8");
        return new ChannelListUI.ChannelItem(
                new SimpleStringProperty(channel.getName()),
                new SimpleStringProperty(channel.getChannelId()),
                new SimpleStringProperty(channel.getCmd()),
                false,
                new SimpleStringProperty(""),
                channel
        );
    }

    private static String menuItemText(MenuItem item) {
        if (item instanceof SeparatorMenuItem) {
            return "<separator>";
        }
        return item.getText();
    }
}
