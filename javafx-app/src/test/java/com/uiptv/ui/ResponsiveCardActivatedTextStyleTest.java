package com.uiptv.ui;

import com.uiptv.widget.PlayMenuButton;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the "currently playing" highlight on cards.
 *
 * <p>The {@code :activated} pseudo class marks the item last opened by a
 * double click. When a different item is activated, the previous card loses the
 * pseudo class and must fall back to its ordinary styling. A blanket
 * {@code .card:activated .text} rule used to also match the internal {@code Text}
 * of every {@link Label} skin; JavaFX then reset that skin node to
 * {@code Labeled}'s hardcoded {@code Color.BLACK} initial value instead of
 * re-reading the label's {@code -fx-text-fill}, leaving black text on the dark
 * card background.
 */
class ResponsiveCardActivatedTextStyleTest {
    private static final PseudoClass ACTIVATED = PseudoClass.getPseudoClass("activated");
    private static final List<String> THEMES = List.of("/application.css", "/dark-application.css");

    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void deactivatingACardRestoresTheDefaultTextColoursInEveryTheme() throws Exception {
        for (String theme : THEMES) {
            Snapshot before = runOnFxThread(() -> snapshot(theme, State.PLAIN));
            Snapshot after = runOnFxThread(() -> snapshot(theme, State.ROUND_TRIP));
            assertEquals(before.toString(), after.toString(),
                    theme + ": deactivating a card must restore the default card styling");
        }
    }

    @Test
    void activatingACardStillHighlightsItsLabelsInEveryTheme() throws Exception {
        for (String theme : THEMES) {
            Snapshot plain = runOnFxThread(() -> snapshot(theme, State.PLAIN));
            Snapshot activated = runOnFxThread(() -> snapshot(theme, State.ACTIVATED));
            assertNotEquals(plain.toString(), activated.toString(),
                    theme + ": :activated must still restyle the card");
        }
    }

    @Test
    void darkThemeDefaultTextColoursAreNotBlack() throws Exception {
        runOnFxThread(() -> {
            Snapshot plain = snapshot("/dark-application.css", State.PLAIN);
            for (Paint fill : plain.labelFills()) {
                assertTrue(isLight(fill), "dark theme label text must not be black: " + fill);
            }
            for (Paint fill : plain.textFills()) {
                assertTrue(isLight(fill), "dark theme card Text fill must not be black: " + fill);
            }
            return null;
        });
    }

    /**
     * The "..." affordance of a bookmark card must stay legible on the dark blue
     * background of the item that was last opened by a double click.
     */
    @Test
    void playMenuAffordanceStaysLightOnAnActivatedCard() throws Exception {
        for (String theme : THEMES) {
            IconColours plain = runOnFxThread(() -> iconColours(theme, State.PLAIN));
            IconColours activated = runOnFxThread(() -> iconColours(theme, State.ACTIVATED));

            assertTrue(isLight(activated.ringStroke),
                    theme + ": activated \"...\" ring must be light, was " + activated.ringStroke);
            assertTrue(isLight(activated.dotFill),
                    theme + ": activated \"...\" dots must be light, was " + activated.dotFill);
            assertNotEquals(plain.ringStroke, activated.ringStroke,
                    theme + ": :activated must restyle the \"...\" ring");
            assertNotEquals(plain.dotFill, activated.dotFill,
                    theme + ": :activated must restyle the \"...\" dots");
        }
    }

    private IconColours iconColours(String theme, State state) throws Exception {
        return runOnFxThread(() -> {
            PlayMenuButton playMenu = new PlayMenuButton("menu");
            playMenu.getStyleClass().add("bookmark-play-menu-button");
            VBox card = new VBox(playMenu);
            card.getStyleClass().addAll("uiptv-responsive-card", "bookmark-card");
            Scene scene = new Scene(new StackPane(card), 400, 300);
            scene.getStylesheets().add(url(theme));
            StackPane root = (StackPane) scene.getRoot();
            root.applyCss();
            if (state == State.ACTIVATED) {
                card.pseudoClassStateChanged(ACTIVATED, true);
                root.applyCss();
            }
            Circle ring = (Circle) iconChild(playMenu, "play-menu-icon-ring");
            Circle dot = (Circle) iconChild(playMenu, "play-menu-icon-dot");
            return new IconColours(ring.getStroke(), dot.getFill());
        });
    }

    private static Node iconChild(PlayMenuButton playMenu, String styleClass) {
        Pane icon = (Pane) playMenu.getGraphic();
        return icon.getChildren().stream()
                .filter(node -> node.getStyleClass().contains(styleClass))
                .findFirst()
                .orElseThrow();
    }

    private record IconColours(Paint ringStroke, Paint dotFill) {
    }

    private enum State {
        PLAIN,
        ACTIVATED,
        ROUND_TRIP
    }

    /** @return the resolved text colours in the requested pseudo-class state */
    private Snapshot snapshot(String theme, State state) throws Exception {
        return runOnFxThread(() -> {
            VBox card = card();
            Scene scene = new Scene(new StackPane(card), 400, 300);
            scene.getStylesheets().add(url(theme));
            StackPane root = (StackPane) scene.getRoot();
            root.applyCss();
            if (state != State.PLAIN) {
                card.pseudoClassStateChanged(ACTIVATED, true);
                root.applyCss();
            }
            if (state == State.ROUND_TRIP) {
                card.pseudoClassStateChanged(ACTIVATED, false);
                root.applyCss();
            }
            return new Snapshot(
                    cardLabels(card).stream().map(Label::getTextFill).toList(),
                    cardTexts(card).stream().map(Text::getFill).toList());
        });
    }

    private static VBox card() {
        Label title = new Label("Channel A");
        title.getStyleClass().addAll("strong-label", "account-drawer-channel-title");
        Label meta = new Label("HD");
        meta.getStyleClass().add("account-drawer-channel-meta");

        Text titleText = new Text("Channel A");
        titleText.getStyleClass().add("bookmark-channel-title-text");
        Text typeText = new Text("Xtream");
        typeText.getStyleClass().add("account-card-type-text");

        VBox card = new VBox(title, meta, titleText, typeText);
        card.getStyleClass().addAll("uiptv-responsive-card", "bookmark-card");
        return card;
    }

    private static List<Label> cardLabels(VBox card) {
        return List.of((Label) card.getChildren().get(0), (Label) card.getChildren().get(1));
    }

    private static List<Text> cardTexts(VBox card) {
        return List.of((Text) card.getChildren().get(2), (Text) card.getChildren().get(3));
    }

    private static boolean isLight(Paint paint) {
        if (!(paint instanceof Color color)) {
            return true;
        }
        // quick sRGB luminance; anything near zero is unreadable on a dark surface
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue() > 0.25;
    }

    private static String url(String resource) {
        return Objects.requireNonNull(ResponsiveCardActivatedTextStyleTest.class.getResource(resource))
                .toExternalForm();
    }

    private record Snapshot(List<Paint> labelFills, List<Paint> textFills) {
        @Override
        public String toString() {
            return "labels=" + labelFills + " texts=" + textFills;
        }
    }
}
