package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.UIptvText;
import com.uiptv.widget.DangerousButton;
import com.uiptv.widget.ProminentButton;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;
import static com.uiptv.widget.DialogAlert.showDialog;

public class ManageAccountUI extends VBox {
    private String accountId;
    final FileChooser fileChooser = new FileChooser();
    private final ComboBox accountType = new ComboBox();
    private final UIptvText name = new UIptvText("name", "Enter Name", 5);
    private final UIptvText username = new UIptvText("username", "Enter User Name", 5);
    private final UIptvText password = new UIptvText("password", "Enter Password", 5);
    private final UIptvText url = new UIptvText("url", "Enter URL", 5);
    private final UIptvText epg = new UIptvText("epg", "EPG", 5);
    private final UIptvText macAddress = new UIptvText("macAddress", "Enter MAC ADDRESS", 5);
    private final UIptvText serialNumber = new UIptvText("serialNumber", "Enter Serial Number (SN)", 5);
    private final UIptvText deviceId1 = new UIptvText("deviceId1", "Enter Device ID 1", 5);
    private final UIptvText deviceId2 = new UIptvText("deviceId2", "Enter Device ID 2", 5);
    private final UIptvText signature = new UIptvText("signature", "Enter Signature", 5);
    private final CheckBox pauseCachingCheckBox = new CheckBox("Pause account caching");


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
        setPadding(new Insets(10, 10, 10, 10));
        setSpacing(10);
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


        HBox buttonWrapper2 = new HBox(10, clearButton, deleteButton, deleteAllButton);
        getChildren().addAll(accountType, name, url, macAddress, serialNumber, deviceId1, deviceId2, signature, username, password, pauseCachingCheckBox, saveButton, buttonWrapper2);
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
                            getChildren().addAll(accountType, name, url, macAddress, serialNumber, deviceId1, deviceId2, signature, username, password, pauseCachingCheckBox, saveButton, buttonWrapper2);
                            break;
                        case M3U8_LOCAL:
                            getChildren().addAll(accountType, name, m3u8Path, browserButtonM3u8Path, pauseCachingCheckBox, saveButton, buttonWrapper2);
                            break;
                        case M3U8_URL:
                            getChildren().addAll(accountType, name, m3u8Path, epg, pauseCachingCheckBox, saveButton, buttonWrapper2);
                            break;
                        case XTREME_API:
                            getChildren().addAll(accountType, name, m3u8Path, username, password, epg, pauseCachingCheckBox, saveButton, buttonWrapper2);
                            break;
                    }
                });
            }
        });
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
        Arrays.stream(new UIptvText[]{name, username, password, url, macAddress, serialNumber, deviceId1, deviceId2, signature, m3u8Path, epg}).forEach(TextInputControl::clear);
        accountType.setValue(STALKER_PORTAL.getDisplay());
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (isBlank(name.getText())) {
                    showErrorAlert("Name cannot be empty");
                    return;
                }
                service.save(new Account(name.getText(), username.getText(), password.getText(), url.getText(),
                        macAddress.getText(), serialNumber.getText(), deviceId1.getText(), deviceId2.getText(), signature.getText(),
                        getAccountTypeByDisplay(accountType.getValue() != null && isNotBlank(accountType.getValue().toString()) ? accountType.getValue().toString() : AccountType.STALKER_PORTAL.getDisplay()), epg.getText(), m3u8Path.getText(), pauseCachingCheckBox.isSelected()));
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
            if (isBlank(name.getText())) {
                showErrorAlert("Name cannot be empty");
                return;
            }
            Alert confirmDialogue = showDialog("Delete This account?");
            if (confirmDialogue.getResult() == ButtonType.YES) {
                try {
                    service.delete(accountId);
                    onSaveCallback.call(null);
                    clearAll();
                } catch (Exception e) {
                    showErrorAlert("Failed!");
                }
            }
        });
    }

    public void editAccount(Account account) {
        accountId = account.getDbId();
        name.setText(account.getAccountName());
        username.setText(account.getUsername());
        password.setText(account.getPassword());
        url.setText(account.getUrl());
        macAddress.setText(account.getMacAddress());
        serialNumber.setText(account.getSerialNumber());
        deviceId1.setText(account.getDeviceId1());
        deviceId2.setText(account.getDeviceId2());
        signature.setText(account.getSignature());
        accountType.setValue(account.getType().getDisplay());
        epg.setText(account.getEpg());
        m3u8Path.setText(account.getM3u8Path());
        pauseCachingCheckBox.setSelected(account.isPauseCaching());
    }
}