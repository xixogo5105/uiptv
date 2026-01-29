package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.util.StalkerPortalTextParserService;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvTextArea;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ParseMultipleAccountUI extends VBox {
    private final String newLine = "\r" + System.lineSeparator();

    private final UIptvTextArea multipleSPAccounts = new UIptvTextArea("multipleSPAccounts", "Enter Data to parse multiple stalker portal accounts" + newLine + "e.g." + newLine + "http://website1.com/c" + newLine + "00:00:00:00:00:00" + newLine + "http://website2.com/c" + newLine + "00:00:00:00:00:00" + newLine + "http://website3.com/c" + newLine + "00:00:00:00:00:00" + newLine + "00:00:00:00:00:01" + newLine + "00:00:00:00:00:02" + newLine + "http://website4.com/c" + newLine + "00:00:00:00:00:00" + newLine + "00:00:00:00:00:01" + newLine + "00:00:00:00:00:02" + newLine + "For M3U play list use the following format:" + newLine + "http://somewebsiteurl.iptv:8080/get.php?username=username&password=password&type=m3u" + newLine + "http://somewebsiteurl2.iptv:8080/get.php.iptv:8080/get.php?username=username2&password=password2&type=m3u", 5);
    private final CheckBox collateAccountsCheckBox = new CheckBox("Where possible, Group Account(s) by MAC Address");
    private final CheckBox parsePlaylistCheckBox = new CheckBox("Parse M3U Playlists Only");
    private final CheckBox findXtremeAccountsPlaylistCheckBox = new CheckBox("Where Possible, Convert M3U to Xtreme");
    private final CheckBox parseXtremeAccountsCheckBox = new CheckBox("Parse Xtreme Accounts Only");
    private final CheckBox pauseCachingCheckBox = new CheckBox("Pause Caching");
    private final ProminentButton saveButton = new ProminentButton("Parse & Save");
    private final Button clearButton = new Button("Clear");
    private Callback onSaveCallback;

    public ParseMultipleAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        saveButton.setPrefWidth(330);
        saveButton.setPrefHeight(50);
        multipleSPAccounts.setMinHeight(400);
        multipleSPAccounts.setPrefHeight(400);
        pauseCachingCheckBox.setSelected(true);
        collateAccountsCheckBox.setSelected(true);

        HBox buttonWrapper2 = new HBox(10, clearButton, saveButton);
        getChildren().addAll(multipleSPAccounts, collateAccountsCheckBox, parsePlaylistCheckBox, findXtremeAccountsPlaylistCheckBox, parseXtremeAccountsCheckBox, pauseCachingCheckBox, buttonWrapper2);
        addSubmitButtonClickHandler();
        addClearButtonClickHandler();
        addCheckBoxListeners();
    }

    private void addCheckBoxListeners() {
        // Grouping: collate, m3u, xtreme are mutually exclusive modes.
        collateAccountsCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                parsePlaylistCheckBox.setSelected(false);
                parseXtremeAccountsCheckBox.setSelected(false);
            }
        });

        parsePlaylistCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                collateAccountsCheckBox.setSelected(false);
                parseXtremeAccountsCheckBox.setSelected(false);
            }
            // The "Convert" checkbox is only active when "Parse M3U" is.
            findXtremeAccountsPlaylistCheckBox.setDisable(!newV);
            if (!newV) {
                findXtremeAccountsPlaylistCheckBox.setSelected(false);
            }
        });

        parseXtremeAccountsCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                collateAccountsCheckBox.setSelected(false);
                parsePlaylistCheckBox.setSelected(false);
            }
        });

        // Initial state
        findXtremeAccountsPlaylistCheckBox.setDisable(!parsePlaylistCheckBox.isSelected());
    }

    private void addClearButtonClickHandler() {
        clearButton.setOnAction(event -> clearAll());
    }

    private void clearAll() {
        multipleSPAccounts.clear();
        // Reset checkboxes to default state
        collateAccountsCheckBox.setSelected(true);
        parsePlaylistCheckBox.setSelected(false);
        findXtremeAccountsPlaylistCheckBox.setSelected(false);
        parseXtremeAccountsCheckBox.setSelected(false);
        pauseCachingCheckBox.setSelected(true);
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (isBlank(multipleSPAccounts.getText())) {
                    showErrorAlert("Input cannot be empty.");
                    return;
                }
                StalkerPortalTextParserService.saveBulkAccounts(multipleSPAccounts.getText(), pauseCachingCheckBox.isSelected(), collateAccountsCheckBox.isSelected(), parsePlaylistCheckBox.isSelected(), findXtremeAccountsPlaylistCheckBox.isSelected(), parseXtremeAccountsCheckBox.isSelected());
                clearAll();
                onSaveCallback.call(null);
                showMessageAlert("Accounts parsed and saved.");
            } catch (Exception e) {
                showErrorAlert("Error parsing or saving accounts.");
            }
        });
    }
}
