package com.uiptv.lightweight;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import com.uiptv.util.ServerUrlUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LightweightApp {
    private ConfigurationService configurationService;
    private ConfigurationApplicationService configurationApplicationService;
    private ToggleButton logsButton;
    private Label serverStatusLabel;
    private Label httpPortLabel;
    private Label httpsPortLabel;
    private ListView<String> logListView;
    private Label terminalLabel;
    private Button serverToggleButton;
    private Button clearButton;
    private final ObservableList<String> logEntries = FXCollections.observableArrayList();
    private boolean logsVisible = false;

    public static void launch() {
        Platform.startup(() -> {
            LightweightApp app = new LightweightApp();
            Stage primaryStage = new Stage();
            app.start(primaryStage);
        });
    }

    private void start(Stage primaryStage) {
        configurationService = ConfigurationService.getInstance();
        configurationApplicationService = ConfigurationApplicationService.getInstance();

primaryStage.setTitle("UIPTV - Lightweight Mode");
        BorderPane root = buildRoot();
        Scene scene = new Scene(root, 620, 540);
        scene.getStylesheets().add(getClass().getResource("/lightweight-ui.css").toExternalForm());
        applyTheme(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        Configuration configuration = configurationService.read();
        if (configuration != null && configuration.isAutoRunServerOnStartup()) {
            Platform.runLater(() -> {
                try {
                    configurationApplicationService.ensureServerStarted();
                    refreshServerStatus();
                    updateClearButtonVisibility();
                } catch (Exception e) {
                    AppLog.addErrorLog(LightweightApp.class, "Auto-start server failed: " + e.getMessage());
                }
            });
        }

        ConfigurationChangeListener configurationChangeListener = _ -> Platform.runLater(() -> {
            refreshServerStatus();
            applyTheme(root);
            updateClearButtonVisibility();
        });
        configurationService.addChangeListener(configurationChangeListener);

        applyTheme(root);

        AppLog.registerListener(this::appendLog);

        // Start server status monitor
        startServerStatusMonitor();
    }

    private void startServerStatusMonitor() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), _ -> refreshServerStatus()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        VBox sections = new VBox(12);
        sections.setMaxWidth(Double.MAX_VALUE);

        VBox section1Content = buildSection1Content();
        VBox section2Content = buildSection2Content();
        BorderPane section1Pane = createCollapsibleSection("configLightweightMode", section1Content);
        BorderPane section2Pane = createCollapsibleSection("configWebServer", section2Content);

        sections.getChildren().addAll(section1Pane, section2Pane);
        root.setTop(sections);

        terminalLabel = new Label("Terminal");
        terminalLabel.getStyleClass().add("terminal-label");
        terminalLabel.setVisible(logsVisible);
        terminalLabel.setManaged(logsVisible);

        logListView = new ListView<>();
        logListView.setItems(logEntries);
        logListView.getStyleClass().add("terminal-log-list");
        logListView.setCellFactory(_ -> new LogCell());
        logListView.setVisible(logsVisible);
        logListView.setManaged(logsVisible);

        VBox logContainer = new VBox(6, terminalLabel, logListView);
        VBox.setVgrow(logListView, Priority.ALWAYS);
        VBox.setVgrow(logListView, Priority.ALWAYS);
        root.setCenter(logContainer);

        refreshServerStatus();

        return root;
    }

    private VBox buildSection1Content() {
        VBox section1Content = new VBox(12);

        // Native button for "Switch to Full Application" - no CSS styling, fixed width
        Button fullAppButton = new Button(I18n.tr("configLightweightModeRevertTitle"));
        fullAppButton.setOnAction(_ -> revertToFullMode());

        // Native toggle button for "Logs" - use i18n key "autoLogs"
        logsButton = new ToggleButton(I18n.tr("autoLogs"));
        logsButton.setOnAction(_ -> setLogsVisible(logsButton.isSelected()));

        // Clear button - positioned on right side of logs button
        clearButton = new Button(I18n.tr("configClearCache"));
        clearButton.setVisible(false);
        clearButton.setManaged(false);
        clearButton.setOnAction(_ -> {
            logEntries.clear();
            AppLog.addInfoLog(LightweightApp.class, "Logs cleared");
        });

        HBox buttonRow = new HBox(12, fullAppButton, logsButton, clearButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.setMaxWidth(Double.MAX_VALUE);

        section1Content.getChildren().add(buttonRow);
        return section1Content;
    }

    private VBox buildSection2Content() {
        VBox section2Content = new VBox(8);

        serverStatusLabel = new Label();
        serverStatusLabel.getStyleClass().add("server-status-label");
        httpPortLabel = new Label();
        httpPortLabel.getStyleClass().add("server-port-label");
        httpsPortLabel = new Label();
        httpsPortLabel.getStyleClass().add("server-port-label");

        HBox statusRow = new HBox(12, serverStatusLabel, httpPortLabel, httpsPortLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setMaxWidth(Double.MAX_VALUE);

        // Server toggle button at bottom - left-aligned, minimal width, red colour coding when stopping
        serverToggleButton = new Button(I18n.tr("configStartServer"));
        serverToggleButton.getStyleClass().addAll("pill-toggle", "pill-toggle-unselected");
        serverToggleButton.setOnAction(_ -> toggleServerAction());

        section2Content.getChildren().addAll(statusRow, serverToggleButton);
        return section2Content;
    }

    private BorderPane createCollapsibleSection(String titleKey, VBox content) {
        BorderPane pane = new BorderPane(content);
        pane.getStyleClass().add("settings-section-card");

        Label titleLabel = new Label(I18n.tr(titleKey));
        titleLabel.getStyleClass().add("settings-section-title");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setWrapText(true);

        Hyperlink toggleLink = new Hyperlink();
        toggleLink.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, titleLabel, spacer, toggleLink);
        header.setAlignment(Pos.CENTER_LEFT);

        final Runnable refreshToggleLabel = () -> {
            boolean expanded = content.isVisible() && content.isManaged();
            toggleLink.setText(expanded ? I18n.tr("commonHide") : I18n.tr("commonShow"));
        };

        content.setVisible(true);
        content.setManaged(true);
        refreshToggleLabel.run();

        toggleLink.setOnAction(event -> {
            boolean expand = !(content.isVisible() && content.isManaged());
            content.setVisible(expand);
            content.setManaged(expand);
            refreshToggleLabel.run();
        });

        BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
        pane.setTop(header);

        return pane;
    }

    private void toggleServerAction() {
        try {
            boolean running = configurationApplicationService.isServerRunning();
            if (running) {
                configurationApplicationService.stopServer();
            } else {
                configurationApplicationService.startServer();
            }
            refreshServerStatus();
        } catch (Exception e) {
            AppLog.addErrorLog(LightweightApp.class, "Server toggle failed: " + e.getMessage());
            Platform.runLater(this::refreshServerStatus);
        }
    }

    private void refreshServerStatus() {
        boolean running = configurationApplicationService.isServerRunning();
        if (serverStatusLabel != null) {
            if (running) {
                serverStatusLabel.setText("Webserver Running");
                serverStatusLabel.getStyleClass().removeAll("status-stopped");
                serverStatusLabel.getStyleClass().add("status-running");
            } else {
                serverStatusLabel.setText("Webserver Stopped");
                serverStatusLabel.getStyleClass().removeAll("status-running");
                serverStatusLabel.getStyleClass().add("status-stopped");
            }
        }
        if (httpPortLabel != null) {
            httpPortLabel.setText("HTTP: " + ServerUrlUtil.getConfiguredServerPort());
        }
        if (httpsPortLabel != null) {
            if (ServerUrlUtil.isHttpsServerEnabled()) {
                httpsPortLabel.setText("HTTPS: " + ServerUrlUtil.getConfiguredHttpsServerPort());
                httpsPortLabel.setVisible(true);
                httpsPortLabel.setManaged(true);
            } else {
                httpsPortLabel.setVisible(false);
                httpsPortLabel.setManaged(false);
            }
        }
        if (serverToggleButton != null) {
            String buttonText = running ? I18n.tr("configStopServer") : I18n.tr("configStartServer");
            serverToggleButton.setText(buttonText);
            serverToggleButton.getStyleClass().remove("pill-toggle-dangerous");
            if (running) {
                serverToggleButton.getStyleClass().add("pill-toggle-dangerous");
            }
        }
    }

    private void setLogsVisible(boolean visible) {
        logsVisible = visible;
        logsButton.setSelected(visible);
        // Show/hide terminal component
        terminalLabel.setVisible(visible);
        terminalLabel.setManaged(visible);
        logListView.setVisible(visible);
        logListView.setManaged(visible);
        // Show/hide clear button
        clearButton.setVisible(visible);
        clearButton.setManaged(visible);
        if (visible) {
            scrollToBottom();
        }
    }

    private void revertToFullMode() {
        if (!showConfirmation("configLightweightModeRevertConfirm")) {
            return;
        }
        Configuration configuration = configurationService.read();
        if (configuration != null) {
            configuration.setLightweightModeEnabled(false);
            configurationService.save(configuration);
        }
        Platform.exit();
        System.exit(0);
    }

    private void appendLog(String message) {
        logEntries.add(message);
        if (logsVisible) {
            Platform.runLater(this::scrollToBottom);
        }
    }

    private void scrollToBottom() {
        if (logListView != null && !logEntries.isEmpty()) {
            logListView.scrollTo(logEntries.size() - 1);
        }
    }

    private void updateClearButtonVisibility() {
        boolean visible = logsVisible || !logEntries.isEmpty();
        clearButton.setVisible(visible);
        clearButton.setManaged(visible);
    }

    private void applyTheme(BorderPane root) {
        Configuration configuration = configurationService.read();
        boolean dark = configuration != null && configuration.isDarkTheme();
        if (dark) {
            root.getStyleClass().add("dark-theme");
        } else {
            root.getStyleClass().remove("dark-theme");
        }
    }

    private boolean showConfirmation(String i18nKey) {
        String message = I18n.tr(i18nKey);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, new ButtonType(I18n.tr("commonOk"), ButtonBar.ButtonData.OK_DONE), new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE));
        alert.setTitle(I18n.tr("commonConfirm"));
        alert.setHeaderText(I18n.tr("commonConfirm"));
        java.util.Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }

    private static class LogCell extends javafx.scene.control.ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item);
                setStyle("-fx-font-family: 'Courier New', 'Monaco', 'Consolas', monospace; -fx-font-size: 12;");
            }
        }
    }
}