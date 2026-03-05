package com.uiptv.ui;

import com.uiptv.util.AppLog;
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
import javafx.stage.Stage;

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
    private static boolean listenerRegistered = false;
    private final VBox contentBox = new VBox(5);

    public LogDisplayUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        if (logArea == null) {
            logArea = new TextArea();
        }
        if (!listenerRegistered) {
            AppLog.registerListener(LogDisplayUI::appendToLogArea);
            listenerRegistered = true;
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
        com.uiptv.util.AppLog.addLog(log);
    }

    public static void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
        if (!enabled && !forceLoggingEnabled && logArea != null) {
            Platform.runLater(() -> logArea.clear());
        }
    }

    private static void appendToLogArea(String log) {
        if (!(isLoggingEnabled || forceLoggingEnabled) || logArea == null) {
            return;
        }

        Runnable append = () -> logArea.appendText(log + "\n");
        if (Platform.isFxApplicationThread()) {
            append.run();
        } else {
            Platform.runLater(append);
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
        detachedStage.setTitle("UIPTV Logs");
        detachedStage.setScene(new Scene(popupRoot, 800, 600));
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
