package com.uiptv.ui;

import com.uiptv.widget.PopupDecorator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_HEIGHT_PIXELS;
import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;

public class LogDisplayUI extends VBox {
    private static TextArea logArea;
    private final Button clearLogButton= new Button("Clear");
    private final Button copyLogButton = new Button("Copy");
    private final Button detachButton = new Button("Detach");
    private final Button attachButton = new Button("Attach");
    private static boolean isLoggingEnabled = false;
    private static boolean forceLoggingEnabled = false;
    private static boolean detached = false;
    private static Stage detachedStage;
    private final VBox contentBox = new VBox(5);

    public LogDisplayUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        if (logArea == null) {
            logArea = new TextArea();
        }
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        logArea.setPrefHeight(GUIDED_MAX_HEIGHT_PIXELS);
        logArea.getStyleClass().add("terminal");

        clearLogButton.setOnAction(event -> {
            logArea.clear();
        });

        copyLogButton.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(logArea.getText());
            clipboard.setContent(content);
        });

        detachButton.setOnAction(event -> detachWindow());
        attachButton.setOnAction(event -> attachWindow());

        renderAttachedView();
        getChildren().addAll(contentBox);
    }

    public static void addLog(String log) {
        System.out.println(log);
        if (isLoggingEnabled || forceLoggingEnabled) {
            Platform.runLater(() -> logArea.appendText(log + "\n"));
        }
    }

    public static void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
        if (!enabled && !forceLoggingEnabled && logArea != null) {
            Platform.runLater(() -> logArea.clear());
        }
    }

    private void renderAttachedView() {
        contentBox.getChildren().clear();
        HBox controlBox = new HBox(10, copyLogButton, clearLogButton, detachButton);
        VBox vbox = new VBox(5, logArea, controlBox);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        contentBox.getChildren().add(vbox);
        detached = false;
    }

    private void renderDetachedPlaceholder() {
        contentBox.getChildren().clear();
        Label info = new Label("Logs detached in a separate window.");
        VBox placeholder = new VBox(10, info, attachButton);
        contentBox.getChildren().add(placeholder);
    }

    private void detachWindow() {
        if (detached) {
            return;
        }
        detached = true;
        forceLoggingEnabled = true;
        renderDetachedPlaceholder();

        VBox popupRoot = new VBox(5);
        popupRoot.setPadding(new Insets(8));
        Button popupAttachButton = new Button("Attach");
        popupAttachButton.setOnAction(event -> attachWindow());
        HBox controlBox = new HBox(10, copyLogButton, clearLogButton, popupAttachButton);
        popupRoot.getChildren().addAll(logArea, controlBox);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        detachedStage = new Stage();
        detachedStage.initStyle(StageStyle.TRANSPARENT);
        VBox decoratedRoot = PopupDecorator.wrap(detachedStage, "UIPTV Logs", popupRoot);
        Scene scene = new Scene(decoratedRoot, 800, 600);
        scene.setFill(Color.TRANSPARENT);
        if (RootApplication.currentTheme != null) {
            scene.getStylesheets().add(RootApplication.currentTheme);
        }
        detachedStage.setScene(scene);
        detachedStage.setOnCloseRequest(event -> attachWindow());
        detachedStage.show();
    }

    private void attachWindow() {
        if (!detached) {
            return;
        }
        detached = false;
        forceLoggingEnabled = false;
        if (detachedStage != null) {
            detachedStage.close();
            detachedStage = null;
        }
        renderAttachedView();
    }
}
