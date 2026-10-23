package com.uiptv.ui;

import com.uiptv.application.EpgRefreshJobService.SourceProgress;
import com.uiptv.testsupport.DbBackedUiTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.uiptv.testsupport.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class EpgRefreshSourceCardsTest extends DbBackedUiTest {
    @BeforeAll static void startFx() throws Exception {
        initJavaFx();
        runOnFxThread(() -> { com.uiptv.widget.AppFonts.load(); return null; });
    }

    private static SourceProgress source(String id, String status, String detail) {
        return new SourceProgress(id, "same.example", id + ".xml.gz", status, detail, 123, 5);
    }

    @Test void stagedDataIsLabelledAsUnpublishedUntilCommit() throws Exception {
        runOnFxThread(() -> {
            var cards = new EpgRefreshProgressDialog.SourceCards();
            cards.update(List.of(source("one", "Staging", "123 programmes")));
            var card = cards.cards.get("one");
            cards.update(List.of(source("one", "Staged", "123 programmes")));
            assertEquals(com.uiptv.util.I18n.tr("epgRefreshStaged"), card.status.getText());
            assertEquals(1, card.body.getChildren().size());
            cards.update(List.of(source("one", "Publishing", "")));
            assertEquals(com.uiptv.util.I18n.tr("epgRefreshPublishing"), card.status.getText());
            cards.update(List.of(source("one", "Completed", "")));
            assertEquals(com.uiptv.util.I18n.tr("epgRefreshCompleted"), card.status.getText());
            assertSame(card, cards.cards.get("one"));
            return null;
        });
    }

    @Test void sameHostCardsKeepIdentityAndManualExpansionDuringPolling() throws Exception {
        runOnFxThread(() -> {
            var cards = new EpgRefreshProgressDialog.SourceCards();
            cards.update(List.of(source("one", "Pending", ""), source("two", "Pending", "")));
            var first = cards.cards.get("one");
            var second = cards.cards.get("two");
            assertFalse(first.body.isManaged());
            assertFalse(second.body.isManaged());
            cards.update(List.of(source("one", "Downloading", ""), source("two", "Pending", "")));
            assertTrue(first.body.isManaged());
            first.header.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
            second.header.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.SPACE, false, false, false, false));
            for (int i = 0; i < 20; i++) {
                cards.update(List.of(source("one", "Saving", "123"), source("two", "Downloading", "")));
            }
            assertSame(first, cards.getChildren().getFirst());
            assertSame(second, cards.getChildren().get(1));
            assertFalse(first.body.isManaged(), "User collapse survives processing updates");
            assertTrue(second.body.isManaged(), "User expansion survives processing updates");
            assertTrue(first.name.getText().contains("one.xml.gz"));
            assertTrue(second.name.getText().contains("two.xml.gz"));
            assertEquals(2, first.body.getChildren().size(), "Repeated counters update the existing step");
            return null;
        });
    }

    @Test void processedCardsStayExpandedAndRetainTheirOwnResults() throws Exception {
        runOnFxThread(() -> {
            var cards = new EpgRefreshProgressDialog.SourceCards();
            cards.update(List.of(source("one", "Saving", "first source result"), source("two", "Pending", "")));
            cards.update(List.of(source("one", "Saved", "123 programmes"), source("two", "Downloading", "second source result")));
            assertTrue(cards.cards.get("one").body.isManaged());
            assertTrue(cards.cards.get("two").body.isManaged());
            cards.update(List.of(source("one", "Completed", "matched 5"), source("two", "Failed", "network failure")));
            assertTrue(cards.cards.get("one").body.isManaged());
            assertTrue(cards.cards.get("two").body.isManaged());
            assertEquals(2, cards.cards.get("one").body.getChildren().size(), "Saving and Saved share one step");
            cards.cards.get("two").toggle();
            assertFalse(cards.cards.get("two").body.isManaged());
            assertTrue(cards.cards.get("one").body.isManaged());
            return null;
        });
    }

    @Test void sourceResultsRenderWithoutClippingInBothThemes() throws Exception {
        runOnFxThread(() -> {
            for (boolean dark : new boolean[]{false, true}) {
                var cards = new EpgRefreshProgressDialog.SourceCards();
                cards.update(List.of(source("epg_ripper_IN1", "Completed", "54,983 programmes; 4,405 channels matched"),
                        source("epg_ripper_UK1", "Saving", "160,500 programmes processed"),
                        source("epg_ripper_US1", "Pending", "")));
                var scroll = new javafx.scene.control.ScrollPane(cards);
                scroll.setFitToWidth(true);
                var root = new javafx.scene.layout.VBox(scroll);
                root.setPadding(new javafx.geometry.Insets(16));
                var scene = new javafx.scene.Scene(root, 1000, 600);
                scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
                if (dark) scene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                root.resize(1000, 600); root.applyCss(); root.layout();
                var image = root.snapshot(null, null);
                var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                    output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(output, "png", java.nio.file.Path.of("target/epg-source-cards-"
                        + (dark ? "dark" : "light") + ".png").toFile());
                for (var card : cards.cards.values()) {
                    assertTrue(card.getWidth() <= scroll.getViewportBounds().getWidth() + 1);
                    for (var node : card.body.getChildren()) {
                        var step = (EpgRefreshProgressDialog.SourceStep) node;
                        if (card.body.isManaged()) for (var label : List.of(step.stage, step.detail))
                            assertTrue(label.getHeight() >= label.prefHeight(label.getWidth()) - 1);
                    }
                }
            }
            return null;
        });
    }

    @Test void progressAndWrappedSourceDetailsStayInsideTheirCardsOnShortWindows() throws Exception {
        runOnFxThread(() -> {
            for (boolean dark : new boolean[]{false, true}) for (int width : new int[]{640, 1000, 1368}) for (int fontSize : new int[]{12, 16}) {
                var cards = new EpgRefreshProgressDialog.SourceCards();
                cards.update(List.of(source("one", "Saving", "Processed 52,779 programmes.")));
                cards.update(List.of(source("one", "Saved", "Processed 52,779 programmes.")));
                cards.update(List.of(source("one", "Completed", "Imported 52,779 programmes; 3,570 cached channels have current or upcoming guide data. Full results must wrap inside the card."),
                        source("two", "No matches", "The source downloaded successfully, but it contains no current or upcoming programmes.")));
                var bar = new com.uiptv.widget.SegmentedProgressBar(); bar.setTotal(13);
                var progressRow = new javafx.scene.layout.HBox(14, new javafx.scene.control.Label("13 of 13 sources completed"), bar);
                javafx.scene.layout.HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS); bar.setMinWidth(0);
                var current = new javafx.scene.control.Label("Current source: epgshare01.online · epg_ripper_IN1.xml.gz");
                current.setWrapText(true); current.setMinWidth(0); current.setMaxWidth(Double.MAX_VALUE);
                javafx.scene.layout.HBox.setHgrow(current, javafx.scene.layout.Priority.ALWAYS);
                var details = new javafx.scene.layout.HBox(12, current, new javafx.scene.control.Label("Elapsed: 02:03"));
                var progressCard = EpgRefreshProgressDialog.createProgressCard(progressRow, details);
                var scroll = new javafx.scene.control.ScrollPane(cards); scroll.setFitToWidth(true); scroll.setMinHeight(0);
                scroll.getStyleClass().addAll("transparent-scroll-pane", "epg-refresh-source-list");
                javafx.scene.layout.VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
                var summary = EpgRefreshProgressDialog.createSummaryCard(new javafx.scene.control.Label("Run Summary"),
                        new javafx.scene.control.Label("Completed: 13/13"), new javafx.scene.control.Label("Good: 11"),
                        new javafx.scene.control.Label("Partial: 2"), new javafx.scene.control.Label("Failed: 0"),
                        new javafx.scene.control.Label("Processed 263,125 programmes."));
                var sourceContent = new javafx.scene.layout.VBox(9, scroll, new javafx.scene.control.Separator(), summary);
                sourceContent.setMinHeight(0); javafx.scene.layout.VBox.setVgrow(sourceContent, javafx.scene.layout.Priority.ALWAYS);
                var sources = new javafx.scene.layout.VBox(10, new javafx.scene.control.Label("EPG sources"), sourceContent);
                sources.getStyleClass().addAll("management-popup-card", "reload-column-card", "verification-log-card", "epg-refresh-sources-card");
                sources.setMinHeight(0);
                var heading = new javafx.scene.control.Label("Refresh EPG"); heading.getStyleClass().add("management-popup-title");
                var header = new javafx.scene.layout.VBox(heading);
                var root = EpgRefreshProgressDialog.createLayout(header, progressCard, sources,
                        new javafx.scene.layout.HBox(new javafx.scene.control.Button("Close")));
                root.setStyle("-fx-font-size: " + fontSize + "px;");
                var scene = new javafx.scene.Scene(root, width, 500);
                scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
                if (dark) scene.getStylesheets().add(getClass().getResource("/dark-application.css").toExternalForm());
                root.resize(width, 500); root.applyCss(); root.layout();
                assertTrue(progressCard.getHeight() >= progressCard.prefHeight(progressCard.getWidth()) - 1,
                        "The two-line progress card must never shrink below its content height");
                for (var child : progressCard.getChildren()) {
                    assertTrue(child.getBoundsInParent().getMaxY() <= progressCard.getHeight(), "Progress details overflow");
                }
                assertTrue(summary.getBoundsInParent().getMaxY() <= sourceContent.getHeight()+1, "Summary overflows its section");
                for (var card : cards.cards.values()) {
                    assertTrue(card.getHeight() >= card.prefHeight(card.getWidth()) - 1, "Card must grow with wrapped details");
                    assertTrue(card.body.getBoundsInParent().getMaxY() <= card.getHeight(), "Source body overflows its card");
                    for (var node : card.body.getChildren()) {
                        var step = (EpgRefreshProgressDialog.SourceStep) node;
                        assertTrue(step.stage.getBoundsInParent().getMaxY() <= step.detail.getBoundsInParent().getMinY(),
                                "Each status appears above its explanation");
                        assertEquals(step.stage.getBoundsInParent().getMinX(), step.detail.getBoundsInParent().getMinX(), 1,
                                "Status and explanation share the same left edge");
                        assertTrue(step.stage.getBoundsInParent().getMaxX() <= step.getWidth() + 1);
                        assertTrue(step.detail.getBoundsInParent().getMaxX() <= step.getWidth() + 1);
                        assertTrue(step.detail.getHeight() >= step.detail.prefHeight(step.detail.getWidth()) - 1);
                    }
                }
                var image = root.snapshot(null, null);
                var output = new java.awt.image.BufferedImage(width, 500, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y=0; y<500; y++) for (int x=0; x<width; x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                javax.imageio.ImageIO.write(output,"png",java.nio.file.Path.of("target/epg-popup-"+(dark?"dark":"light")+"-"+width+"-font"+fontSize+".png").toFile());
            }
            return null;
        });
    }
}
