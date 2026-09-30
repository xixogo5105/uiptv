package com.uiptv.widget;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * The "..." affordance shown on a card.
 * <p>
 * Building one is not free: a {@link Tooltip} plus a {@link Pane} holding a ring and three dots is
 * created for every card in the catalogue, and the channel grid retains up to
 * {@code uiptv.cardGrid.detachedCardCache} off-screen cards, so the whole cost is held in memory
 * for cards the user is not even looking at.
 * <p>
 * The button therefore starts hollow. It keeps its slot in the card's layout - the stylesheet pins
 * it to 24x24 - so nothing reflows when it appears, but the graphic and tooltip are only built on
 * the first {@link #reveal()}. That is driven by card hover from
 * {@link ResponsiveCardGrid#configureCard}, so exactly one button is ever materialised: the one on
 * the card under the pointer. Direct hover or focus on the button also reveals it, which keeps the
 * control usable when it is used outside a grid.
 */
public class PlayMenuButton extends Button implements HoverRevealAction {
    private static final double ICON_SIZE = 24.0;

    private final String tooltipText;
    private boolean materialised;
    private boolean revealed;

    public PlayMenuButton(String accessibleText) {
        this.tooltipText = accessibleText;
        getStyleClass().setAll("button", "play-menu-button");
        setMnemonicParsing(false);
        setFocusTraversable(true);
        setAccessibleText(accessibleText);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> event.consume());

        // Hidden until revealed, but still hit-testable so the reveal triggers reliably and a
        // click that lands before the hover is processed still opens the menu.
        setOpacity(0.0);

        // Self-reveal only. Concealing is deliberately left to ResponsiveCardGrid: if this button
        // concealed itself on MOUSE_EXITED it would disappear the moment the pointer moved off it
        // while still over the card, fighting the grid's card-level hover.
        setOnMouseEntered(event -> reveal());
        focusedProperty().addListener((_, _, isFocused) -> {
            if (Boolean.TRUE.equals(isFocused)) {
                reveal();
            }
        });
    }

    @Override
    public void reveal() {
        revealed = true;
        if (!materialised) {
            materialise();
        }
        setOpacity(1.0);
    }

    @Override
    public void conceal() {
        if (!revealed) {
            return;
        }
        revealed = false;
        setOpacity(0.0);
    }

    @Override
    public boolean isRevealed() {
        return revealed;
    }

    /** Whether the graphic and tooltip have actually been built. Exposed for tests. */
    public boolean isMaterialised() {
        return materialised;
    }

    private void materialise() {
        materialised = true;

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setHideDelay(Duration.millis(80));
        tooltip.setShowDuration(Duration.seconds(4));
        setTooltip(tooltip);

        Pane icon = new Pane();
        icon.getStyleClass().add("play-menu-icon");
        icon.setMouseTransparent(true);
        icon.setMinSize(ICON_SIZE, ICON_SIZE);
        icon.setPrefSize(ICON_SIZE, ICON_SIZE);
        icon.setMaxSize(ICON_SIZE, ICON_SIZE);

        Circle ring = new Circle(12.0, 12.0, 8.5);
        ring.getStyleClass().add("play-menu-icon-ring");
        icon.getChildren().addAll(
                ring,
                createDot(8.0),
                createDot(12.0),
                createDot(16.0)
        );
        setGraphic(icon);
    }

    private static Circle createDot(double centerX) {
        Circle dot = new Circle(centerX, 12.0, 1.25);
        dot.getStyleClass().add("play-menu-icon-dot");
        return dot;
    }
}
