package com.uiptv.ui;

import com.uiptv.util.I18n;
import com.uiptv.widget.InlinePanelService;
import com.uiptv.widget.InlinePanelService.InlinePanelHandle;
import com.uiptv.widget.SegmentedProgressBar;
import com.uiptv.widget.UIptvAlert;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;

public class ProgressInline extends BorderPane {
    private static final String LOG_TEXT_STYLE_CLASS = "log-text";
    private static final double CARD_MAX_WIDTH = 1180;
    private static final double CARD_PREF_WIDTH = 1040;

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
    private Runnable cancelAction = () -> { };
    private Runnable closePanelAction = () -> { };
    private InlinePanelHandle panelHandle;
    private boolean completed;
    private String defaultMacAddress = "";
    
    // Callback actions for MAC card hover actions
    private Consumer<String> onSetDefaultMac = mac -> { };
    private Consumer<String> onDeleteMac = mac -> { };
    
    // Direct stop action (without confirmation)
    private Runnable directStopAction = () -> { };
    
    // Pause Widget Components
    private final HBox pauseWidget = new HBox(10);
    private final Line clockHand = new Line(0, 0, 0, -10);
    private final Label pauseLabel = new Label();

    public ProgressInline() {
        getStyleClass().addAll("management-popup-root", "verification-progress-root", InlinePanelService.FILL_HEIGHT_STYLE_CLASS);
        setPadding(Insets.EMPTY);
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox header = buildHeader();
        VBox progressCard = buildProgressCard();
        VBox topContent = new VBox(14, header, progressCard);

        VBox logCard = buildLogCard();

        HBox bottomBar = buildBottomBar();

        BorderPane card = new BorderPane();
        card.getStyleClass().add("verification-progress-card-shell");
        card.setTop(topContent);
        card.setCenter(logCard);
        BorderPane.setMargin(bottomBar, new Insets(14, 0, 0, 0));
        card.setBottom(bottomBar);
        card.setMinSize(0, 0);
        card.setPrefWidth(CARD_PREF_WIDTH);
        card.setMaxWidth(CARD_MAX_WIDTH);
        card.setMaxHeight(Double.MAX_VALUE);
        BorderPane.setMargin(logCard, new Insets(14, 0, 0, 0));

        StackPane shell = new StackPane(card);
        shell.getStyleClass().add("verification-progress-shell");
        shell.setAlignment(Pos.CENTER);
        shell.setMinSize(0, 0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setCenter(shell);
    }

    public void setPanelHandle(InlinePanelHandle panelHandle) {
        this.panelHandle = panelHandle;
    }

    public void setExternalCloseHandler(Runnable closeHandler) {
        this.closePanelAction = closeHandler == null ? () -> { } : closeHandler;
    }

    private VBox buildHeader() {
        Label title = new Label(I18n.tr("autoVerifyingMacAddresses"));
        title.getStyleClass().add("management-popup-title");

        VBox header = new VBox(2, title);
        header.getStyleClass().add("management-popup-header");
        header.setMaxWidth(Double.MAX_VALUE);
        return header;
    }

    private VBox buildProgressCard() {
        progressSummaryLabel.getStyleClass().add("verification-progress-summary");
        progressSummaryLabel.setMinWidth(Region.USE_PREF_SIZE);
        progressSummaryLabel.setMaxWidth(Region.USE_PREF_SIZE);
        updateProgressSummary();

        progressBar.setMinWidth(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        HBox progressRow = new HBox(12, progressSummaryLabel, progressBar);
        progressRow.getStyleClass().add("verification-progress-row");
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setMaxWidth(Double.MAX_VALUE);

        VBox progressCard = new VBox(progressRow);
        progressCard.getStyleClass().addAll("management-popup-card", "verification-progress-card");
        progressCard.setAlignment(Pos.CENTER_LEFT);
        progressCard.setFillWidth(true);
        progressCard.setMinWidth(0);
        progressCard.setMaxWidth(Double.MAX_VALUE);
        return progressCard;
    }

    private VBox buildLogCard() {
        Label logTitle = new Label(I18n.tr("autoLogs"));
        logTitle.getStyleClass().add("management-popup-section-title");

        scrollPane.getStyleClass().addAll("log-scroll-pane", "verification-log-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setMinHeight(0);
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        messageContainer.getStyleClass().addAll("log-message-container", "verification-log-container");
        messageContainer.setMaxWidth(Double.MAX_VALUE);
        messageContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));

        VBox logCard = new VBox(10, logTitle, scrollPane);
        logCard.getStyleClass().addAll("management-popup-card", "verification-log-card");
        logCard.setFillWidth(true);
        logCard.setMinHeight(0);
        logCard.setMaxWidth(Double.MAX_VALUE);
        logCard.setMaxHeight(Double.MAX_VALUE);
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
        bottomBar.setMinHeight(Region.USE_PREF_SIZE);
        bottomBar.setMaxWidth(Double.MAX_VALUE);
        
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
            case "sec", "secs":
                return value * 1000L;
            case "min", "mins":
                return value * 60 * 1000L;
            default:
                return 10000L;
        }
    }

