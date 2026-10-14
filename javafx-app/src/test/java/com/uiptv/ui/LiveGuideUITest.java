package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.model.Channel;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.util.AccountType;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static com.uiptv.testsupport.FxTestSupport.*;

class LiveGuideUITest extends DbBackedUiTest {
    @BeforeAll static void startFx() throws Exception { initJavaFx(); }

    @Test
    void programmeTitlesWrapOnlyBetweenWords() throws Exception {
        runOnFxThread(() -> {
            var font = javafx.scene.text.Font.font(16);
            var measure = new javafx.scene.text.Text("Titles stay"); measure.setFont(font);
            double width = measure.getLayoutBounds().getWidth() + 1;
            assertEquals("Titles stay\non one\nline", LiveGuideUI.wrapProgrammeTitle("Titles stay on one line", width, font, 3));
            assertEquals("Titanic", LiveGuideUI.wrapProgrammeTitle("Titanic", 8, font, 3));
            assertEquals("one\ntwo\nthree…", LiveGuideUI.wrapProgrammeTitle("one two three four", 1, font, 3));
            return null;
        });
    }

    @Test void rendersAlignedTimelineAndClearsOldAccountRows() throws Exception {
        runOnFxThread(() -> {
            com.uiptv.widget.AppFonts.load();
            long now = System.currentTimeMillis();
            long hour = Instant.now().atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
            List<LiveGuideApplicationService.Row> fixture = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Channel channel = new Channel(); channel.setName(List.of("BBC One", "BBC Two", "ITV1", "Channel 4").get(i)); channel.setChannelId("provider-" + i);
                fixture.add(new LiveGuideApplicationService.Row("account-1", "Example account", "", channel, List.of(
                        new EpgProgramme("Evening News", "Illustrative test data", hour - 3_600_000, hour + 3_600_000),
                        new EpgProgramme("Titles stay on one line with whole words wrapping below", "Illustrative test data", hour + 3_600_000, hour + 2 * 3_600_000),
                        new EpgProgramme("Film", "Illustrative test data", hour + 2 * 3_600_000, hour + 4 * 3_600_000),
                        new EpgProgramme("Morphle", "Short programme", hour + 4 * 3_600_000, hour + 4 * 3_600_000 + 180_000))));
            }
            LiveGuideUI guide = new LiveGuideUI(); guide.setContext("account-1");
            fixture.getFirst().channel().setLogo(Objects.requireNonNull(getClass().getResource("/icon.png")).toExternalForm());
            boolean originalThumbnailMode = ThumbnailAwareUI.areThumbnailsEnabled();
            ThumbnailAwareUI.notifyThumbnailModeChanged(true);
            Channel unavailable = new Channel(); unavailable.setName("No guide channel"); unavailable.setChannelId("unavailable");
            fixture.add(new LiveGuideApplicationService.Row("account-2", "Other account", "bookmark", unavailable, List.of()));
            field("data").set(guide, fixture); field("loadedAt").setLong(guide, now); guide.refreshIfNeeded();
            StackPane root = new StackPane(guide); Scene scene = new Scene(root, 1200, 440);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/dark-application.css")).toExternalForm());
            root.applyCss(); root.resize(1200, 440); root.layout();
            VBox rows = (VBox) field("rows").get(guide);
            assertEquals(5, rows.getChildren().size());
            Pane first = (Pane) ((HBox) rows.getChildren().get(1)).getChildren().get(1);
            Pane second = (Pane) ((HBox) rows.getChildren().get(2)).getChildren().get(1);
            assertEquals(first.getChildren().getFirst().getLayoutX(), second.getChildren().getFirst().getLayoutX());
            assertEquals(477, ((Button) first.getChildren().getFirst()).getPrefWidth(), .01);
            Button programmeBlock = (Button) first.getChildren().getFirst();
            assertEquals(1, programmeBlock.getBackground().getFills().size());
            assertEquals(javafx.geometry.Insets.EMPTY, programmeBlock.getBackground().getFills().getFirst().getInsets());
            assertEquals(programmeBlock.getBackground().getFills().getFirst().getRadii(), programmeBlock.getBorder().getStrokes().getFirst().getRadii());
            assertNull(programmeBlock.getEffect());
            Button shortBlock = (Button) first.getChildren().get(3);
            assertEquals("Morphle", shortBlock.getText());
            assertEquals("Morphle", shortBlock.getAccessibleText());
            assertTrue(shortBlock.getTooltip().getText().startsWith("Morphle"));
            assertFalse(programmeBlock.isWrapText());
            assertEquals(javafx.scene.control.OverrunStyle.ELLIPSIS, programmeBlock.getTextOverrun());
            assertNotNull(((Button) ((HBox) rows.getChildren().get(1)).getChildren().getFirst()).getGraphic().lookup(".channel-logo-view"));
            ThumbnailAwareUI.notifyThumbnailModeChanged(false); guide.refreshIfNeeded();
            assertNull(((Button) ((HBox) rows.getChildren().get(1)).getChildren().getFirst()).getGraphic().lookup(".channel-logo-view"));
            ThumbnailAwareUI.notifyThumbnailModeChanged(originalThumbnailMode); guide.refreshIfNeeded();
            WritableImage snapshot = root.snapshot(null, null);
            BufferedImage png = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            Path output = Path.of("target/epg-desktop-preview.png"); Files.createDirectories(output.getParent()); ImageIO.write(png, "png", output.toFile());
            CheckBox showAll = (CheckBox) field("showAllChannels").get(guide); showAll.fire(); assertEquals(6, rows.getChildren().size());
            guide.setContext("account-2"); assertTrue(rows.getChildren().isEmpty()); guide.dispose();
            return null;
        });
    }

    @Test void onlyDoubleClickPlaysAndIndicatorFollowsAccountAndChannelIdentity() throws Exception {
        runOnFxThread(() -> {
            java.util.concurrent.atomic.AtomicInteger plays = new java.util.concurrent.atomic.AtomicInteger();
            LiveGuideUI guide = new LiveGuideUI() {
                @Override protected void play(LiveGuideApplicationService.Row row) { plays.incrementAndGet(); }
            };
            Channel channel = new Channel(); channel.setChannelId("provider-1"); channel.setName("News");
            long now = System.currentTimeMillis();
            var programme = new EpgProgramme("News at Ten", "", now - 60_000, now + 3_600_000);
            field("data").set(guide, List.of(new LiveGuideApplicationService.Row("account-one", "One", "", channel, List.of(programme))));
            field("loadedAt").setLong(guide, now); guide.refreshIfNeeded();
            StackPane root = new StackPane(guide); new Scene(root, 900, 400);
            VBox rows = (VBox) field("rows").get(guide);
            HBox row = (HBox) rows.getChildren().get(1);
            Button name = (Button) row.getChildren().getFirst();
            Button block = (Button) ((Pane) row.getChildren().get(1)).getChildren().getFirst();
            try {
                for (Button target : List.of(name, block)) {
                    target.fire();
                    mouseClick(target, 1);
                    assertEquals(0, plays.get());
                }
                assertTrue(((Label) field("selectedProgramme").get(guide)).getText().contains("News at Ten"));
                mouseClick(name, 2); assertEquals(1, plays.get());
                mouseClick(block, 2); assertEquals(2, plays.get());
                var account = new com.uiptv.model.Account(); account.setDbId("other-account"); account.setAction(com.uiptv.model.Account.AccountAction.itv);
                com.uiptv.player.MediaPlayerFactory.updateGuideContext(account, channel);
                Node indicator = name.getGraphic().lookup(".playing-card-indicator"); assertFalse(indicator.isVisible());
                account.setDbId("account-one"); com.uiptv.player.MediaPlayerFactory.updateGuideContext(account, channel);
                assertTrue(indicator.isVisible()); assertTrue(indicator.isManaged());
                Channel otherChannel = new Channel(); otherChannel.setChannelId("provider-2");
                com.uiptv.player.MediaPlayerFactory.updateGuideContext(account, otherChannel);
                assertFalse(indicator.isVisible());
            } finally {
                guide.dispose(); root.getChildren().clear(); com.uiptv.player.MediaPlayerFactory.updateGuideContext(null, null);
            }
            return null;
        });
    }

    private static void mouseClick(Button button, int count) {
        javafx.event.Event.fireEvent(button, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, count, false, false, false, false,
                false, false, false, false, false, true, null));
    }

    @Test void largeDesktopGuideOnlyCreatesViewportRowsAndRestoresPlayingIndicator() throws Exception {
        final LiveGuideUI[] guideRef = new LiveGuideUI[1];
        final StackPane[] rootRef = new StackPane[1];
        runOnFxThread(() -> {
            long now = System.currentTimeMillis();
            List<LiveGuideApplicationService.Row> fixture = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                Channel channel = new Channel(); channel.setName("Sample channel " + i); channel.setChannelId("provider-" + i);
                fixture.add(new LiveGuideApplicationService.Row("account-one", "Sample account", "bookmark-" + i, channel,
                        i < 10 ? List.of(new EpgProgramme("Sample programme", "", now - 60_000, now + 3_600_000)) : List.of()));
            }
            LiveGuideUI guide = new LiveGuideUI(); guide.setContext("account-one");
            field("data").set(guide, fixture); field("loadedAt").setLong(guide, now); guide.refreshIfNeeded();
            StackPane root = new StackPane(guide); Scene scene = new Scene(root, 1100, 500);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/dark-application.css")).toExternalForm());
            root.resize(1100, 500); root.applyCss(); root.layout();
            assertEquals(10, ((List<?>) field("filteredRows").get(guide)).size());
            ((CheckBox) field("showAllChannels").get(guide)).fire();
            root.applyCss(); root.layout();
            assertEquals(5000, ((List<?>) field("filteredRows").get(guide)).size());
            assertTrue(((Map<?, ?>) field("playingIndicators").get(guide)).size() <= 20);
            ScrollPane scroll = (ScrollPane) field("scroll").get(guide); scroll.setVvalue(1);
            guideRef[0] = guide; rootRef[0] = root;
            return null;
        });
        runOnFxThread(() -> {
            LiveGuideUI guide = guideRef[0]; StackPane root = rootRef[0];
            root.applyCss(); root.layout();
            try {
                Map<LiveGuideApplicationService.Row, com.uiptv.widget.PlayingCardIndicator> indicators =
                        (Map<LiveGuideApplicationService.Row, com.uiptv.widget.PlayingCardIndicator>) field("playingIndicators").get(guide);
                assertTrue(indicators.size() <= 20);
                assertTrue(indicators.keySet().stream().anyMatch(row -> row.channel().getChannelId().equals("provider-4999")));
                var account = new com.uiptv.model.Account(); account.setDbId("account-one"); account.setAction(com.uiptv.model.Account.AccountAction.itv);
                Channel playing = new Channel(); playing.setChannelId("provider-4999");
                com.uiptv.player.MediaPlayerFactory.updateGuideContext(account, playing);
                assertEquals(1, indicators.values().stream().filter(Node::isVisible).count());
                root.applyCss(); root.layout();
                var activeIndicator = indicators.values().stream().filter(Node::isVisible).findFirst().orElseThrow();
                assertEquals(1.0, activeIndicator.getOpacity(), .001);
                assertTrue(activeIndicator.getBoundsInParent().getWidth() >= 28);
                WritableImage snapshot = root.snapshot(null, null);
                BufferedImage png = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                ImageIO.write(png, "png", Path.of("target/epg-desktop-virtual-preview.png").toFile());
                ((ScrollPane) field("scroll").get(guide)).setVvalue(0);
            } catch (Throwable failure) { guide.dispose(); root.getChildren().clear(); throw failure; }
            return null;
        });
        runOnFxThread(() -> {
            LiveGuideUI guide = guideRef[0];
            assertTrue(((Map<LiveGuideApplicationService.Row, ?>) field("playingIndicators").get(guide)).keySet().stream()
                    .anyMatch(row -> row.channel().getChannelId().equals("provider-0")));
            ((ScrollPane) field("scroll").get(guide)).setVvalue(1);
            return null;
        });
        runOnFxThread(() -> {
            LiveGuideUI guide = guideRef[0];
            try {
                Map<?, com.uiptv.widget.PlayingCardIndicator> indicators = (Map<?, com.uiptv.widget.PlayingCardIndicator>) field("playingIndicators").get(guide);
                assertEquals(1, indicators.values().stream().filter(Node::isVisible).count());
                assertTrue(indicators.size() <= 20);
            } finally { guide.dispose(); rootRef[0].getChildren().clear(); com.uiptv.player.MediaPlayerFactory.updateGuideContext(null, null); }
            return null;
        });
    }

    @Test void playerDetailsAppearBelowVideoAndHideWhenGuideIsAbsent() throws Exception {
        runOnFxThread(() -> {
            com.uiptv.widget.AppFonts.load();
            PlayerEpgUI panel = new PlayerEpgUI();
            long now = System.currentTimeMillis();
            VBox video = new VBox(new Label("Video area (test)")); video.setMinHeight(230); video.setStyle("-fx-background-color: black; -fx-padding: 12;");
            VBox root = new VBox(8, video, panel); Scene scene = new Scene(root, 470, 730);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/dark-application.css")).toExternalForm());
            panel.display(new PlayerEpgApplicationService.Result("BBC One", new EpgProgramme("Evening News", "The latest news and analysis. Illustrative test data.", now - 1800000, now + 1800000),
                    new EpgProgramme("Documentary", "A journey through the natural world. Illustrative test data.", now + 1800000, now + 5400000)));
            root.applyCss(); root.resize(470, 730); root.layout(); root.layout();
            assertTrue(panel.isVisible()); assertTrue(panel.isManaged());
            assertTrue(panel.getBoundsInParent().getMinY() >= video.getBoundsInParent().getMaxY());
            WritableImage snapshot = root.snapshot(null, null);
            BufferedImage image = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            ImageIO.write(image, "png", Path.of("target/epg-player-desktop-preview.png").toFile());
            panel.display(new PlayerEpgApplicationService.Result("No guide", null, null)); assertFalse(panel.isManaged());
            return null;
        });
    }

    @Test void singleColumnKeepsLongTitlesAndNavigationVisibleWithoutOverlapping() throws Exception {
        runOnFxThread(() -> {
            com.uiptv.widget.AppFonts.load();
            for (int height : new int[]{620, 760}) {
                PlayerEpgUI panel = new PlayerEpgUI();
                HBox video = new HBox(new Label("Video area (test)")); video.setStyle("-fx-background-color: black; -fx-padding: 12;");
                VBox controls = new VBox(panel);
                StackPane navigation = new StackPane(new VBox(10, new Label("Live TV · Channel list"), new Label("BBC One"), new Label("BBC Two"), new Label("ITV1")));
                GridPane grid = new GridPane(); grid.getChildren().addAll(video, controls, navigation);
                HBox root = new HBox(grid); HBox.setHgrow(grid, Priority.ALWAYS);
                Scene scene = new Scene(root, 490, height);
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/dark-application.css")).toExternalForm());
                long now = System.currentTimeMillis();
                panel.display(new PlayerEpgApplicationService.Result("BBC One", new EpgProgramme("Evening News: International headlines and reports from correspondents around the world", "A long description that remains accessible through programme details.", now - 1800000, now + 1800000),
                        new EpgProgramme("Documentary: Exploring the extraordinary wildlife of our changing planet", "Full programme description.", now + 1800000, now + 5400000)));
                var ui = new com.uiptv.ui.main.MainApplicationUI(null, null, null, null, 490, height, true);
                for (var entry : Map.of("mainContent", root, "responsiveContent", grid, "embeddedPlayer", video, "navigationShell", navigation, "playerAdjacentControls", controls, "activeTabPane", new TabPane()).entrySet()) {
                    Field field = ui.getClass().getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(ui, entry.getValue());
                }
                Method layout = ui.getClass().getDeclaredMethod("applyStackedEmbeddedLayout"); layout.setAccessible(true);
                root.resize(490, height); grid.resize(490, height); root.applyCss();
                for (int pass = 0; pass < 3; pass++) { layout.invoke(ui); root.layout(); }
                assertTrue(controls.getBoundsInParent().getMinY() >= video.getBoundsInParent().getMaxY());
                assertTrue(navigation.getBoundsInParent().getMinY() >= controls.getBoundsInParent().getMaxY());
                assertTrue(navigation.getHeight() >= 120, "Channel list must retain usable height");
                assertEquals(0, panel.lookupAll(".scroll-pane").size());
                for (Node title : panel.lookupAll(".epg-programme-title")) assertTrue(title.getBoundsInParent().getHeight() >= ((Label) title).prefHeight(title.getBoundsInParent().getWidth()) - 1);
                if (height == 620) {
                    WritableImage snapshot = root.snapshot(null, null);
                    BufferedImage image = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    ImageIO.write(image, "png", Path.of("target/epg-single-column-preview.png").toFile());
                }
            }
            return null;
        });
    }

    @Test void playerCardsAlignAndExtraDetailsStayInsideTheAppInBothThemes() throws Exception {
        runOnFxThread(() -> {
            com.uiptv.widget.AppFonts.load();
            for (boolean dark : new boolean[]{false, true}) {
                var ui = new com.uiptv.ui.main.MainApplicationUI(null, null, null, null, 470, 750, true);
                Method create = ui.getClass().getDeclaredMethod("createPlayerAdjacentControls"); create.setAccessible(true);
                VBox controls = (VBox) create.invoke(ui); controls.setVisible(true); controls.setManaged(true);
                PlayerEpgUI panel = (PlayerEpgUI) controls.getChildren().getFirst();
                Region videoSurface = new Region(); videoSurface.setPrefHeight(180); videoSurface.setStyle("-fx-background-color: black;");
                VBox videoContainer = new VBox(videoSurface); videoContainer.setPadding(new javafx.geometry.Insets(5));
                VBox root = new VBox(8, videoContainer, controls); Scene scene = new Scene(root, 470, 750);
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
                if (dark) scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/dark-application.css")).toExternalForm());
                long now = System.currentTimeMillis();
                panel.display(new PlayerEpgApplicationService.Result("Sample channel", new EpgProgramme("Current programme", "Full description already visible.", now - 1000, now + 100000),
                        new EpgProgramme("Next programme", "", now + 100000, now + 200000)));
                root.resize(470, 750); root.applyCss(); root.layout(); root.layout();
                Node card = panel.lookup(".epg-current-card");
                assertEquals(videoSurface.localToScene(videoSurface.getBoundsInLocal()).getMinX(), card.localToScene(card.getBoundsInLocal()).getMinX(), .1);
                assertEquals(videoSurface.localToScene(videoSurface.getBoundsInLocal()).getMaxX(), card.localToScene(card.getBoundsInLocal()).getMaxX(), .1);
                assertTrue(panel.lookupAll(".hyperlink").stream().noneMatch(Node::isManaged));
                String fullDescription = "Additional programme information. ".repeat(30);
                panel.display(new PlayerEpgApplicationService.Result("Sample channel", new EpgProgramme("Current programme", fullDescription, now - 1000, now + 100000), null));
                root.applyCss(); root.layout();
                Hyperlink link = (Hyperlink) panel.lookup(".hyperlink"); assertTrue(link.isManaged());
                int windows = javafx.stage.Window.getWindows().size(); link.fire(); root.applyCss(); root.layout();
                Node inline = panel.lookup(".epg-inline-details"); assertTrue(inline.isManaged()); assertFalse(link.isManaged()); assertEquals(panel, inline.getParent());
                assertEquals(windows, javafx.stage.Window.getWindows().size());
                WritableImage screenshot = root.snapshot(null, null);
                BufferedImage image = new BufferedImage((int) screenshot.getWidth(), (int) screenshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, screenshot.getPixelReader().getArgb(x, y));
                ImageIO.write(image, "png", Path.of("target/epg-inline-details-" + (dark ? "dark" : "light") + "-preview.png").toFile());
                assertTrue(inline.lookupAll(".label").stream().anyMatch(node -> node instanceof Label label && fullDescription.equals(label.getText())));
                ((Button) inline.lookup(".uiptv-circular-close-button")).fire(); assertFalse(inline.isManaged()); assertTrue(link.isManaged());
            }
            return null;
        });
    }

    @Test void sameEpgFieldIsAvailableForEveryAccountType() throws Exception {
        runOnFxThread(() -> {
            ManageAccountUI ui = new ManageAccountUI();
            Method populate = ManageAccountUI.class.getDeclaredMethod("populateForm", AccountType.class); populate.setAccessible(true);
            Field epgField = ManageAccountUI.class.getDeclaredField("epg"); epgField.setAccessible(true);
            Node epg = (Node) epgField.get(ui);
            for (AccountType type : AccountType.values()) { populate.invoke(ui, type); assertNotNull(epg.getParent(), type.name()); assertFalse(epg.isDisabled()); }
            return null;
        });
    }
    private Field field(String name) throws Exception { Field field = LiveGuideUI.class.getDeclaredField(name); field.setAccessible(true); return field; }
}
