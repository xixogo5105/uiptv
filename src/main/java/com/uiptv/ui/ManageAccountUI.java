package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.widget.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;
import static com.uiptv.util.StringUtils.*;
import static com.uiptv.widget.DialogAlert.showDialog;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ManageAccountUI extends VBox {
    public static final String PRIMARY_MAC_ADDRESS_HINT = "Primary MAC Address";
    final FileChooser fileChooser = new FileChooser();
    final Button browserButtonM3u8Path = new Button("Browse...");
    final UIptvText m3u8Path = new UIptvText("m3u8Path", "M3u8 file path/url", 5);
    private final ComboBox accountType = new ComboBox();
    private final UIptvText name = new UIptvText("name", "Account Name (must be unique)", 5);
    private final UIptvText username = new UIptvText("username", "User Name", 5);
    private final UIptvText password = new UIptvText("password", "Password", 5);
    private final UIptvText url = new UIptvText("url", "URL", 5);
    private final UIptvText epg = new UIptvText("epg", "EPG", 5);
    private final UIptvCombo macAddress = new UIptvCombo("macAddress", PRIMARY_MAC_ADDRESS_HINT, 350);
    private final UIptvTextArea macAddressList = new UIptvTextArea("macAddress", "Your Comma separated MAC Addresses.", 5);
    private final Hyperlink verifyMacsLink = new Hyperlink("Verify");
    private final Hyperlink manageMacsLink = new Hyperlink("Manage");
    private final Label pipeLabel = new Label("|");
    private final UIptvText serialNumber = new UIptvText("serialNumber", "Serial Number (SN)", 5);
    private final UIptvText deviceId1 = new UIptvText("deviceId1", "Device ID 1", 5);
    private final UIptvText deviceId2 = new UIptvText("deviceId2", "Device ID 2", 5);
    private final UIptvText signature = new UIptvText("signature", "Signature", 5);
    private final CheckBox pinToTopCheckBox = new CheckBox("Pin Account on Top");
    private final ProminentButton saveButton = new ProminentButton("Save");
    private final DangerousButton deleteAllButton = new DangerousButton("Delete All");
    private final DangerousButton deleteButton = new DangerousButton("Delete");
    private final Button clearButton = new Button("Clear Data");
    private final Button refreshChannelsButton = new Button("Reload Cache");
    private final CacheService cacheService = new CacheServiceImpl();
    AccountService service = AccountService.getInstance();
    private String accountId;
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
        refreshChannelsButton.setMinWidth(440);
        refreshChannelsButton.setPrefWidth(440);
        refreshChannelsButton.setMinHeight(50);
        refreshChannelsButton.setPrefHeight(50);
        m3u8Path.setMinWidth(180);
        accountType.setMinWidth(250);
        macAddress.setPrefWidth(280); // Reduced by 20% from 350

        clearButton.setMinWidth(140);
        clearButton.setPrefWidth(140);
        deleteButton.setMinWidth(140);
        deleteButton.setPrefWidth(140);
        deleteAllButton.setMinWidth(140);
        deleteAllButton.setPrefWidth(140);

        macAddressList.textProperty().addListener((observable, oldVal, newVal) -> {
            setupMacAddressByList(newVal);
        });

        verifyMacsLink.setVisible(false);
        verifyMacsLink.setOnAction(event -> verifyMacAddresses());

        manageMacsLink.setVisible(false);
        manageMacsLink.setOnAction(event -> openManageMacsPopup());

        pipeLabel.visibleProperty().bind(verifyMacsLink.visibleProperty());
        manageMacsLink.visibleProperty().bind(verifyMacsLink.visibleProperty());

        HBox macAddressContainer = new HBox(5, macAddress, verifyMacsLink, pipeLabel, manageMacsLink);
        macAddressContainer.setAlignment(Pos.CENTER_LEFT);

        HBox buttonWrapper2 = new HBox(10, clearButton, deleteButton, deleteAllButton);
        getChildren().addAll(accountType, name, url, macAddressContainer, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, pinToTopCheckBox, refreshChannelsButton, saveButton, buttonWrapper2);
        addSubmitButtonClickHandler();
        addDeleteAllButtonClickHandler();
        addDeleteButtonClickHandler();
        addClearButtonClickHandler();
        addRefreshChannelsButtonClickHandler();
        addBrowserButton1ClickHandler();
        accountType.getItems().addAll(Arrays.stream(AccountType.values()).map(AccountType::getDisplay).toList());
        accountType.setValue(STALKER_PORTAL.getDisplay());
        accountType.valueProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
            if (newValue != null) {
                Platform.runLater(() -> {
                    getChildren().clear();
                    switch (getAccountTypeByDisplay(newValue)) {
                        case STALKER_PORTAL:
                            getChildren().addAll(accountType, name, url, macAddressContainer, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, pinToTopCheckBox, refreshChannelsButton, saveButton, buttonWrapper2);
                            break;
                        case M3U8_LOCAL:
                            getChildren().addAll(accountType, name, m3u8Path, browserButtonM3u8Path, pinToTopCheckBox, refreshChannelsButton, saveButton, buttonWrapper2);
                            break;
                        case M3U8_URL:
                        case RSS_FEED:
                            getChildren().addAll(accountType, name, m3u8Path, epg, pinToTopCheckBox, refreshChannelsButton, saveButton, buttonWrapper2);
                            break;
                        case XTREME_API:
                            getChildren().addAll(accountType, name, m3u8Path, username, password, epg, pinToTopCheckBox, refreshChannelsButton, saveButton, buttonWrapper2);
                            break;
                    }
                });
            }
        });
    }

    private void openManageMacsPopup() {
        String currentMacs = macAddressList.getText();
        List<String> macList = new ArrayList<>();
        if (isNotBlank(currentMacs)) {
            macList.addAll(Arrays.stream(currentMacs.replace(SPACE, "").split(",")).toList());
        }

        String currentDefault = macAddress.getValue() != null ? macAddress.getValue().toString() : null;

        MacAddressManagementPopup popup = new MacAddressManagementPopup((Stage) getScene().getWindow(), macList, currentDefault, (newMacs, newDefault) -> {
            String newMacsStr = String.join(", ", newMacs);
            macAddressList.setText(newMacsStr);

            Platform.runLater(() -> {
                if (newDefault != null && macAddress.getItems().contains(newDefault)) {
                    macAddress.setValue(newDefault);
                }
                saveAccount(false);
            });
        });
        popup.show();
    }

    private void verifyMacAddresses() {
        String macs = macAddressList.getText();
        if (isBlank(macs)) {
            showErrorAlert("No MAC addresses to verify.");
            return;
        }

        List<String> macList = new ArrayList<>(Arrays.stream(macs.replace(SPACE, "").split(",")).toList());
        if (macList.isEmpty()) return;

        ProgressDialog progressDialog = new ProgressDialog((Stage) getScene().getWindow());
        progressDialog.show();

        AtomicBoolean stopRequested = new AtomicBoolean(false);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> invalidMacs = new ArrayList<>();
                int total = macList.size();
                progressDialog.setTotal(total);
                Account accountToVerify = service.getById(accountId);

                for (int i = 0; i < total; i++) {
                    if (isCancelled() || stopRequested.get()) break;

                    String mac = macList.get(i);
                    progressDialog.addProgressText("Verifying (" + (i + 1) + "/" + total + "): " + mac + "...");

                    boolean isValid = cacheService.verifyMacAddress(accountToVerify, mac);
                    progressDialog.addResult(isValid);

                    if (isValid) {
                        progressDialog.addProgressText("Result: [VALID]VALID");
                    } else {
                        invalidMacs.add(mac);
                        progressDialog.addProgressText("Result: [INVALID]INVALID");
                    }

                    if (isCancelled() || stopRequested.get()) break;

                    if (i < total - 1) {
                        long delayMillis = progressDialog.getSelectedDelayMillis();
                        int totalSeconds = (int) (delayMillis / 1000);

                        for (int seconds = totalSeconds; seconds > 0; seconds--) {
                            if (isCancelled() || stopRequested.get()) break;
                            progressDialog.setPauseStatus(seconds, totalSeconds);
                            Thread.sleep(1000);
                        }
                        progressDialog.setPauseStatus(0, 0);
                    }
                }
                return invalidMacs;
            }
        };

        progressDialog.setOnClose(task::cancel);
        progressDialog.setOnStop(() -> stopRequested.set(true));

        task.setOnSucceeded(e -> {
            progressDialog.close();
            List<String> invalidMacs = task.getValue();
            handleVerificationResults(macList, invalidMacs, stopRequested.get());
        });

        task.setOnFailed(e -> {
            progressDialog.close();
            showErrorAlert("Verification failed: " + task.getException().getMessage());
        });

        task.setOnCancelled(e -> {
            progressDialog.close();
            showMessageAlert("Verification cancelled.");
        });

        new Thread(task).start();
    }

    private void handleVerificationResults(List<String> allMacs, List<String> invalidMacs, boolean wasStopped) {
        if (invalidMacs.isEmpty()) {
            if (!wasStopped) {
                showMessageAlert("All MAC addresses are valid.");
            }
            return;
        }

        if (!wasStopped && invalidMacs.size() == allMacs.size()) {
            Alert alert = showDialog("No valid MAC addresses found. Delete this account?");
            if (alert.getResult() == ButtonType.YES) {
                deleteAccount(name.getText(), accountId);
            }
            return;
        }

        String invalidMacsStr = String.join(", ", invalidMacs);
        StringBuilder message = new StringBuilder("Found invalid MAC addresses: " + invalidMacsStr + "\n");

        String currentDefault = macAddress.getValue() != null ? macAddress.getValue().toString() : "";
        boolean defaultIsInvalid = invalidMacs.contains(currentDefault);

        if (defaultIsInvalid) {
            message.append("\nNote: The default MAC address is invalid and will be removed.\nThe first valid MAC address will be set as the new default.\n");
        }

        message.append("\nDelete them?");
        Alert alert = showDialog(message.toString());
        if (alert.getResult() == ButtonType.YES) {
            List<String> validMacs = new ArrayList<>(allMacs);
            validMacs.removeAll(invalidMacs);
            String newMacsStr = String.join(", ", validMacs);

            if (defaultIsInvalid && !validMacs.isEmpty()) {
                macAddress.setValue(validMacs.get(0));
            }

            macAddressList.setText(newMacsStr);
            saveAccount(false);
        }
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
        clearButton.setOnAction(event -> {
            Alert confirmDialogue = showDialog("Clear all cached data for this account?");
            if (confirmDialogue.getResult() == ButtonType.YES) {
                cacheService.clearCache(getAccountFromForm());
                showMessageAlert("Cache cleared.");
            }
        });
    }

    private void addRefreshChannelsButtonClickHandler() {
        refreshChannelsButton.setOnAction(event -> {
            Account account = getAccountFromForm();
            if (account == null || isBlank(account.getDbId())) {
                showErrorAlert("Please save the account before reloading the cache.");
                return;
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText(null);
            alert.setContentText("Are you sure you want to reload the cache for " + account.getAccountName() + "? This may take a while.");
            if (RootApplication.currentTheme != null) {
                alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
            }
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                LogPopupUI logPopup = new LogPopupUI("Reloading cache. This will take a while...");
                logPopup.show();

                Thread thread = new Thread(() -> {
                    try {
                        cacheService.reloadCache(account, logPopup.getLogger());
                    } catch (IOException e) {
                        Platform.runLater(() -> showErrorAlert("Failed to reload cache: " + e.getMessage()));
                    } finally {
                        logPopup.closeGracefully();
                    }
                });
                logPopup.setOnStop(thread::interrupt);
                thread.start();
            }
        });
    }

    private void clearAll() {
        Arrays.stream(new UIptvText[]{name, username, password, url, serialNumber, deviceId1, deviceId2, signature, m3u8Path, epg}).forEach(TextInputControl::clear);
        macAddressList.clear();
        macAddress.getItems().clear();
        macAddress.setValue(null);
        macAddress.setPromptText(PRIMARY_MAC_ADDRESS_HINT);
        accountType.setValue(STALKER_PORTAL.getDisplay());
        pinToTopCheckBox.setSelected(false);
        verifyMacsLink.setVisible(false);
    }

    private Account getAccountFromForm() {
        Account account = new Account(name.getText(), username.getText(), password.getText(), url.getText(),
                macAddress.getValue() != null ? macAddress.getValue().toString() : "", macAddressList.getText(), serialNumber.getText(), deviceId1.getText(), deviceId2.getText(), signature.getText(),
                getAccountTypeByDisplay(accountType.getValue() != null && isNotBlank(accountType.getValue().toString()) ? accountType.getValue().toString() : AccountType.STALKER_PORTAL.getDisplay()), epg.getText(), m3u8Path.getText(), pinToTopCheckBox.isSelected());
        if (accountId != null) {
            account.setDbId(accountId);
        }
        return account;
    }

    private void saveAccount(boolean isFullSave) {
        try {
            if (isBlank(name.getText())) {
                showErrorAlert("Name cannot be empty");
                return;
            }
            Account account = getAccountFromForm();
            service.save(account);

            if (isFullSave) {
                clearAll();
                showMessageAlert("Your Account details have been successfully saved!");
                onSaveCallback.call(null);
            }
        } catch (Exception e) {
            showErrorAlert("Failed to save successfully saved!");
        }
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> saveAccount(true));
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
            verifyMacsLink.setVisible(true);
        }
        macAddress.setValue(account.getMacAddress());
        serialNumber.setText(account.getSerialNumber());
        deviceId1.setText(account.getDeviceId1());
        deviceId2.setText(account.getDeviceId2());
        signature.setText(account.getSignature());
        epg.setText(account.getEpg());
        m3u8Path.setText(account.getM3u8Path());
        pinToTopCheckBox.setSelected(account.isPinToTop());
        accountType.setValue(account.getType().getDisplay());
    }
}
