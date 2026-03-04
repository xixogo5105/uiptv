package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ThemedDialogs {
    private ThemedDialogs() {
    }

    public static void showInfo(String title, String message) {
        showDialog(title, message, ButtonType.CLOSE);
    }

    public static void showError(String title, String message) {
        showDialog(title, message, ButtonType.CLOSE);
    }

    public static ButtonType showConfirm(String title, String message, ButtonType... buttons) {
        return showDialog(title, message, buttons);
    }

    private static ButtonType showDialog(String title, String message, ButtonType... buttons) {
        if (!Platform.isFxApplicationThread()) {
            AtomicReference<ButtonType> resultRef = new AtomicReference<>(ButtonType.CLOSE);
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    resultRef.set(showDialogInternal(title, message, buttons));
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return resultRef.get();
        }
        return showDialogInternal(title, message, buttons);
    }

    private static ButtonType showDialogInternal(String title, String message, ButtonType... buttons) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        if (RootApplication.primaryStage != null) {
            stage.initOwner(RootApplication.primaryStage);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("custom-dialog-title");

        Button closeButton = new Button("x");
        closeButton.getStyleClass().add("custom-dialog-close");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleLabel, spacer, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("custom-dialog-header");

        Label contentLabel = new Label(message == null ? "" : message);
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("custom-dialog-message");
        VBox.setVgrow(contentLabel, Priority.ALWAYS);

        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.getStyleClass().add("custom-dialog-buttons");

        AtomicReference<ButtonType> resultRef = new AtomicReference<>(ButtonType.CLOSE);
        ButtonType[] resolvedButtons = buttons == null || buttons.length == 0
                ? new ButtonType[]{ButtonType.CLOSE}
                : buttons;

        Button defaultButton = null;
        AtomicReference<Button> cancelButtonRef = new AtomicReference<>();
        for (ButtonType buttonType : resolvedButtons) {
            Button button = new Button(buttonType.getText());
            if (buttonType == ButtonType.YES || buttonType == ButtonType.OK) {
                button.getStyleClass().add("prominent");
            }
            if (buttonType == ButtonType.NO || buttonType == ButtonType.CLOSE || buttonType == ButtonType.CANCEL) {
                button.setCancelButton(true);
                if (cancelButtonRef.get() == null) {
                    cancelButtonRef.set(button);
                }
            }
            if ((buttonType == ButtonType.NO || buttonType == ButtonType.CLOSE) && defaultButton == null) {
                button.setDefaultButton(true);
                defaultButton = button;
            }
            if (defaultButton == null && (buttonType == ButtonType.OK || buttonType == ButtonType.YES)) {
                button.setDefaultButton(true);
                defaultButton = button;
            }
            button.setOnAction(e -> {
                resultRef.set(buttonType);
                stage.close();
            });
            buttonRow.getChildren().add(button);
        }

        closeButton.setOnAction(e -> {
            Button cancelButton = cancelButtonRef.get();
            if (cancelButton != null) {
                cancelButton.fire();
            } else {
                resultRef.set(ButtonType.CLOSE);
                stage.close();
            }
        });

        VBox root = new VBox(10, header, contentLabel, buttonRow);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("custom-dialog-root");

        Scene scene = new Scene(root, 500, Region.USE_COMPUTED_SIZE);
        scene.setFill(Color.TRANSPARENT);
        if (RootApplication.currentTheme != null) {
            scene.getStylesheets().add(RootApplication.currentTheme);
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                Button cancelButton = cancelButtonRef.get();
                if (cancelButton != null) {
                    cancelButton.fire();
                } else {
                    resultRef.set(ButtonType.CLOSE);
                    stage.close();
                }
            }
        });

        stage.setScene(scene);
        stage.showAndWait();
        return resultRef.get();
    }
}
