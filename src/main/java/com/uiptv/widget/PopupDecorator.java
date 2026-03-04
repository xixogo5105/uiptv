package com.uiptv.widget;

import com.uiptv.util.StyleClassDecorator;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class PopupDecorator {
    private PopupDecorator() {
    }

    public static VBox wrap(Stage stage, String title, Node content) {
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("custom-popup-title");

        Button closeButton = new Button("x");
        closeButton.getStyleClass().add("custom-popup-close");
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, titleLabel, spacer, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("custom-popup-header");

        VBox root = new VBox(header, content);
        root.getStyleClass().add("custom-popup-root");
        if (content instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(region, Priority.ALWAYS);
        }

        installDragSupport(stage, header);
        StyleClassDecorator.decorate(root);
        return root;
    }

    private static void installDragSupport(Stage stage, HBox dragHandle) {
        final double[] offset = new double[2];
        dragHandle.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            offset[0] = event.getSceneX();
            offset[1] = event.getSceneY();
        });
        dragHandle.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            stage.setX(event.getScreenX() - offset[0]);
            stage.setY(event.getScreenY() - offset[1]);
        });
    }
}
