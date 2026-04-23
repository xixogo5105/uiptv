package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import com.uiptv.util.I18n;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.util.TextParserService;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvTextArea;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
    private final ComboBox<String> parseModeComboBox = new ComboBox<>();
    private static final String GROUP_BY_MAC_LABEL = "autoGroupAccountsByMACAddress";
    private static final String GROUP_BY_XTREME_LABEL = "autoGroupAccountsByUsernamePassword";
    private final CheckBox groupAccountsCheckBox = new CheckBox(I18n.tr(GROUP_BY_MAC_LABEL));
    private final CheckBox convertM3uToXtremeCheckBox = new CheckBox(I18n.tr("autoWherePossibleConvertM3UToXtreme"));
    private final CheckBox startVerificationAfterParsingCheckBox = new CheckBox(I18n.tr("autoStartVerificationAfterParsing"));
    private final ProminentButton saveButton = new ProminentButton(I18n.tr("parseAndSave"));
    private final Button clearButton = new Button(I18n.tr("autoClear"));
    private final VBox contentContainer = new VBox();
    private Callback<Void> onSaveCallback;

    public ParseMultipleAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback<Void> onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        contentContainer.setPadding(new Insets(5));
        contentContainer.setSpacing(5);

        ScrollPane scrollPane = new ScrollPane(contentContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);

        saveButton.setPrefWidth(330);
        saveButton.setPrefHeight(50);
        multipleSPAccounts.setMinHeight(400);
        multipleSPAccounts.setPrefHeight(400);
        multipleSPAccounts.setPromptText(resolveBulkHintPrompt());

        parseModeComboBox.getItems().addAll(TextParserService.MODE_STALKER, TextParserService.MODE_XTREME, TextParserService.MODE_M3U);
        parseModeComboBox.setValue(TextParserService.MODE_STALKER);

        groupAccountsCheckBox.setSelected(true);
        convertM3uToXtremeCheckBox.setSelected(true);
        startVerificationAfterParsingCheckBox.setSelected(true);

        groupAccountsCheckBox.managedProperty().bind(groupAccountsCheckBox.visibleProperty());
        convertM3uToXtremeCheckBox.managedProperty().bind(convertM3uToXtremeCheckBox.visibleProperty());

        Region spacer = new Region();
        spacer.setPrefHeight(10);

        HBox parseSaveRow = new HBox(5, saveButton);
        HBox clearRow = new HBox(5, clearButton);
        contentContainer.getChildren().addAll(
                multipleSPAccounts,
                parseModeComboBox,
                spacer,
                groupAccountsCheckBox,
                convertM3uToXtremeCheckBox,
                startVerificationAfterParsingCheckBox,
                parseSaveRow,
                clearRow
        );
        addSubmitButtonClickHandler();
        addClearButtonClickHandler();
        addCheckBoxListeners();
        registerSceneCleanupListener();

        // Initial state
        updateCheckboxesVisibility(TextParserService.MODE_STALKER);
    }

    private void registerSceneCleanupListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                releaseTransientState();
            }
        });
    }

    private void releaseTransientState() {
        // Clear all UI components to allow garbage collection
        contentContainer.getChildren().clear();
        multipleSPAccounts.clear();
    }

    private void addCheckBoxListeners() {
        parseModeComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                updateCheckboxesVisibility(newV);
            }
        });
    }

    private void updateCheckboxesVisibility(String mode) {
        boolean showGroup = TextParserService.MODE_STALKER.equals(mode) || TextParserService.MODE_XTREME.equals(mode);
        groupAccountsCheckBox.setVisible(showGroup);
        if (TextParserService.MODE_XTREME.equals(mode)) {
            groupAccountsCheckBox.setText(I18n.tr(GROUP_BY_XTREME_LABEL));
        } else {
            groupAccountsCheckBox.setText(I18n.tr(GROUP_BY_MAC_LABEL));
        }
        convertM3uToXtremeCheckBox.setVisible(TextParserService.MODE_M3U.equals(mode));
    }

    private void addClearButtonClickHandler() {
        clearButton.setOnAction(event -> clearAll());
    }

    private void clearAll() {
        multipleSPAccounts.clear();
        parseModeComboBox.setValue(TextParserService.MODE_STALKER);
        groupAccountsCheckBox.setSelected(true);
        convertM3uToXtremeCheckBox.setSelected(true);
        startVerificationAfterParsingCheckBox.setSelected(true);
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(_ -> {
            try {
                if (isBlank(multipleSPAccounts.getText())) {
                    showErrorAlert(I18n.tr("autoInputCannotBeEmpty"));
                    return;
                }
                String selectedMode = parseModeComboBox.getValue();
                boolean startVerificationAfterParsing = startVerificationAfterParsingCheckBox.isSelected();
                List<Account> createdAccounts = TextParserService.saveBulkAccounts(multipleSPAccounts.getText(), selectedMode, groupAccountsCheckBox.isSelected(), convertM3uToXtremeCheckBox.isSelected());
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
        Stage owner = getScene() != null && getScene().getWindow() instanceof Stage stage
                ? stage
                : null;
        ReloadCachePopup.showPopup(owner, accountsToVerify, this::notifyAccountsChanged);
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
