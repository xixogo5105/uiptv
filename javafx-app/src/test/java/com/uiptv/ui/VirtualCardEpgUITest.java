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
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.imageio.ImageIO;
import static com.uiptv.testsupport.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VirtualCardEpgUITest extends DbBackedUiTest {
    @BeforeAll static void initialize() throws Exception { initJavaFx(); }

    @AfterEach void resetEpgMode() throws Exception {
        new EpgSettingsApplicationService().setEnabled(false);
        for (int i = 0; i < 200 && runOnFxThread(EpgModeUI::isFeatureEnabled); i++) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        assertFalse(runOnFxThread(EpgModeUI::isFeatureEnabled));
    }

    @Test void programmeProgressUsesTheSuppliedClock() throws Exception {
        runOnFxThread(() -> {
            long start = 1_000_000L;
            var current = new EpgProgramme("Fixed-clock programme", "", start, start + 4_000L);
            VBox panel = new VBox(5);
            CardEpgSupport.display(panel, current, null, start + 1_000L);
            ProgressBar progress = (ProgressBar) panel.lookup(".progress-bar");
            assertNotNull(progress);
            assertEquals(.25, progress.getProgress(), .001);
            HBox compact = new HBox(12);
            CardEpgSupport.display(compact, current, null, start + 1_000, true);
            assertEquals(1, compact.getChildren().size(), "Narrow programme rows keep the channel and current programme columns aligned.");
            CardEpgSupport.display(compact, current, null, start + 1_000, false);
            assertEquals(2, compact.getChildren().size(), "Wide programme rows restore the Next column.");
            return null;
        });
    }

    @Test void cardsAndMenusUseExistingGridInBothModesAndKeepChannelsWithoutGuide() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                for (boolean enabled : new boolean[]{false, true}) {
                    mode.when(EpgModeUI::isFeatureEnabled).thenReturn(enabled);
                    Channel channel = new Channel(); channel.setName("Cinema"); channel.setChannelId("provider-id");
                    var item = new ChannelListUI.ChannelItem(new SimpleStringProperty("Cinema"), new SimpleStringProperty("provider-id"),
                            new SimpleStringProperty("http://example.test/live"), true, new SimpleStringProperty(""), channel);
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
                            assertFalse(card.getStyleClass().contains("epg-programme-aware-row"));
                            assertFalse(saved.getStyleClass().contains("epg-programme-aware-row"));
                            if (enabled) {
                                Node bookmarkGlyph = card.lookup(".channel-bookmark-glyph");
                                assertNotNull(bookmarkGlyph, "Bookmarked EPG channels keep their bookmark indicator.");
                                assertNotNull(card.lookup(".epg-card-header"), "EPG details stay inside the full virtual card.");
                                assertInstanceOf(VBox.class, card.lookup(".virtual-card-epg"));
                                assertInstanceOf(VBox.class, saved.lookup(".virtual-card-epg"));
                                if (thumbnails) {
                                    assertNotNull(saved.lookup(".epg-card-header"), "Bookmark EPG details stay inside the full virtual card.");
                                } else {
                                    assertSame(saved, saved.lookup(".virtual-card-epg").getParent());
                                }
                            }
                            ContextMenu menu = (ContextMenu) invoke(channels, "createChannelContextMenu",
                                    new Class<?>[]{ChannelListUI.ChannelItem.class, List.class, Node.class}, item, List.of(item), card);
                            ContextMenu savedMenu = (ContextMenu) invoke(bookmarks, "createBookmarkContextMenu",
                                    new Class<?>[]{BookmarkChannelListUI.BookmarkItem.class, List.class, Node.class}, bookmark, List.of(bookmark), saved);
                            assertEquals(enabled, menu.getItems().stream().anyMatch(entry -> I18n.tr("epgOpenGuide").equals(entry.getText())));
                            assertEquals(enabled, savedMenu.getItems().stream().anyMatch(entry -> I18n.tr("epgOpenGuide").equals(entry.getText())));
                            if (enabled) {
                                Pane panel = (Pane) card.lookup(".virtual-card-epg");
                                CardEpgSupport.display(panel, null, null);
                                assertTrue(panel.isManaged()); assertTrue(panel.isVisible()); assertTrue(card.isManaged());
                                if (panel instanceof HBox columns) {
                                    assertEquals(1, columns.getChildren().size(),
                                            "An empty guide uses one message in the first programme column.");
                                    Label placeholder = (Label) columns.getChildren().getFirst();
                                    assertEquals(I18n.tr("epgNoGuide"), placeholder.getText());
                                    assertEquals(javafx.geometry.Pos.CENTER_LEFT, columns.getAlignment());
                                    assertTrue(placeholder.getStyleClass().contains("virtual-card-epg-title"));
                                    assertEquals(javafx.geometry.Pos.CENTER_LEFT, placeholder.getAlignment());
                                    assertFalse(panel.lookup(".progress-bar") instanceof ProgressBar);
                                } else {
                                    assertEquals(3, panel.getChildren().size());
                                    VBox nowColumn = (VBox) panel.getChildren().getFirst();
                                    VBox nextColumn = (VBox) panel.getChildren().getLast();
                                    assertEquals(I18n.tr("epgNow") + ": " + I18n.tr("epgNoProgrammeDetails"),
                                            ((Label) nowColumn.getChildren().getFirst()).getText());
                                    assertEquals(I18n.tr("epgNext") + ": " + I18n.tr("epgNoProgrammeDetails"),
                                            ((Label) nextColumn.getChildren().getFirst()).getText());
                                    assertNotNull(nowColumn.lookup(".epg-programme-time"), "The placeholder keeps its time range.");
                                    assertNotNull(nextColumn.lookup(".epg-programme-time"), "The next placeholder keeps its time range.");
                                    assertInstanceOf(ProgressBar.class, panel.lookup(".progress-bar"));
                                }
                                long now = System.currentTimeMillis();
                                CardEpgSupport.display(panel, new EpgProgramme("Current film", "", now - 60_000, now + 60_000),
                                        new EpgProgramme("Next film", "", now + 60_000, now + 3_600_000));
                                StackPane preview = new StackPane(card); Scene scene = new Scene(preview, 320, 190);
                                scene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                                preview.resize(320, 190); preview.applyCss(); preview.layout();
                                if (card.getStyleClass().contains("epg-programme-aware-row")) {
                                    assertEquals(Color.WHITE, ((SVGPath) card.lookup(".channel-bookmark-glyph")).getFill(),
                                            "Bookmark glyphs are white on dark cards.");
                                }
                                if (card.getStyleClass().contains("epg-programme-aware-row")) {
                                    assertNotNull(card.lookup(".epg-programme-columns"));
                                    assertEquals(2, panel.getChildren().size());
                                    VBox nowColumn = (VBox) panel.getChildren().getFirst();
                                    assertEquals("Current film",
                                            ((Label) ((VBox) nowColumn.getChildren().getFirst()).getChildren().getFirst()).getText());
                                    assertEquals(.5, ((ProgressBar) nowColumn.getChildren().get(1)).getProgress(), .15);
                                } else {
                                    Node header = card.lookup(".epg-card-header"); assertNotNull(header);
                                    assertEquals(header.localToScene(header.getBoundsInLocal()).getMinX(), panel.localToScene(panel.getBoundsInLocal()).getMinX(), .1);
                                    assertEquals(header.getBoundsInLocal().getWidth(), panel.getWidth(), .1);
                                    if (thumbnails) {
                                        Node thumbnail = card.lookup(".channel-logo-view");
                                        assertNotNull(thumbnail);
                                        assertEquals(header.localToScene(header.getBoundsInLocal()).getMinY(), thumbnail.localToScene(thumbnail.getBoundsInLocal()).getMinY(), .1);
                                    }
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

    @Test void nowNextAndGuideContentUseProgrammeCardsInBothThemesWithoutOpeningWindows() throws Exception {
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
                Label title = new Label("Example Cinema"); title.getStyleClass().add("bookmark-channel-title");
                Button anchor = new Button("⋯"); HBox header = new HBox(12, title, anchor);
                VBox card = new VBox(12, header, panel); card.getStyleClass().add("bookmark-card");
                VBox root = new VBox(card); root.setStyle("-fx-padding: 16; -fx-background-color: -uiptv-app-bg");
                Scene scene = new Scene(root, 480, 250);
                scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
                if (dark) scene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                root.applyCss(); root.layout();
                assertEquals(2, panel.lookupAll(".virtual-card-epg-title").size());
                ProgressBar progress = (ProgressBar) panel.getChildren().get(1);
                assertEquals(4, progress.minHeight(-1), .1);
                Region fill = (Region) progress.lookup(".bar");
                assertEquals(javafx.scene.paint.Color.web("#32b8dd"), fill.getBackground().getFills().getFirst().getFill());
                assertTrue(fill.getHeight() >= 4);
                assertTrue(fill.getWidth() > 0);
                save(root, "target/epg-virtual-card-" + (dark ? "dark" : "light") + ".png");
                card.getStyleClass().add("selected");
                root.applyCss(); root.layout();
                for (Node label : panel.lookupAll(".virtual-card-epg-title")) {
                    assertEquals(javafx.scene.paint.Color.web(dark ? "#f7f7f7" : "#ffffff"), ((Label) label).getTextFill());
                }
                save(root, "target/epg-selected-card-" + (dark ? "dark" : "light") + ".png");
                CardEpgSupport.display(panel, null, null,
                        java.time.ZonedDateTime.of(2026, 10, 15, 8, 45, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                root.applyCss(); root.layout();
                save(root, "target/epg-selected-placeholder-" + (dark ? "dark" : "light") + ".png");

                VBox body = ChannelGuidePopup.content(row, 480, () -> {});
                Scene guideScene = new Scene(body, 480, 370);
                guideScene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
                if (dark) guideScene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                body.applyCss(); body.layout();
                ListView<?> programmes = (ListView<?>) body.lookup(".list-view");
                assertEquals(3, programmes.getItems().size()); assertEquals(javafx.geometry.Orientation.HORIZONTAL, programmes.getOrientation());
                assertFalse(body.lookupAll(".epg-programme-badge").isEmpty()); assertFalse(body.lookupAll(".epg-current-card").isEmpty());
                save(body, "target/epg-guide-popup-" + (dark ? "dark" : "light") + ".png");
                ChannelGuidePopup.release(body);
                assertTrue(programmes.getItems().isEmpty(), "Closing guide content must release its schedule rows");

                VBox wide = ChannelGuidePopup.content(row, 1120, () -> {});
                Scene wideScene = new Scene(wide, 1120, 370);
                wideScene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
                if (dark) wideScene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                wide.applyCss(); wide.layout();
                save(wide, "target/epg-guide-popup-wide-" + (dark ? "dark" : "light") + ".png");
                assertTrue(wide.lookupAll(".epg-programme-badge").size() >= 3);
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

    @Test void visibleCardsHydrateEpgInRenderedOrder() throws Exception {
        var resolvedOrder = new ArrayList<String>();
        var firstBatch = new CountDownLatch(1);
        final CardEpgSupport[] state = new CardEpgSupport[1];
        new EpgSettingsApplicationService().setEnabled(true);
        for (int i = 0; i < 200 && !runOnFxThread(EpgModeUI::isFeatureEnabled); i++) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        assertTrue(runOnFxThread(EpgModeUI::isFeatureEnabled));
            try (var construction = mockConstruction(LiveGuideApplicationService.class, (mock, context) -> {
                 when(mock.cachedSummaries(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
                 when(mock.cachedIcons(anyList())).thenReturn(new IdentityHashMap<>());
             })) {
            state[0] = runOnFxThread(() -> {
                VBox cards = new VBox();
                StackPane owner = new StackPane(cards);
                CardEpgSupport support = new CardEpgSupport(owner);
                for (int i = 0; i < 3; i++) {
                    String name = "Channel " + i;
                    Channel channel = new Channel(); channel.setName(name); channel.setChannelId("id-" + i);
                    VBox card = new VBox(new Label(name));
                    card.setMinHeight(100); card.setPrefHeight(100); card.setMaxHeight(100);
                    support.bind(card, () -> {
                        synchronized (resolvedOrder) {
                            resolvedOrder.add(name);
                            if (resolvedOrder.size() == 3) firstBatch.countDown();
                        }
                        return new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of());
                    });
                    cards.getChildren().add(card);
                }
                Scene scene = new Scene(owner, 400, 360);
                owner.resize(400, 360); scene.getRoot().applyCss(); scene.getRoot().layout();
                support.refreshMode();
                ((javafx.animation.PauseTransition) field(support, "pulse").get(support)).stop();
                ((javafx.animation.PauseTransition) field(support, "layoutRetry").get(support)).stop();
                invoke(support, "refresh", new Class<?>[]{});
                return support;
            });
            assertTrue(firstBatch.await(5, TimeUnit.SECONDS));
            synchronized (resolvedOrder) {
                assertEquals(List.of("Channel 0", "Channel 1", "Channel 2"), resolvedOrder);
            }
            construction.constructed();
        } finally {
            if (state[0] != null) runOnFxThread(() -> {
                state[0].stop();
                return null;
            });
        }
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
                assertTrue(button.isVisible(), "Guide control must be available while its summary loads");
                com.uiptv.player.MediaPlayerFactory.publishGuideSummary(new PlayerEpgApplicationService.Result("Cinema", programme, null));
                assertTrue(button.isVisible());
                Channel channel = new Channel(); channel.setName("Cinema");
                int windows = javafx.stage.Window.getWindows().size();
                overlay.show(new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of(programme)));
                video.applyCss(); video.layout();
                assertSame(video, overlay.getParent()); assertTrue(overlay.isVisible());
                assertEquals(windows, javafx.stage.Window.getWindows().size());
                save(video, "target/epg-inline-player-guide.png");
                var past = new EpgProgramme("Earlier film", "Previous programme.", now - 7_200_000, now - 60_000);
                for (boolean dark : new boolean[]{false, true}) {
                    video.getScene().getStylesheets().setAll(getClass().getResource("/application.css").toExternalForm());
                    if (dark) video.getScene().getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                    overlay.show(new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of(past, programme)));
                    video.applyCss(); video.layout();
                    assertEquals(4, overlay.lookupAll(".epg-guide-action").size());
                    for (Node node : overlay.lookupAll(".epg-guide-action")) {
                        Button action = (Button) node;
                        assertEquals(javafx.scene.paint.Color.web("#303030"), action.getBackground().getFills().getFirst().getFill());
                        if (action.getStyleClass().contains("epg-guide-now-button")) assertTrue(action.getWidth() >= 56);
                        else { assertEquals(30, action.getWidth(), .1); assertNotNull(action.getGraphic()); }
                        action.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                        video.applyCss();
                        assertEquals(javafx.scene.paint.Color.web("#444444"), action.getBackground().getFills().getFirst().getFill());
                        action.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                    }
                    for (Node node : overlay.lookupAll(".epg-programme-badge")) {
                        if (node.getStyleClass().contains("epg-now-badge")) continue;
                        Label badge = (Label) node;
                        assertEquals(javafx.scene.paint.Color.web("#3a3a3a"), badge.getBackground().getFills().getFirst().getFill());
                    }
                    save(video, "target/epg-player-controls-" + (dark ? "dark" : "light") + ".png");
                }
                ((Button) overlay.lookup(".uiptv-circular-close-button")).fire();
                assertFalse(overlay.isVisible()); assertTrue(overlay.getChildren().isEmpty());
                video.resize(1100, 260); video.layout();
                overlay.show(new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of(programme)));
                video.applyCss(); video.layout();
                assertTrue(overlay.getBoundsInParent().getMinY() >= 0);
                assertTrue(overlay.getBoundsInParent().getMaxY() <= video.getHeight());
                overlay.close();
                video.resize(480, 400); video.layout(); assertTrue(button.isManaged());
                wideMode.set(false); assertFalse(button.isManaged(), "Guide control must be hidden in narrow mode");
                fullscreen.set(true); assertTrue(button.isManaged(), "Guide control must return in fullscreen");
                fullscreen.set(false); assertFalse(button.isManaged());
                wideMode.set(true); assertTrue(button.isManaged());
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(false);
                com.uiptv.player.MediaPlayerFactory.publishGuideSummary(null); assertFalse(button.isManaged());
                overlay.close(); video.getChildren().clear();
            } finally { com.uiptv.player.MediaPlayerFactory.updateGuideContext(null, null); }
            return null;
        });
    }

    @Test void guideNowButtonReturnsToCurrentCardAndResetsArrowPosition() throws Exception {
        runOnFxThread(() -> {
            long now = System.currentTimeMillis();
            var programmes = new java.util.ArrayList<EpgProgramme>();
            for (int i = 0; i < 10; i++) programmes.add(new EpgProgramme("Film " + i, "",
                    now + (i - 4) * 3_600_000L - 60_000, now + (i - 3) * 3_600_000L - 60_000));
            Channel channel = new Channel(); channel.setName("Cinema");
            VBox body = ChannelGuidePopup.content(new LiveGuideApplicationService.Row("a", "A", "", channel, programmes), 960, () -> {});
            new Scene(body, 960, 370); body.resize(960, 370); body.applyCss(); body.layout();
            ListView<?> list = (ListView<?>) body.getChildren().get(1);
            var target = new java.util.concurrent.atomic.AtomicInteger(-1);
            list.setOnScrollTo(event -> target.set(event.getScrollTarget()));
            HBox navigation = (HBox) body.getChildren().getLast();
            Button previous = (Button) navigation.getChildren().getFirst();
            Button nowButton = (Button) navigation.getChildren().get(1);
            Button next = (Button) navigation.getChildren().getLast();
            assertEquals(I18n.tr("epgNow"), nowButton.getText());
            previous.fire(); assertEquals(3, target.get());
            previous.fire(); assertEquals(2, target.get());
            nowButton.fire(); assertEquals(4, target.get());
            next.fire(); assertEquals(5, target.get());
            assertEquals(5, ChannelGuidePopup.nowIndex(programmes, programmes.get(4).stop()));
            assertEquals(0, ChannelGuidePopup.nowIndex(programmes, now - 24 * 3_600_000L));
            assertEquals(9, ChannelGuidePopup.nowIndex(programmes, now + 24 * 3_600_000L));
            assertEquals(-1, ChannelGuidePopup.nowIndex(List.of(), now));
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
