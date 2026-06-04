package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    private final Label progressSummaryLabel = new Label();
    private final Button cancelButton = new Button(I18n.tr("autoCancel"));
    private final Button stopButton = new Button(I18n.tr("autoStop"));
    private final ComboBox<String> delayDropdown = new ComboBox<>();
    private int totalItems;
    private int completedItems;
    private VBox currentVerificationDetails;
    private TextFlow lastRenderedLine;
    
    // Pause Widget Components
    private final HBox pauseWidget = new HBox(10);
    private final Line clockHand = new Line(0, 0, 0, -10);
    private final Label pauseLabel = new Label();

    public ProgressDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(I18n.tr("autoVerifyingMacAddresses"));
        setMinWidth(620);
        setMinHeight(500);

        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("management-popup-root", "verification-progress-root");
        root.setPadding(new Insets(18));

        VBox header = buildHeader();
        VBox progressCard = buildProgressCard();
        VBox topContent = new VBox(14, header, progressCard);

        VBox logCard = buildLogCard();

        HBox bottomBar = buildBottomBar();

        root.setTop(topContent);
        root.setCenter(logCard);
        BorderPane.setMargin(bottomBar, new Insets(14, 0, 0, 0));
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 640, 520);
        UiI18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        setScene(scene);

        progressBar.prefWidthProperty().bind(progressCard.widthProperty().subtract(28));
    }

    private VBox buildHeader() {
        Label title = new Label(I18n.tr("autoVerifyingMacAddresses"));
        title.getStyleClass().add("management-popup-title");

        VBox header = new VBox(2, title);
        header.getStyleClass().add("management-popup-header");
        return header;
    }

    private VBox buildProgressCard() {
        progressSummaryLabel.getStyleClass().add("verification-progress-summary");
        updateProgressSummary();

        VBox progressCard = new VBox(8, progressSummaryLabel, progressBar);
        progressCard.getStyleClass().addAll("management-popup-card", "verification-progress-card");
        progressCard.setMaxWidth(Double.MAX_VALUE);
        return progressCard;
    }

    private VBox buildLogCard() {
        Label logTitle = new Label(I18n.tr("autoLogs"));
        logTitle.getStyleClass().add("management-popup-section-title");

        scrollPane.getStyleClass().addAll("log-scroll-pane", "verification-log-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        messageContainer.getStyleClass().addAll("log-message-container", "verification-log-container");
        messageContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));

        VBox logCard = new VBox(10, logTitle, scrollPane);
        logCard.getStyleClass().addAll("management-popup-card", "verification-log-card");
        logCard.setMaxWidth(Double.MAX_VALUE);
        logCard.setMaxHeight(Double.MAX_VALUE);
        BorderPane.setMargin(logCard, new Insets(14, 0, 0, 0));
        return logCard;
    }

    private HBox buildBottomBar() {
        cancelButton.getStyleClass().add("reload-secondary-button");
        stopButton.getStyleClass().add("dangerous");

        delayDropdown.setItems(FXCollections.observableArrayList("1 sec", "5 secs", "10 secs", "30 secs", "1 min", "10 mins", "30 mins"));
        delayDropdown.setValue("10 secs");
        delayDropdown.getStyleClass().add("verification-delay-combo");

        Circle clockFace = new Circle(12);
        clockFace.getStyleClass().add("clock-face");
        clockHand.getStyleClass().add("clock-hand");
        StackPane clockIcon = new StackPane(clockFace, clockHand);
        pauseWidget.getStyleClass().add("verification-pause-widget");
        pauseWidget.getChildren().addAll(clockIcon, pauseLabel);
        pauseWidget.setAlignment(Pos.CENTER_LEFT);
        pauseWidget.setVisible(false);
        pauseWidget.setManaged(false);

        Label delayLabel = new Label(I18n.tr("autoDelay"));
        delayLabel.getStyleClass().add("verification-delay-label");

        HBox bottomBar = new HBox(10);
        bottomBar.getStyleClass().add("management-popup-footer");
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBar.getChildren().addAll(pauseWidget, delayLabel, delayDropdown, stopButton, spacer, cancelButton);
        return bottomBar;
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
        totalItems = Math.max(0, total);
        completedItems = 0;
        updateProgressSummary();
        progressBar.setTotal(total);
    }

    public void addResult(boolean isValid) {
        completedItems++;
        updateProgressSummary();
        progressBar.addResult(isValid);
    }

    public void addProgressText(String text) {
        Platform.runLater(() -> {
            String safeText = text == null ? "" : text;
            if (safeText.startsWith("[UPDATE_LAST]")) {
                updateLastLine(safeText.substring(13));
            } else {
                appendProgressText(safeText);
            }
        });
    }
    
    public void setPauseStatus(int secondsRemaining, int totalSeconds) {
        Platform.runLater(() -> {
            if (secondsRemaining > 0) {
                pauseWidget.setVisible(true);
                pauseWidget.setManaged(true);
                pauseLabel.setText(I18n.tr("autoPausedSeconds", secondsRemaining));
                double rotation = ((double)(totalSeconds - secondsRemaining) / totalSeconds) * 360;
                clockHand.setRotate(rotation);
            } else {
                pauseWidget.setVisible(false);
                pauseWidget.setManaged(false);
            }
        });
    }

    private void updateLastLine(String text) {
        String safeText = text == null ? "" : text;
        if (isSeparatorLine(safeText)) {
            return;
        }
        if (lastRenderedLine == null) {
            appendProgressText(safeText);
            return;
        }
        applyStyledText(lastRenderedLine, displayText(safeText));
    }

    private void updateProgressSummary() {
        Platform.runLater(() -> progressSummaryLabel.setText(totalItems <= 0
                ? I18n.tr("autoQueued")
                : I18n.tr("autoRunningProgress", Math.min(completedItems, totalItems), totalItems)));
    }

    private void appendProgressText(String text) {
        String safeText = text == null ? "" : text;
        if (safeText.isBlank() || isSeparatorLine(safeText)) {
            return;
        }
        if (isDetailLine(safeText) && currentVerificationDetails != null) {
            TextFlow detailLine = createStyledText(displayText(safeText));
            detailLine.getStyleClass().add("verification-card-detail-line");
            currentVerificationDetails.getChildren().add(detailLine);
            lastRenderedLine = detailLine;
            return;
        }

        VBox card = createVerificationCard(displayText(safeText));
        messageContainer.getChildren().add(card);
    }

    private VBox createVerificationCard(String titleText) {
        TextFlow title = createStyledText(titleText);
        title.getStyleClass().add("verification-card-title");
        title.getChildren().forEach(text -> text.getStyleClass().add("verification-card-title-text"));

        VBox details = new VBox(6);
        details.getStyleClass().add("verification-card-details");

        VBox card = new VBox(8, title, details);
        card.getStyleClass().add("verification-mac-card");
        card.setMaxWidth(Double.MAX_VALUE);

        currentVerificationDetails = details;
        lastRenderedLine = title;
        return card;
    }

    private boolean isSeparatorLine(String text) {
        return text != null && text.trim().matches("-{8,}");
    }

    private boolean isDetailLine(String text) {
        return text != null && !text.isBlank() && Character.isWhitespace(text.charAt(0));
    }

    private String displayText(String text) {
        return text == null ? "" : text.trim();
    }

    private TextFlow createStyledText(String text) {
        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("verification-card-text");
        textFlow.setMaxWidth(Double.MAX_VALUE);
        applyStyledText(textFlow, text);
        return textFlow;
    }

    private void applyStyledText(TextFlow textFlow, String text) {
        String safeText = text == null ? "" : text;
        List<Text> texts = new ArrayList<>();

        if (safeText.contains("[VALID]")) {
            String[] parts = safeText.split("\\[VALID\\]", 2);
            Text part1 = new Text(parts[0]);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
            Text validText = new Text(parts[1]);
            validText.getStyleClass().add("valid-text");
            texts.add(validText);
        } else if (safeText.contains("[INVALID]")) {
            String[] parts = safeText.split("\\[INVALID\\]", 2);
            Text part1 = new Text(parts[0]);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
            Text invalidText = new Text(parts[1]);
            invalidText.getStyleClass().add("invalid-text");
            texts.add(invalidText);
        } else {
            Text part1 = new Text(safeText);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
        }

        textFlow.getChildren().setAll(texts);
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
