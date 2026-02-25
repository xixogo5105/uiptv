package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.util.TextParserService;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvTextArea;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ParseMultipleAccountUI extends VBox {
    private final String newLine = "\r" + System.lineSeparator();

    private final UIptvTextArea multipleSPAccounts = new UIptvTextArea("multipleSPAccounts", "Enter Data to parse multiple stalker portal accounts" + newLine + "e.g." + newLine + "http://website1.com/c" + newLine + "00:00:00:00:00:00" + newLine + "http://website2.com/c" + newLine + "00:00:00:00:00:00" + newLine + "http://website3.com/c" + newLine + "00:00:00:00:00:00" + newLine + "00:00:00:00:00:01" + newLine + "00:00:00:00:00:02" + newLine + "http://website4.com/c" + newLine + "00:00:00:00:00:00" + newLine + "00:00:00:00:00:01" + newLine + "00:00:00:00:00:02" + newLine + "For M3U play list use the following format:" + newLine + "http://somewebsiteurl.iptv:8080/get.php?username=username&password=password&type=m3u" + newLine + "http://somewebsiteurl2.iptv:8080/get.php.iptv:8080/get.php?username=username2&password=password2&type=m3u", 5);
    private final ComboBox<String> parseModeComboBox = new ComboBox<>();
    private final CheckBox groupAccountsCheckBox = new CheckBox("Group Account(s) by MAC Address");
    private final CheckBox convertM3uToXtremeCheckBox = new CheckBox("Where Possible, Convert M3U to Xtreme");
    private final CheckBox startVerificationAfterParsingCheckBox = new CheckBox("Start verification after parsing");
    private final ProminentButton saveButton = new ProminentButton("Parse & Save");
    private final Button clearButton = new Button("Clear");
    private Callback<Void> onSaveCallback;

    public ParseMultipleAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback<Void> onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        saveButton.setPrefWidth(330);
        saveButton.setPrefHeight(50);
        multipleSPAccounts.setMinHeight(400);
        multipleSPAccounts.setPrefHeight(400);

        parseModeComboBox.getItems().addAll(TextParserService.MODE_STALKER, TextParserService.MODE_XTREME, TextParserService.MODE_M3U);
        parseModeComboBox.setValue(TextParserService.MODE_STALKER);

        groupAccountsCheckBox.setSelected(true);
        convertM3uToXtremeCheckBox.setSelected(true);
        startVerificationAfterParsingCheckBox.setSelected(true);

        groupAccountsCheckBox.managedProperty().bind(groupAccountsCheckBox.visibleProperty());
        convertM3uToXtremeCheckBox.managedProperty().bind(convertM3uToXtremeCheckBox.visibleProperty());

        Region spacer = new Region();
        spacer.setPrefHeight(10);

        HBox buttonWrapper2 = new HBox(10, clearButton, saveButton);
        getChildren().addAll(multipleSPAccounts, parseModeComboBox, spacer, groupAccountsCheckBox, convertM3uToXtremeCheckBox, startVerificationAfterParsingCheckBox, buttonWrapper2);
        addSubmitButtonClickHandler();
        addClearButtonClickHandler();
        addCheckBoxListeners();

        // Initial state
        updateCheckboxesVisibility(TextParserService.MODE_STALKER);
    }

    private void addCheckBoxListeners() {
        parseModeComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                updateCheckboxesVisibility(newV);
            }
        });
    }

    private void updateCheckboxesVisibility(String mode) {
        groupAccountsCheckBox.setVisible(TextParserService.MODE_STALKER.equals(mode));
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
        saveButton.setOnAction(actionEvent -> {
            try {
                if (isBlank(multipleSPAccounts.getText())) {
                    showErrorAlert("Input cannot be empty.");
                    return;
                }
                String selectedMode = parseModeComboBox.getValue();
                boolean startVerificationAfterParsing = startVerificationAfterParsingCheckBox.isSelected();
                List<Account> createdAccounts = TextParserService.saveBulkAccounts(multipleSPAccounts.getText(), selectedMode, groupAccountsCheckBox.isSelected(), convertM3uToXtremeCheckBox.isSelected());
                clearAll();
                if (onSaveCallback != null) {
                    onSaveCallback.call(null);
                }
                showMessageAlert("Accounts parsed and saved.");
                if (startVerificationAfterParsing && createdAccounts != null && !createdAccounts.isEmpty()) {
                    openVerificationPopup(createdAccounts);
                }
            } catch (Exception e) {
                showErrorAlert("Error parsing or saving accounts.");
            }
        });
    }

    private void openVerificationPopup(List<Account> accountsToVerify) {
        Stage owner = getScene() != null && getScene().getWindow() instanceof Stage
                ? (Stage) getScene().getWindow()
                : null;
        ReloadCachePopup.showPopup(owner, accountsToVerify);
    }
}
