package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

public class ExternalPlayerPathCard extends PlayerOptionCard {
    private static final double BROWSE_BUTTON_WIDTH = 34;
    private static final double BROWSE_BUTTON_HEIGHT = 30;

    public ExternalPlayerPathCard(String title,
                                  String description,
                                  RadioButton defaultSelector,
                                  TextInputControl pathField,
                                  Button browseButton) {
        super(title, description, defaultSelector, createPathChooserContent(pathField, browseButton));
    }

    private static HBox createPathChooserContent(TextInputControl pathField, Button browseButton) {
        pathField.getStyleClass().add("settings-player-path-field");
        pathField.setMinWidth(0);
        pathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Tooltip browseTooltip = new Tooltip(I18n.tr("autoBrowse"));
        browseTooltip.setShowDelay(Duration.millis(150));

        browseButton.setText("...");
        browseButton.setTooltip(browseTooltip);
        browseButton.setAccessibleText(I18n.tr("autoBrowse"));
        browseButton.getStyleClass().add("settings-player-browse-button");
        browseButton.setDisable(false);
        browseButton.setMinSize(BROWSE_BUTTON_WIDTH, BROWSE_BUTTON_HEIGHT);
        browseButton.setPrefSize(BROWSE_BUTTON_WIDTH, BROWSE_BUTTON_HEIGHT);
        browseButton.setMaxSize(BROWSE_BUTTON_WIDTH, BROWSE_BUTTON_HEIGHT);
        UiRenderQuality.optimizeTextNode(browseButton);

        HBox inputRow = new HBox(6, pathField, browseButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(3));
        inputRow.getStyleClass().add("settings-player-field-row");
        inputRow.setMinWidth(0);
        inputRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(inputRow, Priority.ALWAYS);
        return inputRow;
    }
}
