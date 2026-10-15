package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.model.*;
import com.uiptv.testsupport.*;
import com.uiptv.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static com.uiptv.testsupport.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VirtualCardEpgUITest extends DbBackedUiTest {
    @BeforeAll static void initialize() throws Exception { initJavaFx(); }

    @AfterEach void resetEpgMode() throws Exception {
        new EpgSettingsApplicationService().setEnabled(false);
        for (int i = 0; i < 200 && runOnFxThread(EpgModeUI::isFeatureEnabled); i++) Thread.sleep(10);
        assertFalse(runOnFxThread(EpgModeUI::isFeatureEnabled));
    }

    @Test void cardsAndMenusUseExistingGridInBothModesAndKeepChannelsWithoutGuide() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                for (boolean enabled : new boolean[]{false, true}) {
                    mode.when(EpgModeUI::isFeatureEnabled).thenReturn(enabled);
                    Channel channel = new Channel(); channel.setName("Cinema"); channel.setChannelId("provider-id");
                    var item = new ChannelListUI.ChannelItem(new SimpleStringProperty("Cinema"), new SimpleStringProperty("provider-id"),
                            new SimpleStringProperty("http://example.test/live"), false, new SimpleStringProperty(""), channel);
                    Account account = new Account(); account.setDbId("local-account");
                    ChannelListUI channels = new ChannelListUI(account, "All", "all", Account.AccountAction.itv);
                    BookmarkChannelListUI bookmarks = new BookmarkChannelListUI(null, null);
                    var bookmark = new BookmarkChannelListUI.BookmarkItem(new SimpleStringProperty("bookmark"), new SimpleStringProperty("Cinema"),
                            new SimpleStringProperty("provider-id"), new SimpleStringProperty("cmd"), new SimpleStringProperty("Account"),
                            new SimpleStringProperty("All"), new SimpleStringProperty(""), new SimpleStringProperty("Cinema (Account)"),
                            new SimpleStringProperty("category"), new SimpleStringProperty(""), Account.AccountAction.itv, null, null, null, null, null);
                    try {
                        for (boolean thumbnails : new boolean[]{false, true}) {
                            field(channels, "thumbnailsEnabled").setBoolean(channels, thumbnails);
                            field(bookmarks, "thumbnailsEnabled").setBoolean(bookmarks, thumbnails);
                            Region card = (Region) invoke(channels, "createChannelCard", new Class<?>[]{ChannelListUI.ChannelItem.class}, item);
                            Region saved = (Region) invoke(bookmarks, "createBookmarkCard", new Class<?>[]{BookmarkChannelListUI.BookmarkItem.class}, bookmark);
                            assertNotNull(card); assertNotNull(saved);
                            assertEquals(enabled, card.lookup(".virtual-card-epg") != null);
                            assertEquals(enabled, saved.lookup(".virtual-card-epg") != null);
                            ContextMenu menu = (ContextMenu) invoke(channels, "createChannelContextMenu",
                                    new Class<?>[]{ChannelListUI.ChannelItem.class, List.class, Node.class}, item, List.of(item), card);
                            ContextMenu savedMenu = (ContextMenu) invoke(bookmarks, "createBookmarkContextMenu",
                                    new Class<?>[]{BookmarkChannelListUI.BookmarkItem.class, List.class, Node.class}, bookmark, List.of(bookmark), saved);
                            assertEquals(enabled, menu.getItems().stream().anyMatch(entry -> I18n.tr("epgOpenGuide").equals(entry.getText())));
                            assertEquals(enabled, savedMenu.getItems().stream().anyMatch(entry -> I18n.tr("epgOpenGuide").equals(entry.getText())));
                            if (enabled) {
                                VBox panel = (VBox) card.lookup(".virtual-card-epg");
                                CardEpgSupport.display(panel, null, null);
                                assertTrue(panel.isManaged()); assertTrue(panel.isVisible()); assertTrue(card.isManaged());
                                assertEquals(3, panel.getChildren().size());
                                var nowPlaceholder = (VBox) panel.getChildren().getFirst();
                                var nextPlaceholder = (VBox) panel.getChildren().getLast();
                                assertEquals(I18n.tr("epgNow") + ": " + I18n.tr("epgNoProgrammeDetails"), ((Label) nowPlaceholder.getChildren().getFirst()).getText());
                                assertEquals(I18n.tr("epgNext") + ": " + I18n.tr("epgNoProgrammeDetails"), ((Label) nextPlaceholder.getChildren().getFirst()).getText());
                                assertEquals("—", ((Label) nowPlaceholder.getChildren().getLast()).getText());
                                assertEquals("—", ((Label) nextPlaceholder.getChildren().getLast()).getText());
                                assertEquals(0, ((ProgressBar) panel.getChildren().get(1)).getProgress());
                                long now = System.currentTimeMillis();
                                CardEpgSupport.display(panel, new EpgProgramme("Current film", "", now - 60_000, now + 60_000),
                                        new EpgProgramme("Next film", "", now + 60_000, now + 3_600_000));
                                StackPane preview = new StackPane(card); Scene scene = new Scene(preview, 320, 190);
                                scene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                                preview.resize(320, 190); preview.applyCss(); preview.layout();
                                Node header = card.lookup(".epg-card-header"); assertNotNull(header);
                                assertEquals(header.localToScene(header.getBoundsInLocal()).getMinX(), panel.localToScene(panel.getBoundsInLocal()).getMinX(), .1);
                                assertEquals(header.getBoundsInLocal().getWidth(), panel.getWidth(), .1);
                                if (thumbnails) {
                                    Node thumbnail = card.lookup(".channel-logo-view");
                                    assertNotNull(thumbnail);
                                    assertEquals(header.localToScene(header.getBoundsInLocal()).getMinY(), thumbnail.localToScene(thumbnail.getBoundsInLocal()).getMinY(), .1);
                                }
                                save(preview, "target/epg-full-width-card-" + (thumbnails ? "thumbnail" : "plain") + ".png");
                                CardEpgSupport.display(panel, null, null);
                                preview.applyCss(); preview.layout();
                                save(preview, "target/epg-empty-card-" + (thumbnails ? "thumbnail" : "plain") + ".png");
                                preview.getChildren().clear();
                            }
                        }
                        invoke(channels, "applyListContentMode", new Class<?>[]{});
                        invoke(bookmarks, "applyEpgMode", new Class<?>[]{});
                        assertSame(field(channels, "channelGridScroll").get(channels), ((VBox) field(channels, "listPane").get(channels)).getChildren().getLast());
                        assertTrue(((ScrollPane) field(bookmarks, "bookmarkPageScroll").get(bookmarks)).isManaged());
                        assertTrue(channels.lookupAll(".live-guide").isEmpty()); assertTrue(bookmarks.lookupAll(".live-guide").isEmpty());
                        assertTrue(channels.lookupAll(".check-box").stream().noneMatch(node -> node instanceof CheckBox box && I18n.tr("epgShowAllChannels").equals(box.getText())));
                    } finally { channels.dispose(); ((CardEpgSupport) field(bookmarks, "cardEpg").get(bookmarks)).stop(); }
                }
            }
            return null;
        });
    }

    @Test void nowNextAndPopupUseProgrammeCardsInBothThemes() throws Exception {
        runOnFxThread(() -> {
            javafx.application.Platform.setImplicitExit(false);
            long now = System.currentTimeMillis();
            var current = new EpgProgramme("Evening cinema", "A feature film with programme details.", now - 1_800_000, now + 1_800_000);
            var next = new EpgProgramme("Late night film", "Coming up later this evening.", now + 1_800_000, now + 7_200_000);
            Channel channel = new Channel(); channel.setName("Example Cinema");
            var row = new LiveGuideApplicationService.Row("account", "Account", "", channel,
                    List.of(current, next, new EpgProgramme("Overnight cinema", "More films overnight.", now + 7_200_000, now + 12_600_000)));
            for (boolean dark : new boolean[]{false, true}) {
                VBox panel = new VBox(5); panel.getStyleClass().add("virtual-card-epg"); CardEpgSupport.display(panel, current, next);
                Label title = new Label("Example Cinema"); title.getStyleClass().add("epg-programme-title");
                Button anchor = new Button("⋯"); HBox header = new HBox(12, title, anchor);
                VBox card = new VBox(12, header, panel); card.getStyleClass().add("bookmark-card");
                VBox root = new VBox(card); root.setStyle("-fx-padding: 16; -fx-background-color: -uiptv-app-bg");
                Scene scene = new Scene(root, 480, 250);
                scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
                if (dark) scene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                javafx.stage.Stage stage = new javafx.stage.Stage(); stage.setScene(scene); stage.show();
                javafx.stage.Popup popup = null;
                try {
                    root.applyCss(); root.layout();
                    assertEquals(2, panel.lookupAll(".virtual-card-epg-title").size());
                    save(root, "target/epg-virtual-card-" + (dark ? "dark" : "light") + ".png");
                    popup = ChannelGuidePopup.show(anchor, row);
                    assertTrue(popup.isShowing()); assertSame(stage, popup.getOwnerWindow());
                    Parent body = (Parent) popup.getContent().getFirst(); body.applyCss(); body.layout();
                    ListView<?> programmes = (ListView<?>) body.lookup(".list-view");
                    assertEquals(3, programmes.getItems().size()); assertEquals(javafx.geometry.Orientation.HORIZONTAL, programmes.getOrientation());
                    assertFalse(body.lookupAll(".epg-programme-badge").isEmpty()); assertFalse(body.lookupAll(".epg-current-card").isEmpty());
                    save(body, "target/epg-guide-popup-" + (dark ? "dark" : "light") + ".png");
                    popup.hide(); assertTrue(popup.getContent().isEmpty());
                    stage.setWidth(1120); root.resize(1120, 250); root.layout();
                    popup = ChannelGuidePopup.show(anchor, row);
                    Parent wide = (Parent) popup.getContent().getFirst(); wide.applyCss(); wide.layout();
                    save(wide, "target/epg-guide-popup-wide-" + (dark ? "dark" : "light") + ".png");
                    assertTrue(wide.lookupAll(".epg-programme-badge").size() >= 3);
                    popup.hide();
                } finally { if (popup != null) popup.hide(); stage.close(); }
            }
            return null;
        });
    }

    @Test void onlyVisibleCardsLoadAndDetachingReleasesBindings() throws Exception {
        var configuration = com.uiptv.service.ConfigurationService.getInstance().read();
        configuration.setEpgEnabled(true); com.uiptv.service.ConfigurationService.getInstance().save(configuration);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var first = new java.util.concurrent.CountDownLatch(1);
        Object[] state = runOnFxThread(() -> {
            Field feature = EpgModeUI.class.getDeclaredField("featureEnabled"); feature.setAccessible(true);
            ((javafx.beans.property.BooleanProperty) feature.get(null)).set(true);
            VBox cards = new VBox(); ScrollPane scroll = new ScrollPane(cards); scroll.setFitToWidth(true);
            StackPane owner = new StackPane(scroll); CardEpgSupport support = new CardEpgSupport(owner);
            for (int i = 0; i < 20; i++) {
                VBox card = new VBox(new Label("Channel " + i)); card.setMinHeight(100); card.setPrefHeight(100); card.setMaxHeight(100);
                support.bind(card, () -> { assertFalse(javafx.application.Platform.isFxApplicationThread()); count.incrementAndGet(); first.countDown(); return null; });
                cards.getChildren().add(card);
            }
            new Scene(owner, 400, 240); owner.resize(400, 240); owner.applyCss(); owner.layout();
            support.queue(); return new Object[]{support, scroll};
        });
        try {
            assertTrue(first.await(5, java.util.concurrent.TimeUnit.SECONDS));
            runOnFxThread(() -> null);
            assertTrue(count.get() <= 4, "Off-screen cards must not resolve EPG");
            runOnFxThread(() -> {
                CardEpgSupport support = (CardEpgSupport) state[0];
                ((ScrollPane) state[1]).setContent(new Pane());
                assertTrue(((Map<?, ?>) field(support, "attached").get(support)).isEmpty());
                support.stop(); return null;
            });
        } finally { runOnFxThread(() -> { ((CardEpgSupport) state[0]).stop(); return null; }); }
    }

    @Test void playerGuideStaysInsideVideoAndClosesWithoutAnotherWindow() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                StackPane video = new StackPane(); video.setStyle("-fx-background-color: black");
                Button button = new Button(I18n.tr("epgOpenGuide"));
                var fullscreen = new javafx.beans.property.SimpleBooleanProperty(false);
                var wideMode = new javafx.beans.property.SimpleBooleanProperty(true);
                PlayerGuideOverlay overlay = new PlayerGuideOverlay(video, button, fullscreen, wideMode);
                video.getChildren().add(overlay); new Scene(video, 1100, 650);
                video.getScene().getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                video.resize(1100, 650); video.applyCss(); video.layout();
                long now = System.currentTimeMillis();
                var programme = new EpgProgramme("Current film", "Details within the video.", now - 60_000, now + 3_600_000);
                Account account = new Account(); account.setAction(Account.AccountAction.itv);
                Channel playing = new Channel(); playing.setChannelId("provider-stream"); playing.setName("Cinema");
                playing.setExtraJson("{\"epg_channel_id\":\"xmltv-station\"}");
                com.uiptv.player.MediaPlayerFactory.updateGuideContext(account, playing);
                assertEquals(playing.getExtraJson(), com.uiptv.player.MediaPlayerFactory.guideContextProperty().get().channel().getExtraJson());
                com.uiptv.player.MediaPlayerFactory.publishGuideSummary(new PlayerEpgApplicationService.Result("Cinema", programme, null));
                assertTrue(button.isVisible());
                Channel channel = new Channel(); channel.setName("Cinema");
                int windows = javafx.stage.Window.getWindows().size();
                overlay.show(new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of(programme)));
                video.applyCss(); video.layout();
                assertSame(video, overlay.getParent()); assertTrue(overlay.isVisible());
                assertEquals(windows, javafx.stage.Window.getWindows().size());
                save(video, "target/epg-inline-player-guide.png");
                ((Button) overlay.lookup(".uiptv-circular-close-button")).fire();
                assertFalse(overlay.isVisible()); assertTrue(overlay.getChildren().isEmpty());
                video.resize(1100, 260); video.layout();
                overlay.show(new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of(programme)));
                video.applyCss(); video.layout();
                assertTrue(overlay.getBoundsInParent().getMinY() >= 0);
                assertTrue(overlay.getBoundsInParent().getMaxY() <= video.getHeight());
                overlay.close();
                video.resize(480, 400); video.layout(); assertTrue(button.isManaged());
                wideMode.set(false); assertFalse(button.isManaged());
                fullscreen.set(true); assertTrue(button.isManaged());
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(false);
                com.uiptv.player.MediaPlayerFactory.publishGuideSummary(null); assertFalse(button.isManaged());
                overlay.close(); video.getChildren().clear();
            } finally { com.uiptv.player.MediaPlayerFactory.updateGuideContext(null, null); }
            return null;
        });
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }
    private static void save(Parent root, String path) throws Exception {
        root.applyCss(); root.layout(); WritableImage snapshot = root.snapshot(null, null);
        BufferedImage image = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        ImageIO.write(image, "png", Path.of(path).toFile());
    }
}
