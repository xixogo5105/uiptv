package com.uiptv.ui;

import com.uiptv.util.I18n;

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
    private final Button clearLogButton= new Button(I18n.tr("autoClear"));
    private final Button copyLogButton = new Button(I18n.tr("autoCopy"));
    private final Button detachButton = new Button(I18n.tr("autoDetach"));
    private final Button attachButton = new Button(I18n.tr("autoAttach"));
    private static boolean isLoggingEnabled = false;
    private static boolean forceLoggingEnabled = false;
    private static boolean detached = false;
    private static Stage detachedStage;
    private final VBox contentBox = new VBox(5);

    static {
        AppLog.registerListener(LogDisplayUI::appendToLogArea);
    }

    public LogDisplayUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        ensureLogAreaInitialized();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        logArea.setPrefHeight(GUIDED_MAX_HEIGHT_PIXELS);
        logArea.getStyleClass().add("terminal");

        clearLogButton.setOnAction(event -> logArea.clear());

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

    private static void ensureLogAreaInitialized() {
        if (logArea == null) {
            logArea = new TextArea();
        }
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
        setDetached(false);
    }

    private void renderDetachedPlaceholder() {
        contentBox.getChildren().clear();
        Label info = new Label(I18n.tr("autoLogsDetachedInASeparateWindow"));
        VBox placeholder = new VBox(10, info, attachButton);
        contentBox.getChildren().add(placeholder);
    }

    private void detachWindow() {
        if (detached) {
            return;
        }
        setDetached(true);
        setForceLoggingEnabled(true);
        renderDetachedPlaceholder();

        VBox popupRoot = new VBox(5);
        popupRoot.setPadding(new Insets(8));
        Button popupAttachButton = new Button(I18n.tr("autoAttach"));
        popupAttachButton.setOnAction(event -> attachWindow());
        HBox controlBox = new HBox(10, copyLogButton, clearLogButton, popupAttachButton);
        popupRoot.getChildren().addAll(logArea, controlBox);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        setDetachedStage(new Stage());
        detachedStage.setTitle(I18n.tr("autoUiptvLogs"));
        Scene detachedScene = new Scene(popupRoot, 800, 600);
        I18n.applySceneOrientation(detachedScene);
        detachedStage.setScene(detachedScene);
        detachedStage.setOnCloseRequest(event -> attachWindow());
        detachedStage.show();
    }

    private void attachWindow() {
        if (!detached) {
            return;
        }
        setDetached(false);
        setForceLoggingEnabled(false);
        if (detachedStage != null) {
            detachedStage.close();
            setDetachedStage(null);
        }
        renderAttachedView();
    }

    private static void setDetached(boolean isDetached) {
        detached = isDetached;
    }

    private static void setForceLoggingEnabled(boolean enabled) {
        forceLoggingEnabled = enabled;
    }

    private static void setDetachedStage(Stage stage) {
        detachedStage = stage;
    }
}