    public void setDefaultMacAddress(String defaultMacAddress) {
        this.defaultMacAddress = defaultMacAddress == null ? "" : defaultMacAddress.trim();
    }

    public void updateDefaultMacAddress(String newDefaultMacAddress) {
        this.defaultMacAddress = newDefaultMacAddress == null ? "" : newDefaultMacAddress.trim();
        refreshVerificationCards();
    }

    private void refreshVerificationCards() {
        Platform.runLater(() -> {
            for (Node node : messageContainer.getChildren()) {
                String cardMac = (String) node.getProperties().get("macAddress");
                if (cardMac == null || cardMac.isBlank()) continue;

                // The node is either a VBox (for default MAC) or StackPane wrapper (for non-default)
                VBox card = (node instanceof StackPane wrapper) ? (VBox) wrapper.getChildren().get(0) : (VBox) node;
                
                // Check if this card is the new default
                boolean isNewDefault = isDefaultMac(cardMac);
                
                // Update the title row - add/remove default badge
                HBox titleRow = (HBox) card.getChildren().get(0);
                Label existingBadge = null;
                for (Node child : titleRow.getChildren()) {
                    if (child instanceof Label label && label.getStyleClass().contains("verification-default-pill")) {
                        existingBadge = label;
                        break;
                    }
                }

                if (isNewDefault && existingBadge == null) {
                    Label defaultBadge = new Label(I18n.tr("macHoverDefaultBadge"));
                    defaultBadge.getStyleClass().addAll("mac-default-pill", "verification-default-pill");
                    titleRow.getChildren().add(defaultBadge);
                } else if (!isNewDefault && existingBadge != null) {
                    titleRow.getChildren().remove(existingBadge);
                }

                // Update hover actions - remove if now default, add if no longer default
                if (node instanceof StackPane wrapper) {
                    HBox actions = null;
                    for (Node child : wrapper.getChildren()) {
                        if (child instanceof HBox hbox && hbox.getStyleClass().contains("verification-mac-card-actions")) {
                            actions = hbox;
                            break;
                        }
                    }

                    if (isNewDefault) {
                        // Now default - remove action buttons
                        if (actions != null) {
                            wrapper.getChildren().remove(actions);
                        }
                    } else {
                        // No longer default - add action buttons if not present
                        if (actions == null) {
                            HBox newActions = createMacCardActions(cardMac);
                            StackPane.setAlignment(newActions, Pos.TOP_RIGHT);
                            StackPane.setMargin(newActions, new Insets(8, 8, 0, 0));
                            wrapper.getChildren().add(newActions);
                        } else {
                            // Update the set default button state
                            for (Node btn : actions.getChildren()) {
                                if (btn instanceof Button button && button.getTooltip() != null 
                                        && button.getTooltip().getText().equals(I18n.tr("macHoverSetDefault"))) {
                                    button.setDisable(false);
                                }
                            }
                        }
                    }
                }
            }
        });
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

    public void addVerificationHeader(String mac, int index, int total) {
        Platform.runLater(() -> appendVerificationHeader(mac, index, total));
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

    private void appendVerificationHeader(String mac, int index, int total) {
        String safeMac = mac == null ? "" : mac.trim();
        Node card = createVerificationCardWithActions(
                I18n.tr("manageVerifyingMacProgress", index + 1, total, safeMac),
                safeMac,
                isDefaultMac(safeMac)
        );
        messageContainer.getChildren().add(card);
    }

    private VBox createVerificationCard(String titleText) {
        return createVerificationCard(titleText, false);
    }

    private VBox createVerificationCard(String titleText, boolean defaultMac) {
        return createVerificationCard(titleText, null, defaultMac);
    }

    private VBox createVerificationCard(String titleText, String macAddress, boolean defaultMac) {
        TextFlow title = createStyledText(titleText);
        title.getStyleClass().add("verification-card-title");
        title.getChildren().forEach(text -> text.getStyleClass().add("verification-card-title-text"));
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox titleRow = new HBox(8, title);
        titleRow.getStyleClass().add("verification-card-title-row");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        if (defaultMac) {
            Label defaultBadge = new Label(I18n.tr("macHoverDefaultBadge"));
            defaultBadge.getStyleClass().addAll("mac-default-pill", "verification-default-pill");
            titleRow.getChildren().add(defaultBadge);
        }

        VBox details = new VBox(6);
        details.getStyleClass().add("verification-card-details");

        VBox card = new VBox(8, titleRow, details);
        card.getStyleClass().add("verification-mac-card");
        card.setMaxWidth(Double.MAX_VALUE);

        currentVerificationDetails = details;
        lastRenderedLine = title;

        return card;
    }

    private Node createVerificationCardWithActions(String titleText, String macAddress, boolean defaultMac) {
        TextFlow title = createStyledText(titleText);
        title.getStyleClass().add("verification-card-title");
        title.getChildren().forEach(text -> text.getStyleClass().add("verification-card-title-text"));
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox titleRow = new HBox(8, title);
        titleRow.getStyleClass().add("verification-card-title-row");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        if (defaultMac) {
            Label defaultBadge = new Label(I18n.tr("macHoverDefaultBadge"));
            defaultBadge.getStyleClass().addAll("mac-default-pill", "verification-default-pill");
            titleRow.getChildren().add(defaultBadge);
        }

        VBox details = new VBox(6);
        details.getStyleClass().add("verification-card-details");

        VBox card = new VBox(8, titleRow, details);
        card.getStyleClass().add("verification-mac-card");
        card.setMaxWidth(Double.MAX_VALUE);

        currentVerificationDetails = details;
        lastRenderedLine = title;

        // Store MAC address on card for later lookup/removal
        if (macAddress != null && !macAddress.isBlank()) {
            card.getProperties().put("macAddress", macAddress.trim());
        }

        // If we have a MAC address and it's not the default MAC, add hover action buttons
        if (macAddress != null && !macAddress.isBlank() && !defaultMac) {
            return wrapCardWithHoverActions(card, macAddress);
        }
        return card;
    }

    private boolean isDefaultMac(String mac) {
        return mac != null && !mac.isBlank()
                && defaultMacAddress != null
                && mac.equalsIgnoreCase(defaultMacAddress);
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
            // split with limit 2 guarantees at least 1 element (never empty array)
            String firstPart = parts.length > 0 ? parts[0] : "";
            Text part1 = new Text(firstPart);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
            if (parts.length > 1) {
                Text validText = new Text(parts[1]);
                validText.getStyleClass().add("valid-text");
                texts.add(validText);
            }
        } else if (safeText.contains("[INVALID]")) {
            String[] parts = safeText.split("\\[INVALID\\]", 2);
            // split with limit 2 guarantees at least 1 element (never empty array)
            String firstPart = parts.length > 0 ? parts[0] : "";
            Text part1 = new Text(firstPart);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
            if (parts.length > 1) {
                Text invalidText = new Text(parts[1]);
                invalidText.getStyleClass().add("invalid-text");
                texts.add(invalidText);
            }
        } else {
            Text part1 = new Text(safeText);
            part1.getStyleClass().add(LOG_TEXT_STYLE_CLASS);
            texts.add(part1);
        }

        textFlow.getChildren().setAll(texts);
    }

    public void setOnClose(Runnable action) {
        cancelAction = action == null ? () -> { } : action;
        cancelButton.setOnAction(event -> requestClose());
    }

    public void requestClose() {
        if (completed) {
            closePanel();
            return;
        }
        if (showConfirmationAlert("Are you sure you want to cancel the verification process? No changes will be saved.")) {
            cancelAction.run();
            closePanel();
        }
    }

    public void setOnStop(Runnable action) {
        stopButton.setOnAction(event -> {
            if (showConfirmationAlert("Are you sure you want to stop? Invalid MACs found so far will be processed.")) {
                action.run();
            }
        });
    }

    public void markCompleted() {
        completed = true;
        stopButton.setDisable(true);
        cancelButton.setText(I18n.tr("commonClose"));
        cancelButton.setOnAction(event -> closePanel());
    }

    private void closePanel() {
        if (panelHandle != null) {
            panelHandle.close();
            return;
        }
        closePanelAction.run();
    }

    private Node wrapCardWithHoverActions(VBox card, String macAddress) {
        StackPane wrapper = new StackPane();
        wrapper.getChildren().add(card);

        // Store MAC address on wrapper for later lookup/removal
        if (macAddress != null && !macAddress.isBlank()) {
            wrapper.getProperties().put("macAddress", macAddress.trim());
        }

        // Create action buttons
        HBox actions = createMacCardActions(macAddress);
        StackPane.setAlignment(actions, Pos.TOP_RIGHT);
        StackPane.setMargin(actions, new Insets(8, 8, 0, 0)); // top, right, bottom, left
        wrapper.getChildren().add(actions);

        // Hide actions by default
        actions.setVisible(false);
        actions.setManaged(false);

        // Show actions on hover
        wrapper.setOnMouseEntered(event -> {
            actions.setVisible(true);
            actions.setManaged(true);
        });
        wrapper.setOnMouseExited(event -> {
            actions.setVisible(false);
            actions.setManaged(false);
        });

        return wrapper;
    }

    private HBox createMacCardActions(String macAddress) {
        // Set as default button (only show if not already default)
        boolean isCurrentDefault = isDefaultMac(macAddress);
        Button setDefaultBtn = createMacActionButton(
                I18n.tr("macHoverSetDefault"),
                "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z", // Star icon
                !isCurrentDefault,
                () -> {
                    if (!isCurrentDefault) {
                        onSetDefaultMac.accept(macAddress);
                    }
                });

        // Delete button (always show, but with confirmation)
        Button deleteBtn = createMacActionButton(
                I18n.tr("macHoverDelete"),
                "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z", // Trash icon
                true,
                () -> {
                    onDeleteMac.accept(macAddress);
                });

        HBox actions = new HBox(6, setDefaultBtn, deleteBtn);
        actions.getStyleClass().add("verification-mac-card-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        return actions;
    }

    private Button createMacActionButton(String tooltipText, String iconPath, boolean disableIfNotApplicable, Runnable action) {
        Button button = new Button();
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("verification-mac-card-action-icon");
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltipText));
        button.getStyleClass().add("verification-mac-action-button");
        button.setOnAction(_ -> action.run());
        
        return button;
    }

    public void setOnSetDefaultMac(Consumer<String> action) {
        this.onSetDefaultMac = action == null ? mac -> { } : action;
    }

    public void setOnDeleteMac(Consumer<String> action) {
        this.onDeleteMac = action == null ? mac -> { } : action;
    }

    public void setDirectStopAction(Runnable action) {
        this.directStopAction = action == null ? () -> { } : action;
    }

    public void requestDirectStop() {
        directStopAction.run();
    }

    /**
     * Removes the verification card for the given MAC address from the log.
     */
    public void removeVerificationCard(String macAddress) {
        Platform.runLater(() -> {
            String targetMac = macAddress == null ? "" : macAddress.trim();
            if (targetMac.isBlank()) return;

            for (Node node : messageContainer.getChildren()) {
                String cardId = (String) node.getProperties().get("macAddress");
                if (targetMac.equalsIgnoreCase(cardId)) {
                    messageContainer.getChildren().remove(node);
                    break;
                }
            }
        });
    }
}
