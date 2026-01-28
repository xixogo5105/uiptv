package com.uiptv.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProgressDialog extends Stage {

    private final ProgressBar progressBar = new ProgressBar(0);
    private final VBox messageContainer = new VBox(); // Use a VBox to hold each line (TextFlow)
    private final ScrollPane scrollPane = new ScrollPane(messageContainer);
    private final Button closeButton = new Button("Close");

    public ProgressDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Verifying MAC Addresses...");

        progressBar.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;");
        scrollPane.setFitToWidth(true);
        messageContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(progressBar, scrollPane, closeButton);
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

        // Simple parser for messages like "Verifying... [VALID]VALID"
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
        closeButton.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to cancel the verification process?", ButtonType.YES, ButtonType.NO);
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
            closeButton.fire();
        });
    }
}