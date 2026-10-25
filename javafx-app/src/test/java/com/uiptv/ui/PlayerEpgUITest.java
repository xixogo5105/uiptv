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
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static com.uiptv.testsupport.FxTestSupport.*;

class PlayerEpgUITest extends DbBackedUiTest {
    @BeforeAll static void startFx() throws Exception { initJavaFx(); }

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
            root.applyCss(); root.layout();
            WritableImage snapshot = root.snapshot(null, null);
            BufferedImage image = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            ImageIO.write(image, "png", Path.of("target/epg-player-desktop-preview.png").toFile());
            panel.display(new PlayerEpgApplicationService.Result("No guide", null, null)); assertFalse(panel.isManaged());
            panel.display(new PlayerEpgApplicationService.Result("No guide", null, null, "NO_MATCH"));
            assertFalse(panel.isVisible());
            assertFalse(panel.isManaged());
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
                Label inlineDescription = (Label) panel.lookup(".epg-current-card .epg-programme-description");
                assertTrue(inlineDescription.isManaged()); assertEquals(fullDescription, inlineDescription.getText());
                assertTrue(panel.lookupAll(".hyperlink").stream().noneMatch(Node::isManaged));
                Node inline = panel.lookup(".epg-inline-details"); assertFalse(inline.isManaged());
                WritableImage screenshot = root.snapshot(null, null);
                BufferedImage image = new BufferedImage((int) screenshot.getWidth(), (int) screenshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, screenshot.getPixelReader().getArgb(x, y));
                ImageIO.write(image, "png", Path.of("target/epg-inline-details-" + (dark ? "dark" : "light") + "-preview.png").toFile());
                assertFalse(inline.isManaged());
            }
            return null;
        });
    }

    @Test void unchangedProgrammeAndCompactModePreserveDetailsTooltipsAndLayout() throws Exception {
        runOnFxThread(() -> {
            var panel = new PlayerEpgUI(); panel.setCompact(true);
            long now = System.currentTimeMillis();
            var result = new PlayerEpgApplicationService.Result("Station",new EpgProgramme("Current", "Long description. ".repeat(40),now-60000,now+3600000),null);
            panel.display(result);
            var root = new VBox(panel); root.resize(390,650); root.applyCss(); root.layout();
            Label description = (Label) panel.lookup(".epg-current-card .epg-programme-description");
            assertTrue(description.isManaged(), "Available descriptions must remain visible in compact mode");
            assertEquals(result.now().description(), description.getText());
            assertTrue(panel.lookupAll(".hyperlink").stream().noneMatch(Node::isManaged));
            Node details = panel.lookup(".epg-inline-details");
            Label title = (Label)panel.lookup(".epg-current-card .epg-programme-title");
            Tooltip tooltip = title.getTooltip();
            var styleChanges = new java.util.concurrent.atomic.AtomicInteger();
            panel.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) _ -> styleChanges.incrementAndGet());
            double height = panel.prefHeight(390);
            for (int i=0;i<20;i++) {
                panel.setCompact(true);
                panel.display(new PlayerEpgApplicationService.Result(result.channelName(),result.now(),result.next()));
            }
            root.layout();
            assertSame(tooltip,title.getTooltip(),"Unchanged programmes must not recreate tooltips");
            assertFalse(details.isManaged(),"Inline descriptions do not need a replacement details panel");
            assertEquals(0,styleChanges.get(),"Repeated narrow-layout passes must not remove and re-add compact CSS");
            assertEquals(height,panel.prefHeight(390),.1);
            return null;
        });
    }

    @Test void repeatedLayoutDoesNotReloadGuideData() throws Exception {
        runOnFxThread(() -> {
            com.uiptv.player.MediaPlayerFactory.updateGuideContext(null, null);
            var panel = new PlayerEpgUI(); panel.setCompact(true);
            var root = new VBox(panel); new Scene(root,390,650);
            long now = System.currentTimeMillis();
            panel.display(new PlayerEpgApplicationService.Result("Station",new EpgProgramme("Current","Details",now-60000,now+3600000),null));
            Field generationField = PlayerEpgUI.class.getDeclaredField("generation"); generationField.setAccessible(true);
            var generation = (java.util.concurrent.atomic.AtomicInteger)generationField.get(panel);
            int before = generation.get();
            for (int i=0; i<100; i++) { root.resize(390,650); root.applyCss(); root.layout(); }
            assertTrue(panel.isVisible()); assertTrue(panel.isManaged());
            assertEquals(before, generation.get(), "Layout work must not start new live guide requests");
            root.getChildren().clear();
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
}
