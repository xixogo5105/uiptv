package com.uiptv.widget;

import com.uiptv.api.LoggerCallback;
import com.uiptv.ui.RootApplication;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import static com.uiptv.ui.RootApplication.primaryStage;

public class LogPopupUI extends Stage {

    private final TextFlow logArea;
    private final ScrollPane scrollPane;
    private final HBox bottomBar = new HBox(10);
    private Timeline timeline;
    private Runnable onStop;

    public LogPopupUI(String title) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(primaryStage);
        
        // Allow closing, but trigger onStop if set
        setOnCloseRequest(e -> {
            if (onStop != null) {
                onStop.run();
            }
        });

        VBox dialogVbox = new VBox(10);
        dialogVbox.setPadding(new Insets(10, 10, 10, 10));
        dialogVbox.getChildren().add(new Label(title));

        logArea = new TextFlow();
        scrollPane = new ScrollPane(logArea);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        dialogVbox.getChildren().add(scrollPane);

        logArea.heightProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue(1.0));

        // Initialize bottom bar with Close button
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            if (onStop != null) {
                onStop.run();
            }
            close();
        });
        bottomBar.getChildren().add(closeButton);
        dialogVbox.getChildren().add(bottomBar);

        Scene dialogScene = new Scene(dialogVbox, 672, 342); // Adjusted dimensions
        if (RootApplication.currentTheme != null) {
            dialogScene.getStylesheets().add(RootApplication.currentTheme);
        }
        setScene(dialogScene);
    }

    public void setOnStop(Runnable onStop) {
        this.onStop = onStop;
    }

    public LoggerCallback getLogger() {
        return logMessage -> Platform.runLater(() -> {
            Text text = new Text(logMessage + "\n");
            logArea.getChildren().add(text);
        });
    }

    public void closeGracefully() {
        Platform.runLater(() -> {
            // Process finished, so onStop is no longer needed
            onStop = null;
            setOnCloseRequest(null);

            // Add final message
            Text finalMessage = new Text("All done\n");
            finalMessage.setStyle("-fx-font-weight: bold;");
            finalMessage.setFill(Color.DARKGREEN);
            logArea.getChildren().add(finalMessage);

            // Update bottom bar
            bottomBar.getChildren().clear();
            bottomBar.setAlignment(Pos.CENTER_LEFT);

            Label countdownLabel = new Label();
            AnalogClock clock = new AnalogClock();
            Button closeButton = new Button("Close");
            closeButton.setOnAction(e -> {
                if (timeline != null) {
                    timeline.stop();
                }
                this.close();
            });

            Pane spacer = new Pane();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            bottomBar.getChildren().addAll(clock, countdownLabel, spacer, closeButton);

            // Start countdown
            final int[] secondsRemaining = {30};
            timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                secondsRemaining[0]--;
                countdownLabel.setText("Auto closing this window in " + secondsRemaining[0]);
                clock.setSeconds(secondsRemaining[0]);
                if (secondsRemaining[0] <= 0) {
                    timeline.stop();
                    if (isShowing()) {
                        close();
                    }
                }
            }));
            timeline.setCycleCount(30);
            timeline.play();
            countdownLabel.setText("Auto closing this window in 30");
        });
    }
}
