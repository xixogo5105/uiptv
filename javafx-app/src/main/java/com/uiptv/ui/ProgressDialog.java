package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import com.uiptv.util.I18n;

import com.uiptv.widget.SegmentedProgressBar;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProgressDialog extends Stage {
    private static final String LOG_TEXT_STYLE_CLASS = "log-text";

    private final SegmentedProgressBar progressBar = new SegmentedProgressBar();
    private final VBox messageContainer = new VBox();
    private final ScrollPane scrollPane = new ScrollPane(messageContainer);
    private final Button cancelButton = new Button(I18n.tr("autoCancel"));
    private final Button stopButton = new Button(I18n.tr("autoStop"));
    private final ComboBox<String> delayDropdown = new ComboBox<>();
    
    // Pause Widget Components
    private final HBox pauseWidget = new HBox(10);
    private final Line clockHand = new Line(0, 0, 0, -10);
    private final Label pauseLabel = new Label();

    public ProgressDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.tr("autoVerifyingMacAddresses"));

        // Root Layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        
        // Progress Bar (Top)
        BorderPane.setMargin(progressBar, new Insets(0, 0, 10, 0));
        
        // Message Area (Center)
        scrollPane.getStyleClass().add("log-scroll-pane");
        scrollPane.setFitToWidth(true);
        messageContainer.getStyleClass().add("log-message-container");
        messageContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));
        
        // Bottom Bar (Bottom)
        cancelButton.getStyleClass().add("dialog-button");
        stopButton.getStyleClass().add("dialog-button");

        // Delay Dropdown
        delayDropdown.setItems(FXCollections.observableArrayList("1 sec", "5 secs", "10 secs", "30 secs", "1 min", "10 mins", "30 mins"));
        delayDropdown.setValue("10 secs");

        // Pause Widget
        Circle clockFace = new Circle(12);
        clockFace.getStyleClass().add("clock-face");
        clockHand.getStyleClass().add("clock-hand");
        StackPane clockIcon = new StackPane(clockFace, clockHand);
        pauseWidget.getChildren().addAll(clockIcon, pauseLabel);
        pauseWidget.setAlignment(Pos.CENTER_LEFT);
        pauseWidget.setVisible(false);

        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBar.getChildren().addAll(pauseWidget, new Label(I18n.tr("autoDelay")), delayDropdown, stopButton, spacer, cancelButton);

        // Assemble Root
        root.setTop(progressBar);
        root.setCenter(scrollPane);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 600, 450); // Increased width for dropdown
        UiI18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        setScene(scene);
        
        progressBar.prefWidthProperty().bind(scene.widthProperty().subtract(45));
    }

    public long getSelectedDelayMillis() {
        String selected = delayDropdown.getValue();
        if (selected == null) return 10000; // Default 10s
        
        String[] parts = selected.split(" ");
        int value = Integer.parseInt(parts[0]);
        
        switch (parts[1]) {
            case "sec":
            case "secs":
                return value * 1000L;
            case "min":
            case "mins":
                return value * 60 * 1000L;
            default:
                return 10000L;
        }
    }

    public void setTotal(int total) {
        progressBar.setTotal(total);
    }

    public void addResult(boolean isValid) {
        progressBar.addResult(isValid);
    }

    public void addProgressText(String text) {
        Platform.runLater(() -> {
            if (text.startsWith("[UPDATE_LAST]")) {
                updateLastLine(text.substring(13));
            } else {
                messageContainer.getChildren().add(createStyledText(text));
            }
        });
    }
    
    public void setPauseStatus(int secondsRemaining, int totalSeconds) {
        Platform.runLater(() -> {
            if (secondsRemaining > 0) {
                pauseWidget.setVisible(true);
                pauseLabel.setText(I18n.tr("autoPausedSeconds", secondsRemaining));
                double rotation = ((double)(totalSeconds - secondsRemaining) / totalSeconds) * 360;
                clockHand.setRotate(rotation);
            } else {
                pauseWidget.setVisible(false);
            }
        });
    }

    private void updateLastLine(String text) {
        if (!messageContainer.getChildren().isEmpty()) {
            int lastIndex = messageContainer.getChildren().size() - 1;
            Node lastNode = messageContainer.getChildren().get(lastIndex);
            if (lastNode instanceof TextFlow textFlow) {
                textFlow.getChildren().setAll(new Text(text));
            }
        } else {
            messageContainer.getChildren().add(createStyledText(text));
        }
    }

    private TextFlow createStyledText(String text) {
        TextFlow textFlow = new TextFlow();
        List<Text> texts = new ArrayList<>();

        if (text.contains("[VALID]")) {
            String[] parts = text.split("\\[VALID\\]", 2);
            Text part1 = new Text(parts[0]);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
            Text validText = new Text(parts[1]);
            validText.getStyleClass().add("valid-text");
            texts.add(validText);
        } else if (text.contains("[INVALID]")) {
            String[] parts = text.split("\\[INVALID\\]", 2);
            Text part1 = new Text(parts[0]);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
            Text invalidText = new Text(parts[1]);
            invalidText.getStyleClass().add("invalid-text");
            texts.add(invalidText);
        } else {
            Text part1 = new Text(text);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
        }

        textFlow.getChildren().addAll(texts);
        return textFlow;
    }

    public void setOnClose(Runnable action) {
        cancelButton.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to cancel the verification process? No changes will be saved.", ButtonType.YES, ButtonType.NO);
            alert.initOwner(this);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    action.run();
                    close();
                }
            });
        });

        setOnCloseRequest(event -> {
            event.consume();
            cancelButton.fire();
        });
    }

    public void setOnStop(Runnable action) {
        stopButton.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to stop? Invalid MACs found so far will be processed.", ButtonType.YES, ButtonType.NO);
            alert.initOwner(this);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    action.run();
                }
            });
        });
    }

    public void markCompleted() {
        stopButton.setDisable(true);
        cancelButton.setText(I18n.tr("commonClose"));
        cancelButton.setOnAction(event -> close());
        setOnCloseRequest(event -> close());
    }
}
