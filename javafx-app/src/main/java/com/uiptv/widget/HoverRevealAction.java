package com.uiptv.widget;

/**
 * A card sub-control that is only built once the user actually points at (or focuses) the card
 * that contains it.
 * <p>
 * Implemented by {@link PlayMenuButton}, the "..." affordance. {@link ResponsiveCardGrid}
 * discovers these inside a card when the card is configured and reveals them on hover, so a
 * catalogue of several hundred cards does not pay for affordances nobody has looked at yet.
 */
public interface HoverRevealAction {

    /** Builds the control's content if needed and makes it visible. */
    void reveal();

    /** Hides the control, keeping its layout slot and any already-built content. */
    void conceal();

    /** Whether the control is currently shown. */
    boolean isRevealed();
}
