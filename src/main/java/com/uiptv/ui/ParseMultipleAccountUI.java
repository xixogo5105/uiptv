package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvTextArea;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ParseMultipleAccountUI extends VBox {
    private final String newLine = "\r" + System.lineSeparator();

    private final UIptvTextArea multipleSPAccounts = new UIptvTextArea("multipleSPAccounts", "Enter Data to parse multiple stalker portal accounts" + newLine + "e.g." + newLine + "http://website1.com/c" + newLine + "00:00:00:00:00:00" + newLine + "http://website2.com/c" + newLine + "00:00:00:00:00:00" + newLine + "http://website3.com/c" + newLine + "00:00:00:00:00:00" + newLine + "00:00:00:00:00:01" + newLine + "00:00:00:00:00:02" + newLine + "http://website4.com/c" + newLine + "00:00:00:00:00:00" + newLine + "00:00:00:00:00:01" + newLine + "00:00:00:00:00:02", 5);
    private final CheckBox pauseCachingCheckBox = new CheckBox("Pause account caching");
    private final ProminentButton saveButton = new ProminentButton("Parse Text");
    private final Button clearButton = new Button("Clear Data");
    AccountService service = AccountService.getInstance();
    private Callback onSaveCallback;

    public ParseMultipleAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(new Insets(10, 10, 10, 10));
        setSpacing(10);
//        saveButton.setMinWidth(400);
        saveButton.setPrefWidth(330);
        saveButton.setPrefHeight(50);
        multipleSPAccounts.setMinHeight(400);
        multipleSPAccounts.setPrefHeight(400);
        HBox buttonWrapper2 = new HBox(10, clearButton, saveButton);
        getChildren().addAll(multipleSPAccounts, pauseCachingCheckBox, buttonWrapper2);
        addSubmitButtonClickHandler();
        addClearButtonClickHandler();
    }

    private void addClearButtonClickHandler() {
        clearButton.setOnAction(event -> clearAll());
    }

    private void clearAll() {
        multipleSPAccounts.clear();
        pauseCachingCheckBox.setSelected(false);
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (isBlank(multipleSPAccounts.getText())) {
                    showErrorAlert("Data cannot be empty");
                    return;
                }
                parse(multipleSPAccounts.getText());
                clearAll();
                onSaveCallback.call(null);
                showMessageAlert("Account(s) details have been parsed and saved successfully!");
            } catch (Exception e) {
                showErrorAlert("An error has occured while parsing or saving accounts!");
            }
        });
    }

    private void parse(String data) {
        String[] lines = data.split("\\R");
        String lastAccountUrl = null;
        for (String uncleanLine : lines) {
            if (isBlank(uncleanLine) || isBlank(uncleanLine.trim())) continue;
            String line = uncleanLine.trim();
            if (isValidURL(line)) {
                lastAccountUrl = line;
            } else if (isNotBlank(lastAccountUrl) && isValidMACAddress(line)) {
                service.save(new Account(getNameFromUrl(lastAccountUrl), null, null, lastAccountUrl, line, null, null, null, null,
                        AccountType.STALKER_PORTAL, null, null, pauseCachingCheckBox.isSelected()));
            }
        }
    }

    public static boolean isValidURL(String urlString) {
        try {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isValidMACAddress(String line) {
        if (isBlank(line)) return false;
        String regex = "^([0-9A-Fa-f]{2}[:-])"
                + "{5}([0-9A-Fa-f]{2})|"
                + "([0-9a-fA-F]{4}\\."
                + "[0-9a-fA-F]{4}\\."
                + "[0-9a-fA-F]{4})$";

        return Pattern.compile(regex).matcher(line).matches();
    }

    public static String getNameFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            URI uri = url.toURI();
            int i = 1;
            String validName;
            do {
                validName = uri.getHost() + " (" + i++ + ")";
            } while (AccountService.getInstance().getByName(validName) != null);
            return validName;
        } catch (Exception ignored) {
        }
        return urlString;
    }
}