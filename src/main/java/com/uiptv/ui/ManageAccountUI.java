package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;
import static com.uiptv.util.StringUtils.*;
import static com.uiptv.widget.DialogAlert.showDialog;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ManageAccountUI extends VBox {
    public static final String PRIMARY_MAC_ADDRESS_HINT = "Primary MAC Address";
    private String accountId;
    final FileChooser fileChooser = new FileChooser();
    private final ComboBox accountType = new ComboBox();
    private final UIptvText name = new UIptvText("name", "Account Name (must be unique)", 5);
    private final UIptvText username = new UIptvText("username", "User Name", 5);
    private final UIptvText password = new UIptvText("password", "Password", 5);
    private final UIptvText url = new UIptvText("url", "URL", 5);
    private final UIptvText epg = new UIptvText("epg", "EPG", 5);
    private final UIptvCombo macAddress = new UIptvCombo("macAddress", PRIMARY_MAC_ADDRESS_HINT, 350);
    private final UIptvTextArea macAddressList = new UIptvTextArea("macAddress", "Your Comma separated MAC Addresses.", 5);
    private final UIptvText serialNumber = new UIptvText("serialNumber", "Serial Number (SN)", 5);
    private final UIptvText deviceId1 = new UIptvText("deviceId1", "Device ID 1", 5);
    private final UIptvText deviceId2 = new UIptvText("deviceId2", "Device ID 2", 5);
    private final UIptvText signature = new UIptvText("signature", "Signature", 5);
    private final CheckBox pauseCachingCheckBox = new CheckBox("Pause Account Caching");
    private final CheckBox pinToTopCheckBox = new CheckBox("Pin Account on Top");


    final Button browserButtonM3u8Path = new Button("Browse...");
    final UIptvText m3u8Path = new UIptvText("m3u8Path", "M3u8 file path/url", 5);

    private final ProminentButton saveButton = new ProminentButton("Save");
    private final DangerousButton deleteAllButton = new DangerousButton("Delete All");
    private final DangerousButton deleteButton = new DangerousButton("Delete");
    private final Button clearButton = new Button("Clear Data");
    AccountService service = AccountService.getInstance();
    private Callback onSaveCallback;

    public ManageAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        saveButton.setMinWidth(440);
        saveButton.setPrefWidth(440);
        saveButton.setMinHeight(50);
        saveButton.setPrefHeight(50);
        m3u8Path.setMinWidth(180);
        accountType.setMinWidth(250);

        clearButton.setMinWidth(140);
        clearButton.setPrefWidth(140);
        deleteButton.setMinWidth(140);
        deleteButton.setPrefWidth(140);
        deleteAllButton.setMinWidth(140);
        deleteAllButton.setPrefWidth(140);
        macAddressList.textProperty().addListener((observable, oldVal, newVal) -> {
            setupMacAddressByList(newVal);
        });
        HBox buttonWrapper2 = new HBox(10, clearButton, deleteButton, deleteAllButton);
        getChildren().addAll(accountType, name, url, macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, pauseCachingCheckBox, pinToTopCheckBox, saveButton, buttonWrapper2);
        addSubmitButtonClickHandler();
        addDeleteAllButtonClickHandler();
        addDeleteButtonClickHandler();
        addClearButtonClickHandler();
        addBrowserButton1ClickHandler();
        accountType.getItems().addAll(Arrays.stream(AccountType.values()).map(AccountType::getDisplay).toList());
        accountType.setValue(STALKER_PORTAL.getDisplay());
        accountType.valueProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
            if (newValue != null) {
                Platform.runLater(() -> {
                    getChildren().clear();
                    switch (getAccountTypeByDisplay(newValue)) {
                        case STALKER_PORTAL:
                            getChildren().addAll(accountType, name, url, macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, pauseCachingCheckBox, pinToTopCheckBox, saveButton, buttonWrapper2);
                            break;
                        case M3U8_LOCAL:
                            getChildren().addAll(accountType, name, m3u8Path, browserButtonM3u8Path, pauseCachingCheckBox, pinToTopCheckBox, saveButton, buttonWrapper2);
                            break;
                        case M3U8_URL:
                        case RSS_FEED:
                            getChildren().addAll(accountType, name, m3u8Path, epg, pauseCachingCheckBox, pinToTopCheckBox, saveButton, buttonWrapper2);
                            break;
                        case XTREME_API:
                            getChildren().addAll(accountType, name, m3u8Path, username, password, epg, pauseCachingCheckBox, pinToTopCheckBox, saveButton, buttonWrapper2);
                            break;
                    }
                });
            }
        });
    }

    private void setupMacAddressByList(String newVal) {
        if (isBlank(newVal)) {
            return;
        }
        macAddress.getItems().clear();
        List<String> items = new ArrayList<>(Arrays.stream(newVal.replace(SPACE, "").split(",")).toList());
        Collections.sort(items);
        macAddress.getItems().addAll(items);
        if (macAddress.getValue() == null || isBlank(macAddress.getValue().toString()) || !macAddress.getValue().toString().toLowerCase().contains(newVal.toLowerCase())) {
            macAddress.setValue(newVal.split(",")[0].trim());
        } else {
            macAddress.setValue(macAddress.getValue().toString());
        }
    }

    private void addBrowserButton1ClickHandler() {
        browserButtonM3u8Path.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
            m3u8Path.setText(file.getAbsolutePath());
        });
    }

    private void addClearButtonClickHandler() {
        clearButton.setOnAction(event -> clearAll());
    }

    private void clearAll() {
        Arrays.stream(new UIptvText[]{name, username, password, url, serialNumber, deviceId1, deviceId2, signature, m3u8Path, epg}).forEach(TextInputControl::clear);
        macAddressList.clear();
        macAddress.getItems().clear();
        macAddress.setValue(null);
        macAddress.setPromptText(PRIMARY_MAC_ADDRESS_HINT);
        accountType.setValue(STALKER_PORTAL.getDisplay());
        pauseCachingCheckBox.setSelected(false);
        pinToTopCheckBox.setSelected(false);
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (isBlank(name.getText())) {
                    showErrorAlert("Name cannot be empty");
                    return;
                }
                service.save(new Account(name.getText(), username.getText(), password.getText(), url.getText(),
                        macAddress.getValue() != null ? macAddress.getValue().toString() : "", macAddressList.getText(), serialNumber.getText(), deviceId1.getText(), deviceId2.getText(), signature.getText(),
                        getAccountTypeByDisplay(accountType.getValue() != null && isNotBlank(accountType.getValue().toString()) ? accountType.getValue().toString() : AccountType.STALKER_PORTAL.getDisplay()), epg.getText(), m3u8Path.getText(), pauseCachingCheckBox.isSelected(), pinToTopCheckBox.isSelected()));
                clearAll();
                showMessageAlert("Your Account details have been successfully saved!");
                onSaveCallback.call(null);
            } catch (Exception e) {
                showErrorAlert("Failed to save successfully saved!");
            }
        });
    }

    private void addDeleteAllButtonClickHandler() {
        deleteAllButton.setOnAction(actionEvent -> {
            Alert confirmDialogue1 = showDialog("Delete All accounts?");
            if (confirmDialogue1.getResult() == ButtonType.YES) {
                Alert confirmDialogueFinal = showDialog("Final Chance to abort. Press Yes button to delete all accounts?");
                if (confirmDialogueFinal.getResult() == ButtonType.YES) {
                    try {
                        service.deleteAll();
                        onSaveCallback.call(null);
                        clearAll();
                    } catch (Exception e) {
                        showErrorAlert("Failed!");
                    }
                }
            }
        });
    }

    private void addDeleteButtonClickHandler() {
        deleteButton.setOnAction(actionEvent -> {
            deleteAccount(name.getText(), accountId);
        });
    }

    private void deleteAccount(String name, String accountId) {
        if (isBlank(name) || isBlank(accountId)) {
            return;
        }
        Alert confirmDialogue = showDialog("Delete This account " + name + "?");
        if (confirmDialogue.getResult() == ButtonType.YES) {
            try {
                service.delete(accountId);
                onSaveCallback.call(null);
                clearAll();
            } catch (Exception e) {
                showErrorAlert("Failed!");
            }
        }
    }

    public void deleteAccount(Account account) {
        if (account == null) return;
        deleteAccount(account.getAccountName(), account.getDbId());
    }

    public void editAccount(Account account) {
        accountId = account.getDbId();
        name.setText(account.getAccountName());
        username.setText(account.getUsername());
        password.setText(account.getPassword());
        url.setText(account.getUrl());
        macAddressList.setText(account.getMacAddressList());
        if (account.getType() == STALKER_PORTAL) {
            setupMacAddressByList(account.getMacAddressList());
        }
        macAddress.setValue(account.getMacAddress());
        serialNumber.setText(account.getSerialNumber());
        deviceId1.setText(account.getDeviceId1());
        deviceId2.setText(account.getDeviceId2());
        signature.setText(account.getSignature());
        epg.setText(account.getEpg());
        m3u8Path.setText(account.getM3u8Path());
        pauseCachingCheckBox.setSelected(account.isPauseCaching());
        pinToTopCheckBox.setSelected(account.isPinToTop());
        accountType.setValue(account.getType().getDisplay());
    }
}