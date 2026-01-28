package com.uiptv.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProgressDialog extends Stage {

    private final ProgressBar progressBar = new ProgressBar(0);
    private final VBox messageContainer = new VBox();
    private final ScrollPane scrollPane = new ScrollPane(messageContainer);
    private final Button cancelButton = new Button("Cancel");
    private final Button stopButton = new Button("Stop");
    
    // Pause Widget Components
    private final HBox pauseWidget = new HBox(10);
    private final Line clockHand = new Line(0, 0, 0, -10);
    private final Label pauseLabel = new Label();

    public ProgressDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Verifying MAC Addresses...");

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMinHeight(20);
        VBox.setVgrow(progressBar, Priority.NEVER);

        scrollPane.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;");
        scrollPane.setFitToWidth(true);
        messageContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));

        // Button styling
        String buttonStyle = "-fx-font-size: 14px; -fx-padding: 10 20 10 20;";
        cancelButton.setStyle(buttonStyle);
        stopButton.setStyle(buttonStyle);

        // Pause Widget Setup
        Circle clockFace = new Circle(12);
        clockFace.setFill(Color.TRANSPARENT);
        clockFace.setStroke(Color.GRAY);
        clockFace.setStrokeWidth(2);
        
        clockHand.setStroke(Color.RED);
        clockHand.setStrokeWidth(2);
        
        StackPane clockIcon = new StackPane(clockFace, clockHand);
        pauseWidget.getChildren().addAll(clockIcon, pauseLabel);
        pauseWidget.setAlignment(Pos.CENTER_LEFT);
        pauseWidget.setVisible(false); // Hidden by default

        // Bottom Bar Layout
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        VBox.setVgrow(bottomBar, Priority.NEVER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBox = new HBox(10, stopButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        bottomBar.getChildren().addAll(pauseWidget, spacer, buttonBox);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(progressBar, scrollPane, bottomBar);
        vbox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(vbox, 500, 450);
        setScene(scene);
    }

    public void setProgress(double progress) {
        Platform.runLater(() -> progressBar.setProgress(progress));
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
    
    public void setPauseStatus(int secondsRemaining) {
        Platform.runLater(() -> {
            if (secondsRemaining > 0) {
                pauseWidget.setVisible(true);
                pauseLabel.setText("Paused: " + secondsRemaining + "s");
                // Rotate hand: 360 degrees / 10 seconds = 36 degrees per second
                // We want it to tick. 
                double rotation = (10 - secondsRemaining) * 36; 
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