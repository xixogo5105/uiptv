package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.AppLog;
import com.uiptv.util.WebActivityLog;
import com.uiptv.widget.AppHeaderActions;
import com.uiptv.widget.AppPageHeader;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

import java.util.List;
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
    private final HostServices hostServices;
    private final Runnable themeToggleHandler;

    static {
        AppLog.registerListener(LogDisplayUI::appendToLogArea);
    }

    public LogDisplayUI() {
        this(null, null);
    }

    public LogDisplayUI(HostServices hostServices, Runnable themeToggleHandler) {
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        setPadding(Insets.EMPTY);
        setSpacing(0);
        getStyleClass().add("log-page");
        contentBox.getStyleClass().add("log-content");
        contentBox.setPadding(new Insets(24, 10, 20, 10));
        contentBox.setSpacing(14);
        contentBox.setFillWidth(true);
        contentBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        ensureLogAreaInitialized();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        logArea.setPrefHeight(GUIDED_MAX_HEIGHT_PIXELS);
        logArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        logArea.getStyleClass().add("terminal");
        logArea.getStyleClass().add("log-text-area");

        for (Button button : List.of(copyLogButton, clearLogButton, webLogButton, detachButton, attachButton)) {
            button.getStyleClass().add("log-action-button");
        }
        webLogButton.getStyleClass().add("log-primary-action-button");
        detachButton.getStyleClass().add("log-primary-action-button");

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
        getChildren().setAll(contentBox);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
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
        HBox controlBox = createControlRow(copyLogButton, clearLogButton, webLogButton, detachButton);
        AppPageHeader header = createHeader();
        VBox logCard = new VBox(logArea);
        logCard.getStyleClass().add("log-card");
        logCard.setFillWidth(true);
        logCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        logCard.setMinHeight(0);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        contentBox.getChildren().setAll(header, controlBox, logCard);
        VBox.setVgrow(logCard, Priority.ALWAYS);
        setDetached(false);
    }

    private void renderDetachedPlaceholder() {
        contentBox.getChildren().clear();
        Label info = new Label(I18n.tr("autoLogsDetachedInASeparateWindow"));
        info.getStyleClass().add("log-detached-title");
        info.setWrapText(true);
        VBox placeholder = new VBox(10, info);
        placeholder.getStyleClass().add("log-detached-card");
        placeholder.setMaxWidth(Double.MAX_VALUE);
        AppPageHeader header = createHeader();
        contentBox.getChildren().setAll(header, createControlRow(attachButton), placeholder);
    }

    private AppPageHeader createHeader() {
        return new AppPageHeader(
                I18n.tr("autoLogs"),
                new AppHeaderActions(hostServices, themeToggleHandler, null)
        );
    }

    private HBox createControlRow(Button... buttons) {
        HBox row = new HBox(8, buttons);
        row.getStyleClass().add("log-control-row");
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setFillHeight(false);
        return row;
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
        for (Button button : List.of(copyButton, clearButton, refreshButton, closeButton)) {
            button.getStyleClass().add("log-action-button");
        }
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
