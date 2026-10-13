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
                        new EpgProgramme("Documentary", "Illustrative test data", hour + 3_600_000, hour + 2 * 3_600_000),
                        new EpgProgramme("Film", "Illustrative test data", hour + 2 * 3_600_000, hour + 4 * 3_600_000))));
            }
            LiveGuideUI guide = new LiveGuideUI(); guide.setContext("account-1");
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
            WritableImage snapshot = root.snapshot(null, null);
            BufferedImage png = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            Path output = Path.of("target/epg-desktop-preview.png"); Files.createDirectories(output.getParent()); ImageIO.write(png, "png", output.toFile());
            CheckBox showAll = (CheckBox) field("showAllChannels").get(guide); showAll.fire(); assertEquals(6, rows.getChildren().size());
            guide.setContext("account-2"); assertTrue(rows.getChildren().isEmpty()); guide.dispose();
            return null;
        });
    }

    @Test void playerDetailsAppearBelowVideoAndHideWhenGuideIsAbsent() throws Exception {
        runOnFxThread(() -> {
            com.uiptv.widget.AppFonts.load();
            PlayerEpgUI panel = new PlayerEpgUI();
            long now = System.currentTimeMillis();
            VBox video = new VBox(new Label("Video area (test)")); video.setMinHeight(230); video.setStyle("-fx-background-color: black; -fx-padding: 12;");
            VBox root = new VBox(8, video, panel); Scene scene = new Scene(root, 470, 530);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/dark-application.css")).toExternalForm());
            panel.display(new PlayerEpgApplicationService.Result("BBC One", new EpgProgramme("Evening News", "The latest news and analysis. Illustrative test data.", now - 1800000, now + 1800000),
                    new EpgProgramme("Documentary", "A journey through the natural world. Illustrative test data.", now + 1800000, now + 5400000)));
            root.applyCss(); root.resize(470, 530); root.layout();
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
