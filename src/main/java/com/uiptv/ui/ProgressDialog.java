package com.uiptv.ui;

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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProgressDialog extends Stage {

    private final HBox progressBarContainer = new HBox(0);
    private final VBox messageContainer = new VBox();
    private final ScrollPane scrollPane = new ScrollPane(messageContainer);
    private final Button cancelButton = new Button("Cancel");
    private final Button stopButton = new Button("Stop");
    private final ComboBox<String> delayDropdown = new ComboBox<>();
    
    // Pause Widget Components
    private final HBox pauseWidget = new HBox(10);
    private final Line clockHand = new Line(0, 0, 0, -10);
    private final Label pauseLabel = new Label();

    private int totalItems = 0;
    private int currentIndex = 0;

    public ProgressDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Verifying MAC Addresses...");

        // Root Layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        
        // Progress Bar (Top)
        progressBarContainer.setMinHeight(14);
        progressBarContainer.setPrefHeight(14);
        progressBarContainer.setMaxHeight(14);
        progressBarContainer.setAlignment(Pos.CENTER_LEFT);
        progressBarContainer.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 2; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;");
        BorderPane.setMargin(progressBarContainer, new Insets(0, 0, 10, 0));
        
        // Message Area (Center)
        scrollPane.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;");
        scrollPane.setFitToWidth(true);
        messageContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));
        
        // Bottom Bar (Bottom)
        String buttonStyle = "-fx-font-size: 14px; -fx-padding: 5 15 5 15;";
        cancelButton.setStyle(buttonStyle);
        stopButton.setStyle(buttonStyle);

        // Delay Dropdown
        delayDropdown.setItems(FXCollections.observableArrayList("1 sec", "5 secs", "10 secs", "30 secs", "1 min", "10 mins", "30 mins"));
        delayDropdown.setValue("10 secs");

        // Pause Widget
        Circle clockFace = new Circle(12);
        clockFace.setFill(Color.TRANSPARENT);
        clockFace.setStroke(Color.GRAY);
        clockFace.setStrokeWidth(2);
        clockHand.setStroke(Color.RED);
        clockHand.setStrokeWidth(2);
        StackPane clockIcon = new StackPane(clockFace, clockHand);
        pauseWidget.getChildren().addAll(clockIcon, pauseLabel);
        pauseWidget.setAlignment(Pos.CENTER_LEFT);
        pauseWidget.setVisible(false);

        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBox = new HBox(10, stopButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        bottomBar.getChildren().addAll(pauseWidget, new Label("Delay:"), delayDropdown, spacer, buttonBox);

        // Assemble Root
        root.setTop(progressBarContainer);
        root.setCenter(scrollPane);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 600, 450); // Increased width for dropdown
        setScene(scene);
        
        progressBarContainer.prefWidthProperty().bind(scene.widthProperty().subtract(45));
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
        this.totalItems = total;
        this.currentIndex = 0;
        Platform.runLater(() -> {
            progressBarContainer.getChildren().clear();
            if (total > 0) {
                for (int i = 0; i < total; i++) {
                    Region segment = new Region();
                    HBox.setHgrow(segment, Priority.ALWAYS);
                    segment.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 0;");
                    progressBarContainer.getChildren().add(segment);
                }
            }
        });
    }

    public void addResult(boolean isValid) {
        Platform.runLater(() -> {
            if (currentIndex < progressBarContainer.getChildren().size()) {
                Node node = progressBarContainer.getChildren().get(currentIndex);
                if (node instanceof Region) {
                    String color = isValid ? "#4CAF50" : "#F44336";
                    node.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 0;");
                }
                currentIndex++;
            }
        });
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
                pauseLabel.setText("Paused: " + secondsRemaining + "s");
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
            if (lastNode instanceof TextFlow) {
                ((TextFlow) lastNode).getChildren().setAll(new Text(text));
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
            texts.add(new Text(parts[0]));
            Text validText = new Text(parts[1]);
            validText.setStyle("-fx-font-weight: bold; -fx-fill: green;");
            texts.add(validText);
        } else if (text.contains("[INVALID]")) {
            String[] parts = text.split("\\[INVALID\\]", 2);
            texts.add(new Text(parts[0]));
            Text invalidText = new Text(parts[1]);
            invalidText.setStyle("-fx-font-weight: bold; -fx-fill: red;");
            texts.add(invalidText);
        } else {
            texts.add(new Text(text));
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
}