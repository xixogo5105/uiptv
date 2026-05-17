package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.AppLog;
import com.uiptv.util.WebActivityLog;
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

import java.util.function.Consumer;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_HEIGHT_PIXELS;
import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;

public class LogDisplayUI extends VBox {
    private static TextArea logArea;
    private final Button clearLogButton= new Button(I18n.tr("autoClear"));
    private final Button copyLogButton = new Button(I18n.tr("autoCopy"));
    private final Button webLogButton = new Button(I18n.tr("autoWebLogs"));
    private final Button detachButton = new Button(I18n.tr("autoDetach"));
    private final Button attachButton = new Button(I18n.tr("autoAttach"));
    private static boolean isLoggingEnabled = false;
    private static boolean forceLoggingEnabled = false;
    private static boolean detached = false;
    private static Stage detachedStage;
    private static Stage webLogStage;
    private static TextArea webLogArea;
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
        webLogButton.setOnAction(event -> showWebLogWindow());

        renderAttachedView();
        getChildren().addAll(contentBox);
    }

    public static void addLog(String log) {
        com.uiptv.util.AppLog.addInfoLog(LogDisplayUI.class, log);
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
        HBox controlBox = new HBox(10, copyLogButton, clearLogButton, webLogButton, detachButton);
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
        HBox controlBox = new HBox(10, copyLogButton, clearLogButton, webLogButton, popupAttachButton);
        popupRoot.getChildren().addAll(logArea, controlBox);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        setDetachedStage(new Stage());
        detachedStage.setTitle(I18n.tr("autoUiptvLogs"));
        Scene detachedScene = new Scene(popupRoot, 800, 600);
        UiI18n.applySceneOrientation(detachedScene);
        detachedStage.setScene(detachedScene);
        detachedStage.setOnCloseRequest(event -> attachWindow());
        detachedStage.show();
    }

    private static void showWebLogWindow() {
        if (webLogStage != null && webLogStage.isShowing()) {
            refreshWebLogArea();
            webLogStage.toFront();
            return;
        }

        webLogArea = new TextArea();
        webLogArea.setEditable(false);
        webLogArea.setWrapText(true);
        webLogArea.setPromptText(I18n.tr("autoNoWebRequestsLoggedYet"));
        webLogArea.getStyleClass().add("terminal");

        Label location = new Label(I18n.tr("autoWebLogsTemporaryLocation", WebActivityLog.getLogFilePath()));
        location.setWrapText(true);

        Button copyButton = new Button(I18n.tr("autoCopy"));
        Button clearButton = new Button(I18n.tr("autoClear"));
        Button refreshButton = new Button(I18n.tr("autoRefresh"));
        Button closeButton = new Button(I18n.tr("commonClose"));
        copyButton.setOnAction(event -> copyWebLogToClipboard());
        clearButton.setOnAction(event -> {
            WebActivityLog.clear();
            refreshWebLogArea();
        });
        refreshButton.setOnAction(event -> refreshWebLogArea());

        HBox controls = new HBox(10, copyButton, clearButton, refreshButton, closeButton);
        VBox popupRoot = new VBox(8, location, webLogArea, controls);
        popupRoot.setPadding(new Insets(8));
        VBox.setVgrow(webLogArea, Priority.ALWAYS);

        Consumer<String> webLogListener = LogDisplayUI::appendToWebLogArea;
        WebActivityLog.registerListener(webLogListener);
        refreshWebLogArea();

        webLogStage = new Stage();
        webLogStage.setTitle(I18n.tr("autoWebRequestLogs"));
        Scene scene = new Scene(popupRoot, 900, 600);
        UiI18n.applySceneOrientation(scene);
        webLogStage.setScene(scene);
        webLogStage.setOnHidden(event -> {
            WebActivityLog.unregisterListener(webLogListener);
            webLogArea = null;
            webLogStage = null;
        });
        closeButton.setOnAction(event -> webLogStage.close());
        webLogStage.show();
    }

    private static void refreshWebLogArea() {
        if (webLogArea == null) {
            return;
        }
        Runnable refresh = () -> webLogArea.setText(WebActivityLog.readAllText());
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
    }

    private static void appendToWebLogArea(String line) {
        if (webLogArea == null) {
            return;
        }
        Runnable append = () -> webLogArea.appendText(line + "\n");
        if (Platform.isFxApplicationThread()) {
            append.run();
        } else {
            Platform.runLater(append);
        }
    }

    private static void copyWebLogToClipboard() {
        if (webLogArea == null) {
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(webLogArea.getText());
        clipboard.setContent(content);
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
