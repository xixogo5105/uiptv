package com.uiptv.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_HEIGHT_PIXELS;
import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;

public class LogDisplayUI extends VBox {
    private static TextArea logArea = new TextArea();
    private final Button clearLogButton= new Button("Clear");
    private final Button copyLogButton = new Button("Copy");
    private static boolean isLoggingEnabled = false;

    public LogDisplayUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        logArea.setPrefHeight(GUIDED_MAX_HEIGHT_PIXELS);
        logArea.getStyleClass().add("terminal");

        clearLogButton.setOnAction(event -> {
            logArea.clear();
        });

        copyLogButton.setOnAction(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(logArea.getText());
            clipboard.setContent(content);
        });

        HBox controlBox = new HBox(10);
        controlBox.getChildren().addAll(copyLogButton, clearLogButton);

        VBox vbox = new VBox(5, logArea, controlBox);
        vbox.getChildren().forEach(child -> VBox.setVgrow(child, Priority.ALWAYS));
        getChildren().addAll(vbox);
    }

    public static void addLog(String log) {
        System.out.println(log);
        if (isLoggingEnabled) {
            logArea.appendText(log + "\n");
        }
    }

    public static void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
        if (!enabled) {
            logArea.clear();
        }
    }
}