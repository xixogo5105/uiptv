package com.uiptv.ui;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import com.uiptv.util.WebActivityLog;
import com.uiptv.widget.AppHeaderActions;
import com.uiptv.widget.AppPageHeader;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
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
            configureLogActionButton(button);
        }
        webLogButton.getStyleClass().add("log-primary-action-button");
        detachButton.getStyleClass().add("log-primary-action-button");
        attachButton.getStyleClass().add("log-primary-action-button");

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
        detachFromParent(logArea);
        FlowPane controlBox = createControlRow(copyLogButton, clearLogButton, webLogButton, detachButton);
        AppPageHeader header = createHeader();
        Label title = new Label(I18n.tr("autoUiptvLogs"));
        VBox cardHeader = createLogCardHeader(title, controlBox);

        VBox logCard = new VBox(12, cardHeader, logArea);
        logCard.getStyleClass().add("log-card");
        logCard.setFillWidth(true);
        logCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        logCard.setMinHeight(0);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        contentBox.getChildren().setAll(header, logCard);
        VBox.setVgrow(logCard, Priority.ALWAYS);
        setDetached(false);
    }

    private void renderDetachedPlaceholder() {
        contentBox.getChildren().clear();
        Label info = new Label(I18n.tr("autoLogsDetachedInASeparateWindow"));
        info.getStyleClass().add("log-detached-title");
        info.setWrapText(true);
        VBox placeholder = new VBox(12, info, createControlRow(attachButton));
        placeholder.getStyleClass().add("log-detached-card");
        placeholder.setMaxWidth(Double.MAX_VALUE);
        placeholder.setAlignment(Pos.CENTER_LEFT);
        AppPageHeader header = createHeader();
        contentBox.getChildren().setAll(header, placeholder);
    }

    private AppPageHeader createHeader() {
        AppPageHeader header = new AppPageHeader(
                I18n.tr("autoLogs"),
                new AppHeaderActions(hostServices, themeToggleHandler, null)
        );
        header.setHeaderTitleVisible(false);
        header.setNavigationSelectionEnabled(false);
        return header;
    }

    private static VBox createLogCardHeader(Label title, FlowPane controlBox) {
        title.getStyleClass().add("log-card-title");
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        VBox header = new VBox(8, title, controlBox);
        header.getStyleClass().add("log-card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setFillWidth(true);
        header.setMinWidth(0);
        header.setMaxWidth(Double.MAX_VALUE);
        return header;
    }

    private static FlowPane createControlRow(Button... buttons) {
        for (Button button : buttons) {
            detachFromParent(button);
        }
        FlowPane row = new FlowPane(8, 8);
        row.getStyleClass().add("log-control-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        for (Button button : buttons) {
            row.getChildren().add(button);
        }
        return row;
    }

    private static void configureLogActionButton(Button button) {
        button.getStyleClass().add("log-action-button");
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        button.setWrapText(false);
        button.setTooltip(new Tooltip(button.getText()));
    }

    private void detachWindow() {
        if (detached) {
            return;
        }
        setDetached(true);
        setForceLoggingEnabled(true);
        renderDetachedPlaceholder();
        detachFromParent(logArea);

        VBox popupRoot = new VBox(14);
        popupRoot.getStyleClass().addAll("log-page", "log-window-root");
        popupRoot.setPadding(new Insets(16));
        Button popupAttachButton = new Button(I18n.tr("autoAttach"));
        configureLogActionButton(popupAttachButton);
        popupAttachButton.getStyleClass().add("log-primary-action-button");
        popupAttachButton.setOnAction(event -> attachWindow());
        FlowPane controlBox = createControlRow(copyLogButton, clearLogButton, webLogButton, popupAttachButton);
        Label title = new Label(I18n.tr("autoUiptvLogs"));
        VBox cardHeader = createLogCardHeader(title, controlBox);
        VBox logCard = new VBox(12, cardHeader, logArea);
        logCard.getStyleClass().add("log-card");
        logCard.setFillWidth(true);
        logCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        popupRoot.getChildren().add(logCard);
        VBox.setVgrow(logCard, Priority.ALWAYS);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        setDetachedStage(new Stage());
        detachedStage.setTitle(I18n.tr("autoUiptvLogs"));
        Scene detachedScene = new Scene(popupRoot, 800, 600);
        applyCurrentTheme(detachedScene);
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
        webLogArea.getStyleClass().add("log-text-area");

        Label location = new Label(I18n.tr("autoWebLogsTemporaryLocation", WebActivityLog.getLogFilePath()));
        location.setWrapText(true);
        location.getStyleClass().add("log-web-location");

        Button copyButton = new Button(I18n.tr("autoCopy"));
        Button clearButton = new Button(I18n.tr("autoClear"));
        Button refreshButton = new Button(I18n.tr("autoRefresh"));
        Button closeButton = new Button(I18n.tr("commonClose"));
        for (Button button : List.of(copyButton, clearButton, refreshButton, closeButton)) {
            configureLogActionButton(button);
        }
        refreshButton.getStyleClass().add("log-primary-action-button");
        copyButton.setOnAction(event -> copyWebLogToClipboard());
        clearButton.setOnAction(event -> {
            WebActivityLog.clear();
            refreshWebLogArea();
        });
        refreshButton.setOnAction(event -> refreshWebLogArea());

        FlowPane controls = createControlRow(copyButton, clearButton, refreshButton, closeButton);
        VBox card = new VBox(12, location, webLogArea, controls);
        card.getStyleClass().add("log-card");
        card.setFillWidth(true);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox popupRoot = new VBox(card);
        popupRoot.getStyleClass().addAll("log-page", "log-window-root");
        popupRoot.setPadding(new Insets(16));
        VBox.setVgrow(card, Priority.ALWAYS);
        VBox.setVgrow(webLogArea, Priority.ALWAYS);

        Consumer<String> webLogListener = LogDisplayUI::appendToWebLogArea;
        WebActivityLog.registerListener(webLogListener);
        refreshWebLogArea();

        webLogStage = new Stage();
        webLogStage.setTitle(I18n.tr("autoWebRequestLogs"));
        Scene scene = new Scene(popupRoot, 900, 600);
        applyCurrentTheme(scene);
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

    private static void detachFromParent(Node node) {
        if (node != null && node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private static void applyCurrentTheme(Scene scene) {
        try {
            ConfigurationService service = ConfigurationService.getInstance();
            Configuration configuration = service.read();
            RootApplication.applyTheme(
                    scene,
                    RootApplication.class,
                    configuration != null && configuration.isDarkTheme(),
                    service.getUiZoomPercent()
            );
            return;
        } catch (RuntimeException _) {
            // Detached log windows are also used by tests without an initialized database.
        }
        String currentTheme = RootApplication.getCurrentTheme();
        if (currentTheme != null && !currentTheme.isBlank()) {
            scene.getStylesheets().setAll(currentTheme);
        }
        UiI18n.applySceneOrientation(scene);
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
