package com.uiptv.lightweight;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.ButtonBar;
import javafx.stage.Stage;

import java.util.concurrent.CopyOnWriteArrayList;

public class LightweightApp {
    private ConfigurationService configurationService;
    private ConfigurationApplicationService configurationApplicationService;
    private ToggleButton serverToggleButton;
    private ToggleButton logsToggleButton;
    private ListView<String> logListView;
    private final ObservableList<String> logEntries = FXCollections.observableArrayList();
    private boolean logsVisible = false;
    private ConfigurationChangeListener configurationChangeListener;

    public static void launch(String[] args) {
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
        Scene scene = new Scene(root, 600, 500);
        applyTheme(scene, root);
        primaryStage.setScene(scene);
        primaryStage.show();

        Configuration configuration = configurationService.read();
        if (configuration != null && configuration.isAutoRunServerOnStartup()) {
            Platform.runLater(() -> {
                try {
                    configurationApplicationService.ensureServerStarted();
                } catch (Exception e) {
                    AppLog.addErrorLog(LightweightApp.class, "Auto-start server failed: " + e.getMessage());
                }
            });
        }

        configurationChangeListener = _ -> Platform.runLater(() -> {
            if (serverToggleButton != null) {
                serverToggleButton.setSelected(configurationApplicationService.isServerRunning());
            }
            applyTheme(scene, root);
        });
        configurationService.addChangeListener(configurationChangeListener);
        if (serverToggleButton != null) {
            serverToggleButton.setSelected(configurationApplicationService.isServerRunning());
        }

        AppLog.registerListener(this::appendLog);
    }

    private void stop() {
        if (configurationChangeListener != null) {
            configurationService.removeChangeListener(configurationChangeListener);
        }
        AppLog.unregisterListener(this::appendLog);
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        VBox controlPanel = new VBox(10);
        controlPanel.setPadding(new Insets(0, 0, 12, 0));

        serverToggleButton = new ToggleButton("Web Server");
        serverToggleButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(serverToggleButton, Priority.ALWAYS);
        serverToggleButton.setSelected(configurationApplicationService.isServerRunning());
        serverToggleButton.setOnAction(_ -> toggleServer());

        Button revertButton = new Button("Switch to Full Application");
        revertButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(revertButton, Priority.ALWAYS);
        revertButton.setOnAction(_ -> revertToFullMode());

        logsToggleButton = new ToggleButton("Logs");
        logsToggleButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(logsToggleButton, Priority.ALWAYS);
        logsToggleButton.setOnAction(_ -> toggleLogs());

        HBox buttonRow1 = new HBox(10, serverToggleButton, revertButton);
        buttonRow1.setAlignment(Pos.CENTER_LEFT);
        buttonRow1.setMaxWidth(Double.MAX_VALUE);

        HBox buttonRow2 = new HBox(10, logsToggleButton);
        buttonRow2.setAlignment(Pos.CENTER_LEFT);
        buttonRow2.setMaxWidth(Double.MAX_VALUE);

        controlPanel.getChildren().addAll(buttonRow1, buttonRow2);
        root.setTop(controlPanel);

        logListView = new ListView<>();
        logListView.setItems(logEntries);
        logListView.setStyle("-fx-font-family: 'Courier New', 'Monaco', 'Consolas', monospace; -fx-font-size: 12;");
        logListView.setCellFactory(_ -> new LogCell());
        logListView.setVisible(logsVisible);
        logListView.setManaged(logsVisible);

        VBox logContainer = new VBox(10, new Label("Terminal"), logListView);
        logContainer.setVgrow(logListView, Priority.ALWAYS);
        VBox.setVgrow(logListView, Priority.ALWAYS);
        root.setCenter(logContainer);

        return root;
    }

    private void toggleServer() {
        try {
            if (serverToggleButton.isSelected()) {
                configurationApplicationService.startServer();
            } else {
                configurationApplicationService.stopServer();
            }
        } catch (Exception e) {
            AppLog.addErrorLog(LightweightApp.class, "Server toggle failed: " + e.getMessage());
            Platform.runLater(() -> serverToggleButton.setSelected(!serverToggleButton.isSelected()));
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

    private void toggleLogs() {
        logsVisible = logsToggleButton.isSelected();
        logListView.setVisible(logsVisible);
        logListView.setManaged(logsVisible);
        if (logsVisible) {
            scrollToBottom();
        }
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

    private void applyTheme(Scene scene, BorderPane root) {
        Configuration configuration = configurationService.read();
        boolean dark = configuration != null && configuration.isDarkTheme();
        String bg = dark ? "#1e1e1e" : "#ffffff";
        String fg = dark ? "#d4d4d4" : "#1e1e1e";
        root.setStyle("-fx-background-color: " + bg + ";");
        if (scene != null) {
            scene.getRoot().setStyle("-fx-background-color: " + bg + ";");
        }
        if (logListView != null) {
            logListView.setStyle("-fx-font-family: 'Courier New', 'Monaco', 'Consolas', monospace; -fx-font-size: 12; -fx-control-inner-background: " + bg + "; -fx-text-fill: " + fg + ";");
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
