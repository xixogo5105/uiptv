package com.uiptv.ui;

import com.uiptv.widget.PlayingCardIndicator;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void activatingACardPreservesItsThemeTextColours() throws Exception {
        for (String theme : THEMES) {
            Snapshot plain = runOnFxThread(() -> snapshot(theme, State.PLAIN));
            Snapshot activated = runOnFxThread(() -> snapshot(theme, State.ACTIVATED));
            assertEquals(plain, activated,
                    theme + ": activation must not recolour the card text");
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

    @Test
    void broadcastIndicatorAppearsOnActivatedCardsWithThemeContrast() throws Exception {
        for (String theme : THEMES) {
            IndicatorSnapshot hidden = runOnFxThread(() -> indicatorSnapshot(theme, State.PLAIN));
            IndicatorSnapshot shown = runOnFxThread(() -> indicatorSnapshot(theme, State.ACTIVATED));

            assertEquals(0, hidden.opacity(), 0.001, theme + ": indicator must start hidden");
            assertEquals(1, shown.opacity(), 0.001, theme + ": indicator must appear on activation");
            if (theme.equals("/dark-application.css")) {
                assertTrue(isLight(shown.arcStroke()), "dark theme broadcast arcs should be light");
                assertTrue(isLight(shown.dotFill()), "dark theme broadcast dot should be light");
            } else {
                assertFalse(isLight(shown.arcStroke()), "light theme broadcast arcs should be dark");
                assertFalse(isLight(shown.dotFill()), "light theme broadcast dot should be dark");
            }
        }
    }

    private IndicatorSnapshot indicatorSnapshot(String theme, State state) throws Exception {
        return runOnFxThread(() -> {
            VBox card = card();
            PlayingCardIndicator indicator = new PlayingCardIndicator();
            card.getChildren().add(indicator);
            Scene scene = new Scene(new StackPane(card), 400, 300);
            scene.getStylesheets().add(url(theme));
            StackPane root = (StackPane) scene.getRoot();
            root.applyCss();
            if (state == State.ACTIVATED) {
                card.pseudoClassStateChanged(ACTIVATED, true);
                root.applyCss();
            }
            SVGPath arc = (SVGPath) indicator.lookup(".playing-card-broadcast-arc");
            Circle dot = (Circle) indicator.lookup(".playing-card-broadcast-dot");
            return new IndicatorSnapshot(indicator.getOpacity(), arc.getStroke(), dot.getFill());
        });
    }

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
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue() > 0.25;
    }

    private static String url(String resource) {
        return Objects.requireNonNull(ResponsiveCardActivatedTextStyleTest.class.getResource(resource))
                .toExternalForm();
    }

    private enum State {
        PLAIN,
        ACTIVATED,
        ROUND_TRIP
    }

    private record Snapshot(List<Paint> labelFills, List<Paint> textFills) {
    }

    private record IndicatorSnapshot(double opacity, Paint arcStroke, Paint dotFill) {
    }
}
