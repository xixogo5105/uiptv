package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.util.TextParserService;
import com.uiptv.widget.AppHeaderActions;
import com.uiptv.widget.AppPageHeader;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.SwitchToggle;
import com.uiptv.widget.UIptvTextArea;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.application.HostServices;

import java.util.List;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ParseMultipleAccountUI extends VBox {
    private static final Pattern TOKEN_ARTIFACT_PATTERN = Pattern.compile("__\\s*T\\s*K\\d+__|__TK\\d+__");
    private static final String BULK_HINT_FALLBACK = """
            Enter data to parse multiple stalker portal accounts
            e.g.
            http://website1.com/c
            00:00:00:00:00:00
            http://website2.com/c
            00:00:00:00:00:00
            http://website3.com/c
            00:00:00:00:00:00
            00:00:00:00:00:01
            00:00:00:00:00:02
            http://website4.com/c
            00:00:00:00:00:00
            00:00:00:00:00:01
            00:00:00:00:00:02
            For M3U playlist use the following format:
            http://somewebsiteurl.iptv:8080/get.php?username=username&password=password&type=m3u
            http://somewebsiteurl2.iptv:8080/get.php?username=username2&password=password2&type=m3u
            """;
    private final UIptvTextArea multipleSPAccounts = new UIptvTextArea("multipleSPAccounts", "parseMultipleAccountsInputHint", 5);
    private static final String GROUP_BY_MAC_LABEL = "autoGroupAccountsByMACAddress";
    private static final String GROUP_BY_XTREME_LABEL = "autoGroupAccountsByUsernamePassword";
    private final PillBar<String> parseModePillBar = new PillBar<>(mode -> mode, mode -> mode);
    private final SwitchToggle groupAccountsSwitch = new SwitchToggle();
    private final SwitchToggle convertM3uToXtremeSwitch = new SwitchToggle();
    private final SwitchToggle startVerificationAfterParsingSwitch = new SwitchToggle();
    private final Label groupAccountsLabel = new Label(I18n.tr(GROUP_BY_MAC_LABEL));
    private final Label convertM3uToXtremeLabel = new Label(I18n.tr("autoWherePossibleConvertM3UToXtreme"));
    private final Label startVerificationAfterParsingLabel = new Label(I18n.tr("autoStartVerificationAfterParsing"));
    private final Button saveButton = new Button(I18n.tr("parseAndSave"));
    private final Button clearButton = new Button(I18n.tr("autoClear"));
    private final VBox contentContainer = new VBox();
    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private HBox groupAccountsRow;
    private HBox convertM3uToXtremeRow;
    private Callback<Void> onSaveCallback;

    public ParseMultipleAccountUI() {
        this(null, null);
    }

    public ParseMultipleAccountUI(HostServices hostServices, Runnable themeToggleHandler) {
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        initWidgets();
    }

    public void addCallbackHandler(Callback<Void> onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        getStyleClass().add("bulk-import-page");
        contentContainer.getStyleClass().add("bulk-import-content");
        contentContainer.setPadding(new Insets(24, 10, 20, 10));
        contentContainer.setSpacing(14);
        contentContainer.setFillWidth(true);
        contentContainer.setMinSize(0, 0);
        contentContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ScrollPane scrollPane = new ScrollPane(contentContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);

        saveButton.getStyleClass().add("bulk-import-primary-button");
        saveButton.setMinWidth(Region.USE_PREF_SIZE);
        saveButton.setMaxWidth(Region.USE_PREF_SIZE);
        clearButton.getStyleClass().add("bulk-import-clear-button");
        clearButton.setMinWidth(Region.USE_PREF_SIZE);
        multipleSPAccounts.getStyleClass().add("bulk-import-input");
        multipleSPAccounts.setMinHeight(360);
        multipleSPAccounts.setPrefHeight(620);
        multipleSPAccounts.setMaxHeight(Double.MAX_VALUE);
        multipleSPAccounts.setPromptText(resolveBulkHintPrompt());

        parseModePillBar.getStyleClass().add("watching-now-mode-pill-bar");
        parseModePillBar.setItems(List.of(TextParserService.MODE_STALKER, TextParserService.MODE_XTREME, TextParserService.MODE_M3U));
        parseModePillBar.setSelectedItem(TextParserService.MODE_STALKER);

        groupAccountsSwitch.setSelected(true);
        convertM3uToXtremeSwitch.setSelected(true);
        startVerificationAfterParsingSwitch.setSelected(true);

        groupAccountsRow = createSwitchRow(groupAccountsLabel, groupAccountsSwitch);
        convertM3uToXtremeRow = createSwitchRow(convertM3uToXtremeLabel, convertM3uToXtremeSwitch);
        HBox verificationRow = createSwitchRow(startVerificationAfterParsingLabel, startVerificationAfterParsingSwitch);
        groupAccountsRow.managedProperty().bind(groupAccountsRow.visibleProperty());
        convertM3uToXtremeRow.managedProperty().bind(convertM3uToXtremeRow.visibleProperty());

        AppPageHeader pageHeader = new AppPageHeader(
                I18n.tr("autoImportBulkAccounts"),
                new AppHeaderActions(hostServices, themeToggleHandler, null)
        );

        VBox modeCard = createModeCard(groupAccountsRow, convertM3uToXtremeRow, verificationRow);
        VBox editorCard = createEditorCard();
        HBox actionRow = createActionRow();
        contentContainer.getChildren().setAll(pageHeader, modeCard, editorCard, actionRow);
        VBox.setVgrow(editorCard, Priority.ALWAYS);
        addSubmitButtonClickHandler();
        addClearButtonClickHandler();
        addModeListener();
        registerSceneCleanupListener();

        // Initial state
        updateCheckboxesVisibility(TextParserService.MODE_STALKER);
    }

    private VBox createModeCard(HBox groupRow, HBox m3uRow, HBox verificationRow) {
        Label modeTitle = new Label(I18n.tr("bulkImportSourceType"));
        modeTitle.getStyleClass().add("bulk-import-section-title");

        Label modeDescription = new Label(I18n.tr("bulkImportSourceDescription"));
        modeDescription.getStyleClass().add("bulk-import-description");
        modeDescription.setWrapText(true);

        VBox modeGroup = new VBox(8, modeTitle, modeDescription, parseModePillBar);
        modeGroup.setFillWidth(true);
        modeGroup.setMaxWidth(Double.MAX_VALUE);

        VBox optionsGroup = new VBox(8, groupRow, m3uRow, verificationRow);
        optionsGroup.getStyleClass().add("bulk-import-options");
        optionsGroup.setFillWidth(true);
        optionsGroup.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(14, modeGroup, optionsGroup);
        card.getStyleClass().add("bulk-import-card");
        card.getStyleClass().add("bulk-import-mode-card");
        card.setFillWidth(true);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox createEditorCard() {
        Label title = new Label(I18n.tr("bulkImportDataTitle"));
        title.getStyleClass().add("bulk-import-section-title");

        Label description = new Label(I18n.tr("bulkImportDataDescription"));
        description.getStyleClass().add("bulk-import-description");
        description.setWrapText(true);

        VBox card = new VBox(10, title, description, multipleSPAccounts);
        card.getStyleClass().add("bulk-import-card");
        card.getStyleClass().add("bulk-import-editor-card");
        card.setFillWidth(true);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(0);
        card.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(multipleSPAccounts, Priority.ALWAYS);
        return card;
    }

    private HBox createActionRow() {
        HBox row = new HBox(10, saveButton, clearButton);
        row.getStyleClass().add("bulk-import-actions");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private HBox createSwitchRow(Label label, SwitchToggle toggle) {
        label.getStyleClass().add("bulk-import-switch-label");
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        toggle.setAccessibleText(label.getText());
        label.textProperty().addListener((_, _, text) -> toggle.setAccessibleText(text));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, label, spacer, toggle);
        row.getStyleClass().add("bulk-import-switch-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        label.setOnMouseClicked(event -> {
            if (!toggle.isDisabled()) {
                toggle.setSelected(!toggle.isSelected());
                toggle.requestFocus();
                event.consume();
            }
        });
        return row;
    }

    private void registerSceneCleanupListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                releaseTransientState();
            }
        });
    }

    private void releaseTransientState() {
        multipleSPAccounts.clear();
    }

    private void addModeListener() {
        parseModePillBar.selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                updateCheckboxesVisibility(newV);
            }
        });
    }

    private void updateCheckboxesVisibility(String mode) {
        boolean showGroup = TextParserService.MODE_STALKER.equals(mode) || TextParserService.MODE_XTREME.equals(mode);
        groupAccountsRow.setVisible(showGroup);
        if (TextParserService.MODE_XTREME.equals(mode)) {
            groupAccountsLabel.setText(I18n.tr(GROUP_BY_XTREME_LABEL));
        } else {
            groupAccountsLabel.setText(I18n.tr(GROUP_BY_MAC_LABEL));
        }
        groupAccountsSwitch.setAccessibleText(groupAccountsLabel.getText());
        convertM3uToXtremeRow.setVisible(TextParserService.MODE_M3U.equals(mode));
    }

    private void addClearButtonClickHandler() {
        clearButton.setOnAction(event -> clearAll());
    }

    private void clearAll() {
        multipleSPAccounts.clear();
        parseModePillBar.setSelectedItem(TextParserService.MODE_STALKER);
        groupAccountsSwitch.setSelected(true);
        convertM3uToXtremeSwitch.setSelected(true);
        startVerificationAfterParsingSwitch.setSelected(true);
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(_ -> {
            try {
                if (isBlank(multipleSPAccounts.getText())) {
                    showErrorAlert(I18n.tr("autoInputCannotBeEmpty"));
                    return;
                }
                String selectedMode = parseModePillBar.getSelectedItem();
                boolean startVerificationAfterParsing = startVerificationAfterParsingSwitch.isSelected();
                List<Account> createdAccounts = TextParserService.saveBulkAccounts(multipleSPAccounts.getText(), selectedMode, groupAccountsSwitch.isSelected(), convertM3uToXtremeSwitch.isSelected());
                clearAll();
                if (onSaveCallback != null) {
                    onSaveCallback.call(null);
                }
                showMessageAlert(I18n.tr("autoAccountsParsedAndSaved"));
                if (startVerificationAfterParsing && createdAccounts != null && !createdAccounts.isEmpty()) {
                    openVerificationPopup(createdAccounts);
                }
            } catch (Exception _) {
                showErrorAlert(I18n.tr("autoErrorParsingOrSavingAccounts"));
            }
        });
    }

    private void openVerificationPopup(List<Account> accountsToVerify) {
        ReloadCacheInline.open(accountsToVerify, this::notifyAccountsChanged);
    }

    private void notifyAccountsChanged() {
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private String resolveBulkHintPrompt() {
        String raw = I18n.tr("parseMultipleAccountsInputHint");
        if (raw == null || raw.isBlank()) {
            return BULK_HINT_FALLBACK;
        }
        if (TOKEN_ARTIFACT_PATTERN.matcher(raw).find()) {
            return BULK_HINT_FALLBACK;
        }
        return raw
                .replace("\\\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\:", ":")
                .replace("\\=", "=")
                .replace("\\/", "/")
                .replaceAll("https?\\s+://", "http://");
    }
}
