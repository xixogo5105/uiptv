package com.uiptv.ui;

import com.uiptv.db.AccountDb;
import com.uiptv.model.Account;
import com.uiptv.service.ChannelService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ProminentButton;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.stream.Collectors;

public class ReloadCachePopup extends VBox {

    private final Stage stage;
    private final VBox accountsVBox = new VBox(5);
    private final VBox logVBox = new VBox(5);
    private final ScrollPane accountsScrollPane = new ScrollPane();
    private final ScrollPane logScrollPane = new ScrollPane(logVBox);
    private final CheckBox selectAllCheckBox = new CheckBox("Select All");
    private final HBox progressBarContainer = new HBox(0);
    private final ProminentButton reloadButton = new ProminentButton("Reload Selected");
    private final StackPane loadingIndicator = createLoadingIndicator();

    public ReloadCachePopup(Stage stage) {
        this.stage = stage;
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefSize(1368, 720);
        getStylesheets().add(RootApplication.currentTheme);

        progressBarContainer.setMinHeight(20);
        progressBarContainer.setPrefHeight(20);
        progressBarContainer.setMaxHeight(20);
        progressBarContainer.setAlignment(Pos.CENTER_LEFT);
        progressBarContainer.getStyleClass().add("progress-bar-container");

        accountsVBox.setPadding(new Insets(10));
        List<Account> stalkerAccounts = AccountDb.get().getAccounts().stream()
                .filter(account -> account.getType() == AccountType.STALKER_PORTAL)
                .collect(Collectors.toList());

        for (int i = 0; i < stalkerAccounts.size(); i++) {
            Account account = stalkerAccounts.get(i);
            CheckBox accountCheckBox = new CheckBox(account.getAccountName());
            accountCheckBox.setUserData(account);
            accountCheckBox.setMaxWidth(Double.MAX_VALUE);
            accountCheckBox.setPadding(new Insets(5));

            if (i % 2 == 0) {
                accountCheckBox.setStyle("-fx-background-color: derive(-fx-control-inner-background, -2%);");
            } else {
                accountCheckBox.setStyle("-fx-background-color: -fx-control-inner-background;");
            }
            accountsVBox.getChildren().add(accountCheckBox);
        }

        selectAllCheckBox.setOnAction(event -> {
            boolean selected = selectAllCheckBox.isSelected();
            accountsVBox.getChildren().forEach(node -> {
                if (node instanceof CheckBox) {
                    ((CheckBox) node).setSelected(selected);
                }
            });
        });

        accountsScrollPane.setContent(accountsVBox);
        accountsScrollPane.setFitToWidth(true);
        accountsScrollPane.setMaxHeight(400);

        logVBox.setPadding(new Insets(5));
        logScrollPane.setFitToWidth(true);
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);
        logVBox.heightProperty().addListener((obs, oldVal, newVal) -> logScrollPane.setVvalue(1.0));


        reloadButton.setOnAction(event -> {
            logVBox.getChildren().clear();
            new Thread(this::reloadSelectedAccounts).start();
        });

        Button copyLogButton = new Button("Copy Log");
        copyLogButton.setOnAction(event -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            StringBuilder sb = new StringBuilder();
            for (Node node : logVBox.getChildren()) {
                if (node instanceof Text) {
                    sb.append(((Text) node).getText()).append("\n");
                } else if (node instanceof TextFlow) {
                    ((TextFlow) node).getChildren().forEach(t -> {
                        if (t instanceof Text) {
                            sb.append(((Text) t).getText());
                        }
                    });
                    sb.append("\n");
                }
            }
            content.putString(sb.toString());
            clipboard.setContent(content);
        });

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBox = new HBox(10, reloadButton, loadingIndicator, spacer, copyLogButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        getChildren().addAll(progressBarContainer, selectAllCheckBox, accountsScrollPane, logScrollPane, buttonBox);
    }

    private StackPane createLoadingIndicator() {
        SVGPath svgPath = new SVGPath();
        svgPath.setContent("M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z");
        svgPath.setFill(Color.TRANSPARENT);

        SVGPath spinner = new SVGPath();
        spinner.setContent("M12,2A10,10,0,0,1,22,12");
        spinner.setStroke(Color.BLACK);
        spinner.setStrokeWidth(3);
        spinner.setFill(Color.TRANSPARENT);

        StackPane stackPane = new StackPane(svgPath, spinner);
        stackPane.setPrefSize(24, 24);
        stackPane.setVisible(false);

        RotateTransition rotateTransition = new RotateTransition(Duration.seconds(1), spinner);
        rotateTransition.setByAngle(360);
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE);
        rotateTransition.play();

        return stackPane;
    }

    private void reloadSelectedAccounts() {
        Platform.runLater(() -> {
            reloadButton.setVisible(false);
            loadingIndicator.setVisible(true);
        });

        List<CheckBox> selectedAccounts = accountsVBox.getChildren().stream()
                .filter(node -> node instanceof CheckBox && ((CheckBox) node).isSelected())
                .map(node -> (CheckBox) node)
                .collect(Collectors.toList());

        int total = selectedAccounts.size();
        Platform.runLater(() -> {
            progressBarContainer.getChildren().clear();
            if (total > 0) {
                for (int i = 0; i < total; i++) {
                    Region segment = new Region();
                    HBox.setHgrow(segment, Priority.ALWAYS);
                    segment.getStyleClass().add("progress-bar-segment");
                    progressBarContainer.getChildren().add(segment);
                }
            }
        });

        for (int i = 0; i < total; i++) {
            CheckBox checkBox = selectedAccounts.get(i);
            Account account = (Account) checkBox.getUserData();
            boolean success = false;
            try {
                ChannelService.getInstance().reloadAllChannelsAndCategories(account, this::logMessage);
                success = true;
            } catch (Exception e) {
                logMessage("Error reloading cache for " + account.getAccountName() + ": " + e.getMessage());
            }

            final int index = i;
            final boolean result = success;
            Platform.runLater(() -> {
                if (index < progressBarContainer.getChildren().size()) {
                    Region segment = (Region) progressBarContainer.getChildren().get(index);
                    segment.getStyleClass().add(result ? "success" : "failure");
                }
            });
        }

        Platform.runLater(() -> {
            Text allDoneText = new Text("All done.");
            allDoneText.setStyle("-fx-font-weight: bold; -fx-fill: darkgreen;");
            logVBox.getChildren().add(allDoneText);
            System.out.print("\u0007"); // Beep
            reloadButton.setVisible(true);
            loadingIndicator.setVisible(false);
        });
    }

    public void logMessage(String message) {
        Platform.runLater(() -> logVBox.getChildren().add(new Text(message)));
    }
}
