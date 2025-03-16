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
    private final CheckBox parsePlaylistCheckBox = new CheckBox("Find m3u playlist only");
    private final CheckBox findXtremeAccountsPlaylistCheckBox = new CheckBox("Where possible convert to m3u playlist to Xtreme account");
    private final CheckBox pauseCachingCheckBox = new CheckBox("Pause account caching");
    private final CheckBox collateAccountsCheckBox = new CheckBox("Group account(s) by MAC Address");
    private final ProminentButton saveButton = new ProminentButton("Parse Text");
    private final Button clearButton = new Button("Clear Data");
    private Callback onSaveCallback;

    public ParseMultipleAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(new Insets(5, 5, 5, 5));
        setSpacing(5);
        saveButton.setPrefWidth(330);
        saveButton.setPrefHeight(50);
        multipleSPAccounts.setMinHeight(400);
        multipleSPAccounts.setPrefHeight(400);
        pauseCachingCheckBox.setSelected(true);
        collateAccountsCheckBox.setSelected(true);

        HBox buttonWrapper2 = new HBox(10, clearButton, saveButton);
        getChildren().addAll(multipleSPAccounts, parsePlaylistCheckBox, findXtremeAccountsPlaylistCheckBox, pauseCachingCheckBox, collateAccountsCheckBox, buttonWrapper2);
        addSubmitButtonClickHandler();
        addClearButtonClickHandler();
    }

    private void addClearButtonClickHandler() {
        clearButton.setOnAction(event -> clearAll());
    }

    private void clearAll() {
        multipleSPAccounts.clear();
        findXtremeAccountsPlaylistCheckBox.setSelected(false);
        parsePlaylistCheckBox.setSelected(false);
        pauseCachingCheckBox.setSelected(true);
        collateAccountsCheckBox.setSelected(true);
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (isBlank(multipleSPAccounts.getText())) {
                    showErrorAlert("Data cannot be empty");
                    return;
                }
                StalkerPortalTextParserService.saveBulkAccounts(multipleSPAccounts.getText(), pauseCachingCheckBox.isSelected(), collateAccountsCheckBox.isSelected(), parsePlaylistCheckBox.isSelected(), findXtremeAccountsPlaylistCheckBox.isSelected());
                clearAll();
                onSaveCallback.call(null);
                showMessageAlert("Account(s) details have been parsed and saved successfully!");
            } catch (Exception e) {
                showErrorAlert("An error has occurred while parsing or saving accounts!");
            }
        });
    }
}