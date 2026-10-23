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
            assertEquals(1, compact.getChildren().size(), "A missing next programme does not create a placeholder column.");
            var next = new EpgProgramme("Next programme", "", start + 4_000, start + 8_000);
            CardEpgSupport.display(compact, current, next, start + 1_000, false);
            assertEquals(2, compact.getChildren().size(), "A real next programme is shown in the second column.");
            return null;
        });
    }

    @Test void missingGuideUsesOneNoGuideMessageWithoutInventedTimelines() throws Exception {
        runOnFxThread(() -> {
            long now = System.currentTimeMillis();
            VBox card = new VBox(5);
            CardEpgSupport.display(card, null, null, now);
            assertEquals(3, card.getChildren().size(), "The no-guide message sits below its own separator at the card bottom.");
            Region flexibleSpace = (Region) card.getChildren().get(0);
            Region separator = (Region) card.getChildren().get(1);
            assertEquals(javafx.scene.layout.Priority.ALWAYS, VBox.getVgrow(flexibleSpace));
            assertTrue(separator.getStyleClass().contains("epg-no-guide-separator"));
            assertEquals(I18n.tr("epgNoGuide"), ((Label) card.getChildren().getLast()).getText());
            assertTrue(card.getStyleClass().contains("virtual-card-epg-no-guide"));
            assertTrue(card.lookupAll(".epg-programme-time").isEmpty());
            assertNull(card.lookup(".progress-bar"));
            assertTrue(card.lookupAll(".label").stream().noneMatch(node -> node instanceof Label label
                    && I18n.tr("epgSourceUnavailable").equals(label.getText())),
                    "Source refresh failures stay out of the card content.");

            HBox programmeRow = new HBox(12);
            CardEpgSupport.display(programmeRow, null, null, now);
            assertEquals(I18n.tr("epgNoGuide"), ((Label) programmeRow.getChildren().getFirst()).getText());
            assertNull(programmeRow.lookup(".progress-bar"));
            assertNull(programmeRow.lookup(".epg-programme-time"));

            for (EpgProgramme invalid : List.of(new EpgProgramme("Real title", "", 0, 0),
                    new EpgProgramme("Real title", "", 100, 1))) {
                CardEpgSupport.display(card, invalid, null, now);
                assertNull(card.lookup(".progress-bar"));
                assertNull(card.lookup(".epg-programme-time"), "Malformed provider times are not replaced with fabricated times.");
            }
            return null;
        });
    }

    @Test void escapedProgrammeTitlesRenderAsReadableText() throws Exception {
        runOnFxThread(() -> {
            long now = System.currentTimeMillis();
            VBox card = new VBox(5);
            CardEpgSupport.display(card,
                    new EpgProgramme("Women and &amp;#039;s One-Day International Cricket", "", now - 60_000, now + 60_000),
                    null, now);

            Label title = (Label) card.lookup(".virtual-card-epg-title");
            assertNotNull(title);
            assertEquals("Now: Women and 's One-Day International Cricket", title.getText());
            return null;
        });
    }

    @Test void providerMissingDetailsMarkersDoNotRenderAsFakeProgrammes() throws Exception {
        runOnFxThread(() -> {
            VBox panel = new VBox();
            CardEpgSupport.display(panel, new EpgProgramme("No programme details", "", 1000, 3000),
                    new EpgProgramme("No details available", "", 3000, 5000), 2000);
            assertEquals(3, panel.getChildren().size());
            assertEquals(I18n.tr("epgNoGuide"), ((Label) panel.getChildren().getLast()).getText());
            assertNull(panel.lookup(".progress-bar"));
            assertTrue(panel.lookupAll(".epg-programme-time").isEmpty());
            CardEpgSupport.display(panel, null, null, System.currentTimeMillis());
            assertEquals(3, panel.getChildren().size());
            assertNull(panel.lookup(".progress-bar"));
            assertTrue(panel.lookupAll(".epg-programme-time").isEmpty());
            return null;
        });
    }

    @Test void minuteTickKeepsNoGuideMessageStableWithoutAddingProgress() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                VBox card = new VBox(); VBox root = new VBox(card);
                new Scene(root, 320, 300); root.resize(320, 300);
                CardEpgSupport support = new CardEpgSupport(root);
                support.bind(card, () -> null);
                long now = System.currentTimeMillis();
                Pane panel = (Pane) card.lookup(".virtual-card-epg");
                CardEpgSupport.display(panel, null, null, now);
                root.applyCss(); root.layout();
                var nodes = List.copyOf(panel.getChildren());
                invoke(support, "updateProgress", new Class<?>[0]);
                assertEquals(nodes, panel.getChildren());
                assertNull(panel.lookup(".progress-bar"));
                assertEquals(I18n.tr("epgNoGuide"), ((Label) panel.getChildren().getLast()).getText());
                support.stop();
            }
            return null;
        });
    }

    @Test void minuteTickPreservesProgrammeNodesAndDoesNoGuideLookup() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                VBox card = new VBox(); VBox root = new VBox(card);
                new Scene(root, 320, 300); root.resize(320, 300);
                CardEpgSupport support = new CardEpgSupport(root);
                java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
                support.bind(card, () -> { lookups.incrementAndGet(); return null; });
                long now = System.currentTimeMillis();
                EpgProgramme current = new EpgProgramme("Real programme", "", now - 60_000, now + 60_000);
                Pane panel = (Pane) card.lookup(".virtual-card-epg");
                CardEpgSupport.display(panel, current, null, now - 30_000);
                var bindings = (Map<?, ?>) field(support, "attached").get(support);
                Object binding = bindings.get(card);
                field(binding, "current").set(binding, current);
                root.applyCss(); root.layout();
                var nodes = List.copyOf(panel.getChildren());
                ProgressBar progress = (ProgressBar) panel.lookup(".progress-bar");
                invoke(support, "updateProgress", new Class<?>[0]);
                assertEquals(nodes, panel.getChildren());
                assertSame(progress, panel.lookup(".progress-bar"));
                assertEquals(.5, progress.getProgress(), .1);
                assertEquals(0, lookups.get());
                support.stop();
            }
            return null;
        });
    }

    @Test void thousandDetachedCardsOwnNoLoadingAnimationsOrActiveClocks() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                CardEpgSupport support = new CardEpgSupport(new VBox());
                for (int i = 0; i < 1_000; i++) {
                    VBox card = new VBox(); support.bind(card, () -> null);
                    Pane panel = (Pane) card.lookup(".virtual-card-epg");
                    invoke(support, "displayLoading", new Class<?>[]{Pane.class, boolean.class}, panel, false);
                    assertNull(panel.lookup(".progress-indicator"));
                    assertEquals(I18n.tr("epgLoading"), ((Label) panel.lookup(".label")).getText());
                }
                assertEquals(javafx.animation.Animation.Status.STOPPED,
                        ((javafx.animation.Timeline) field(support, "clock").get(support)).getStatus());
                assertTrue(((Map<?, ?>) field(support, "attached").get(support)).isEmpty());
                support.stop();
            }
            return null;
        });
    }

    @Test void cachedSummaryReattachmentKeepsTheSameTextAndProgressNodes() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                CardEpgSupport support = new CardEpgSupport(new VBox());
                VBox card = new VBox(); VBox root = new VBox(card); new Scene(root, 320, 300);
                support.bind(card, () -> null);
                Pane panel = (Pane) card.lookup(".virtual-card-epg");
                long now = System.currentTimeMillis();
                var current = new EpgProgramme("Real programme", "", now - 1000, now + 60000);
                CardEpgSupport.display(panel, current, null, now);
                Object binding = ((Map<?, ?>) field(support, "attached").get(support)).get(card);
                field(binding, "current").set(binding, current);
                field(binding, "refreshAt").setLong(binding, now + 60000);
                var nodes = List.copyOf(panel.getChildren());
                root.getChildren().clear(); root.getChildren().add(card);
                assertEquals(nodes, panel.getChildren(), "Recycled cards retain their existing summary controls");
                support.stop();
            }
            return null;
        });
    }

    @Test void wrappedProgrammeTextStaysInsideResponsiveBookmarkCards() throws Exception {
        runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                for (boolean dark : new boolean[]{false, true}) for (int width : new int[]{1900, 1450, 980, 620}) {
                    CardEpgSupport support = new CardEpgSupport(new VBox());
                    var grid = new com.uiptv.widget.ResponsiveCardGrid<Integer>(item -> {
                        var card = new com.uiptv.widget.BookmarkCard("Willow Xtra - 1 (US-HD) with a longer channel title",
                                "wtc05.mi20.cc (ultapulta 22-02-2202) with a very long account name", "", true, "channel", false);
                        return support.bind(card, () -> null);
                    });
                    grid.setVirtualizationThreshold(5); grid.setEpgCardSizingEnabled(true);
                    grid.setCardWidthRange(306, 414); grid.setCardMinHeight(168);
                    grid.setMinWidth(width - 20);
                    grid.setItems(javafx.collections.FXCollections.observableArrayList(java.util.stream.IntStream.range(0, 24).boxed().toList()));
                    ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
                    StackPane root = new StackPane(scroll); new Scene(root, width, 600);
                    root.getStylesheets().add(getClass().getResource(dark ? "/dark-application.css" : "/application.css").toExternalForm());
                    root.resize(width, 600); root.applyCss(); root.layout();
                    grid.resize(width - 20, grid.prefHeight(width - 20));
                    invoke(grid, "updateVirtualWindowGuarded", new Class<?>[0]); grid.layout();
                    long now = System.currentTimeMillis(); int item = 0;
                    for (Node node : grid.lookupAll(".virtual-card-epg")) {
                        boolean missing = item++ % 3 == 0;
                        CardEpgSupport.display((Pane) node,
                                missing ? null : new EpgProgramme("Women's One-Day International Cricket with an unusually long programme title", "", now - 1000, now + 3600000),
                                missing ? null : new EpgProgramme("Women's ODI Cricket Review with a long title that must wrap", "", now + 3600000, now + 7200000));
                    }
                    root.applyCss(); root.layout();
                    invoke(grid, "measureRenderedVirtualCardHeight", new Class<?>[0]); root.layout();
                    save(root, "target/epg-responsive-" + width + "-" + dark + ".png");
                    if (width == 1900) assertTrue(grid.lookupAll(".bookmark-card").stream()
                            .anyMatch(card -> Objects.equals(GridPane.getColumnIndex(card), 4)), "Wide panes remain free to show more than four cards");
                    for (Node node : grid.lookupAll(".bookmark-card")) {
                        Region card = (Region) node;
                        assertTrue(card.getMinHeight() >= Math.ceil(Math.max(168, card.prefHeight(card.getWidth())) * 1.1) - 1,
                                "Full-EPG cards include the requested 10% height at width " + width);
                        Region noGuideSeparator = (Region) card.lookup(".epg-no-guide-separator");
                        if (noGuideSeparator != null) {
                            Label noGuideMessage = (Label) card.lookup(".virtual-card-epg-title");
                            var separatorBounds = card.sceneToLocal(noGuideSeparator.localToScene(noGuideSeparator.getBoundsInLocal()));
                            var messageBounds = card.sceneToLocal(noGuideMessage.localToScene(noGuideMessage.getBoundsInLocal()));
                            assertTrue(separatorBounds.getMaxY() <= messageBounds.getMinY(),
                                    "The bottom placeholder must sit below its separator.");
                            assertTrue(separatorBounds.getMinY() > card.getHeight() * .55,
                                    "The no-guide separator is bottom-aligned inside its card.");
                            assertTrue(card.getHeight() - messageBounds.getMaxY() <= card.getInsets().getBottom() + 2,
                                    "The no-guide message must sit at the card bottom.");
                        }
                        Label account = (Label) card.lookup(".bookmark-channel-account");
                        assertFalse(account.isWrapText());
                        assertEquals(OverrunStyle.ELLIPSIS, account.getTextOverrun());
                        for (Node text : card.lookupAll(".text")) {
                            var bounds = card.sceneToLocal(text.localToScene(text.getBoundsInLocal()));
                            assertTrue(bounds.getMaxY() <= card.getHeight() - card.getInsets().getBottom() + 1,
                                    "Text overlaps card padding at width " + width + ": " + bounds + " card height=" + card.getHeight());
                        }
                    }
                    grid.setItems(javafx.collections.FXCollections.observableArrayList()); support.stop();
                }
            }
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
                            invoke(channels, "applyChannelGridSizing", new Class<?>[0]);
                            Object channelGrid = field(channels, "channelGrid").get(channels);
                            assertEquals(enabled ? (thumbnails ? 306.0 : 288.0) : (thumbnails ? 255.0 : 240.0),
                                    field(channelGrid, "minCardWidth").getDouble(channelGrid));
                            assertEquals(enabled, field(channelGrid, "epgCardSizingEnabled").getBoolean(channelGrid));
                            ((javafx.collections.ObservableList) field(bookmarks, "filteredItems").get(bookmarks)).setAll(bookmark);
                            invoke(bookmarks, "applyBookmarkGridDisplayMode", new Class<?>[]{boolean.class}, thumbnails);
                            Object bookmarkGrid = field(bookmarks, "bookmarkGrid").get(bookmarks);
                            assertEquals(enabled ? 306.0 : 255.0, field(bookmarkGrid, "minCardWidth").getDouble(bookmarkGrid));
                            assertEquals(enabled, field(bookmarkGrid, "epgCardSizingEnabled").getBoolean(bookmarkGrid));
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
                                    assertEquals(I18n.tr("epgNoGuide"), ((Label) panel.getChildren().getLast()).getText());
                                    assertNull(panel.lookup(".epg-programme-time"));
                                    assertNull(panel.lookup(".progress-bar"));
                                    assertTrue(panel.lookupAll(".label").stream().noneMatch(node -> node instanceof Label label
                                            && I18n.tr("epgSourceUnavailable").equals(label.getText())));
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
                VBox.setVgrow(panel, javafx.scene.layout.Priority.ALWAYS);
                card.setMinHeight(220); card.setPrefHeight(220);
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
                Region noGuideSeparator = (Region) panel.lookup(".epg-no-guide-separator");
                assertNotNull(noGuideSeparator);
                assertNotNull(noGuideSeparator.getBorder());
                Color separatorColor = (Color) noGuideSeparator.getBorder().getStrokes().getFirst().getTopStroke();
                assertTrue(separatorColor.getBrightness() > .7,
                        "The no-guide separator remains visible on a selected card in either theme.");
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
    private static String timeRange(long start, long stop) {
        var formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        var zone = java.time.ZoneId.systemDefault();
        return formatter.format(java.time.Instant.ofEpochMilli(start).atZone(zone)) + " – "
                + formatter.format(java.time.Instant.ofEpochMilli(stop).atZone(zone));
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
