package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.widget.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.uiptv.model.Account.CACHE_SUPPORTED;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;
import static com.uiptv.util.StringUtils.*;
import static com.uiptv.widget.DialogAlert.showDialog;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ManageAccountUI extends VBox {
    public static final String PRIMARY_MAC_ADDRESS_HINT_KEY = "managePrimaryMacAddressHint";
    private static final String DEFAULT_TIMEZONE = "Europe/London";
    final FileChooser fileChooser = new FileChooser();
    final Button browserButtonM3u8Path = new Button(I18n.tr("autoBrowse"));
    final UIptvText m3u8Path = new UIptvText("m3u8Path", "manageM3u8FilePathUrlPrompt", 5);
    private final ComboBox<String> accountType = new ComboBox<>();
    private final UIptvText name = new UIptvText("name", "manageAccountNameMustBeUniquePrompt", 5);
    private final UIptvText username = new UIptvText("username", "manageUserNamePrompt", 5);
    private final UIptvText password = new UIptvText("password", "managePasswordPrompt", 5);
    private final UIptvText url = new UIptvText("url", "manageUrlPrompt", 5);
    private final UIptvText epg = new UIptvText("epg", "manageEpgPrompt", 5);
    private final UIptvCombo macAddress = new UIptvCombo("macAddress", PRIMARY_MAC_ADDRESS_HINT_KEY, 350);
    private final UIptvTextArea macAddressList = new UIptvTextArea("macAddress", "manageMacAddressListPrompt", 5);
    private final Hyperlink verifyMacsLink = new Hyperlink(I18n.tr("autoVerify"));
    private final Hyperlink manageMacsLink = new Hyperlink(I18n.tr("autoManage"));
    private final Label pipeLabel = new Label("|");
    private final UIptvText serialNumber = new UIptvText("serialNumber", "manageSerialNumberPrompt", 5);
    private final UIptvText deviceId1 = new UIptvText("deviceId1", "manageDeviceId1Prompt", 5);
    private final UIptvText deviceId2 = new UIptvText("deviceId2", "manageDeviceId2Prompt", 5);
    private final UIptvText signature = new UIptvText("signature", "manageSignaturePrompt", 5);
    private final CheckBox pinToTopCheckBox = new CheckBox(I18n.tr("autoPinAccountOnTop"));
    private final UIptvCombo httpMethodCombo = new UIptvCombo("httpMethod", "manageHttpMethodPrompt", 150);
    private final UIptvCombo timezoneCombo = new UIptvCombo("timezone", "manageTimezonePrompt", 250);
    private final ProminentButton saveButton = new ProminentButton(I18n.tr("commonSave"));
    private final DangerousButton deleteButton = new DangerousButton(I18n.tr("autoDeleteAccount"));
    private final Button clearButton = new Button(I18n.tr("autoClearData"));
    private final Button refreshChannelsButton = new Button(I18n.tr("autoReloadCache"));
    private final CacheService cacheService = new CacheServiceImpl();
    private final VBox formContainer = new VBox();
    AccountService service = AccountService.getInstance();
    private String accountId;
    private Callback<Object> onSaveCallback;
    private Timeline saveSuccessTimeline;

    public ManageAccountUI() {
        initWidgets();
    }

    public void addCallbackHandler(Callback<Object> onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    private void initWidgets() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        formContainer.setPadding(new Insets(8));
        formContainer.setSpacing(5);
        formContainer.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(formContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);

        saveButton.setMinWidth(Region.USE_COMPUTED_SIZE);
        saveButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setMinHeight(50);
        saveButton.setPrefHeight(50);
        refreshChannelsButton.setMinWidth(Region.USE_COMPUTED_SIZE);
        refreshChannelsButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
        m3u8Path.setMinWidth(180);
        accountType.setMinWidth(250);
        macAddress.setPrefWidth(235); // Reduced by ~33% from 350

        clearButton.setMinWidth(Region.USE_COMPUTED_SIZE);
        clearButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
        deleteButton.setMinWidth(Region.USE_COMPUTED_SIZE);
        deleteButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
        accountType.setMaxWidth(Double.MAX_VALUE);
        name.setMaxWidth(Double.MAX_VALUE);
        url.setMaxWidth(Double.MAX_VALUE);
        m3u8Path.setMaxWidth(Double.MAX_VALUE);
        epg.setMaxWidth(Double.MAX_VALUE);
        username.setMaxWidth(Double.MAX_VALUE);
        password.setMaxWidth(Double.MAX_VALUE);
        serialNumber.setMaxWidth(Double.MAX_VALUE);
        deviceId1.setMaxWidth(Double.MAX_VALUE);
        deviceId2.setMaxWidth(Double.MAX_VALUE);
        signature.setMaxWidth(Double.MAX_VALUE);
        macAddressList.setMaxWidth(Double.MAX_VALUE);
        httpMethodCombo.setMaxWidth(Double.MAX_VALUE);
        timezoneCombo.setMaxWidth(Double.MAX_VALUE);

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

        HBox actionButtonRow = new HBox(5, refreshChannelsButton, clearButton, deleteButton);
        HBox.setHgrow(refreshChannelsButton, Priority.ALWAYS);
        HBox.setHgrow(clearButton, Priority.ALWAYS);
        HBox.setHgrow(deleteButton, Priority.ALWAYS);
        refreshChannelsButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        VBox actionSection = new VBox(12, saveButton, actionButtonRow);

        populateForm(STALKER_PORTAL, macAddressContainer, actionSection);
        addSubmitButtonClickHandler();
        addDeleteButtonClickHandler();
        addClearButtonClickHandler();
        addRefreshChannelsButtonClickHandler();
        addBrowserButton1ClickHandler();

        // Initialize HTTP Method combo
        httpMethodCombo.getItems().addAll("GET", "POST");
        httpMethodCombo.setValue("GET");

        // Initialize Timezone combo with all available timezones
        timezoneCombo.getItems().addAll(java.time.ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneCombo.setValue(DEFAULT_TIMEZONE);

        accountType.getItems().addAll(Arrays.stream(AccountType.values()).map(AccountType::getDisplay).toList());
        accountType.setValue(STALKER_PORTAL.getDisplay());
        accountType.valueProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
            if (newValue != null) {
                Platform.runLater(() -> {
                    AccountType type = getAccountTypeByDisplay(newValue);
                    populateForm(type, macAddressContainer, actionSection);
                });
            }
        });
    }

    private void populateForm(AccountType type, HBox macAddressContainer, VBox actionSection) {
        formContainer.getChildren().clear();
        switch (type) {
            case STALKER_PORTAL:
                formContainer.getChildren().addAll(accountType, name, url, macAddressContainer, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, httpMethodCombo, timezoneCombo, pinToTopCheckBox);
                break;
            case M3U8_LOCAL:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, browserButtonM3u8Path, pinToTopCheckBox);
                break;
            case M3U8_URL:
            case RSS_FEED:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, epg, pinToTopCheckBox);
                break;
            case XTREME_API:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, username, password, epg, pinToTopCheckBox);
                break;
        }

        boolean cacheSupported = CACHE_SUPPORTED.contains(type);
        refreshChannelsButton.setManaged(cacheSupported);
        refreshChannelsButton.setVisible(cacheSupported);
        formContainer.getChildren().add(actionSection);
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
        List<String> macList = parseMacAddressesForVerification();
        if (macList.isEmpty()) {
            showErrorAlert(I18n.tr("autoNoMACAddressesToVerify"));
            return;
        }

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
                    if (shouldStopVerification(stopRequested)) break;
                    String mac = macList.get(i);
                    boolean isValid = verifySingleMac(progressDialog, accountToVerify, mac, i, total);
                    if (!isValid) {
                        invalidMacs.add(mac);
                    }
                    if (i < total - 1) {
                        pauseBetweenMacChecks(progressDialog, stopRequested);
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
            showErrorAlert(I18n.tr("autoVerificationFailed", task.getException().getMessage()));
        });

        task.setOnCancelled(e -> {
            progressDialog.close();
            showMessageAlert(I18n.tr("autoVerificationCancelled"));
        });

        new Thread(task).start();
    }

    private List<String> parseMacAddressesForVerification() {
        String macs = macAddressList.getText();
        if (isBlank(macs)) {
            return List.of();
        }
        return new ArrayList<>(Arrays.stream(macs.replace(SPACE, "").split(","))
                .filter(value -> !isBlank(value))
                .toList());
    }

    private boolean shouldStopVerification(AtomicBoolean stopRequested) {
        return Thread.currentThread().isInterrupted() || stopRequested.get();
    }

    private boolean verifySingleMac(ProgressDialog progressDialog, Account accountToVerify, String mac, int index, int total) {
        progressDialog.addProgressText(I18n.tr("manageVerifyingMacProgress", index + 1, total, mac));
        boolean isValid = cacheService.verifyMacAddress(accountToVerify, mac);
        progressDialog.addResult(isValid);
        progressDialog.addProgressText(I18n.tr(isValid ? "manageResultValid" : "manageResultInvalid"));
        return isValid;
    }

    private void pauseBetweenMacChecks(ProgressDialog progressDialog, AtomicBoolean stopRequested) throws InterruptedException {
        long delayMillis = progressDialog.getSelectedDelayMillis();
        int totalSeconds = (int) (delayMillis / 1000);
        for (int seconds = totalSeconds; seconds > 0; seconds--) {
            if (shouldStopVerification(stopRequested)) {
                break;
            }
            progressDialog.setPauseStatus(seconds, totalSeconds);
            Thread.sleep(1000);
        }
        progressDialog.setPauseStatus(0, 0);
    }

    private void handleVerificationResults(List<String> allMacs, List<String> invalidMacs, boolean wasStopped) {
        if (invalidMacs.isEmpty()) {
            if (!wasStopped) {
                showMessageAlert(I18n.tr("autoAllMACAddressesAreValid"));
            }
            return;
        }

        if (!wasStopped && invalidMacs.size() == allMacs.size()) {
            ButtonType result = showDialog(I18n.tr("manageNoValidMacAddressesFoundDeleteAccount"));
            if (result == ButtonType.YES) {
                deleteAccount(name.getText(), accountId);
            }
            return;
        }

        String invalidMacsStr = String.join(", ", invalidMacs);
        StringBuilder message = new StringBuilder(I18n.tr("manageFoundInvalidMacAddresses", invalidMacsStr)).append("\n");

        String currentDefault = macAddress.getValue() != null ? macAddress.getValue().toString() : "";
        boolean defaultIsInvalid = invalidMacs.contains(currentDefault);

        if (defaultIsInvalid) {
            message.append("\n").append(I18n.tr("manageDefaultMacAddressInvalidAndWillBeRemoved")).append("\n");
        }

        message.append("\n").append(I18n.tr("manageDeleteInvalidMacAddressesQuestion"));
        ButtonType result = showDialog(message.toString());
        if (result == ButtonType.YES) {
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
            ButtonType result = showDialog(I18n.tr("manageClearAllCachedDataForThisAccount"));
            if (result == ButtonType.YES) {
                cacheService.clearCache(getAccountFromForm());
                showMessageAlert(I18n.tr("autoCacheCleared"));
            }
        });
    }

    private void addRefreshChannelsButtonClickHandler() {
        refreshChannelsButton.setOnAction(event -> {
            Account account = getAccountFromForm();
            if (account == null || isBlank(account.getDbId())) {
                showErrorAlert(I18n.tr("autoPleaseSaveTheAccountBeforeReloadingTheCache"));
                return;
            }
            ReloadCachePopup.showPopup(resolveOwnerStage(), List.of(account), this::notifyAccountsChanged);
        });
    }

    private void notifyAccountsChanged() {
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private Stage resolveOwnerStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage) {
            return (Stage) getScene().getWindow();
        }
        return RootApplication.primaryStage;
    }

    public void clearAll() {
        Arrays.stream(new UIptvText[]{name, username, password, url, serialNumber, deviceId1, deviceId2, signature, m3u8Path, epg}).forEach(TextInputControl::clear);
        macAddressList.clear();
        macAddress.getItems().clear();
        macAddress.setValue(null);
        macAddress.setPromptText(I18n.tr(PRIMARY_MAC_ADDRESS_HINT_KEY));
        accountType.setValue(STALKER_PORTAL.getDisplay());
        pinToTopCheckBox.setSelected(false);
        httpMethodCombo.setValue("GET");
        timezoneCombo.setValue(DEFAULT_TIMEZONE);
        verifyMacsLink.setVisible(false);
        accountId = null;
        updateButtonState();
    }

    private void updateButtonState() {
        // Disable cache and delete buttons when no account is loaded
        boolean accountLoaded = isNotBlank(accountId);
        clearButton.setDisable(!accountLoaded);
        deleteButton.setDisable(!accountLoaded);
        refreshChannelsButton.setDisable(!accountLoaded);
    }

    private Account getAccountFromForm() {
        Account account = new Account(name.getText(), username.getText(), password.getText(), url.getText(),
                macAddress.getValue() != null ? macAddress.getValue().toString() : "", macAddressList.getText(), serialNumber.getText(), deviceId1.getText(), deviceId2.getText(), signature.getText(),
                getAccountTypeByDisplay(accountType.getValue() != null && isNotBlank(accountType.getValue().toString()) ? accountType.getValue().toString() : AccountType.STALKER_PORTAL.getDisplay()), epg.getText(), m3u8Path.getText(), pinToTopCheckBox.isSelected());
        if (accountId != null) {
            account.setDbId(accountId);
        }
        account.setHttpMethod(httpMethodCombo.getValue() != null ? httpMethodCombo.getValue().toString() : "GET");
        account.setTimezone(timezoneCombo.getValue() != null ? timezoneCombo.getValue().toString() : DEFAULT_TIMEZONE);
        return account;
    }

    private void saveAccount(boolean isFullSave) {
        if (saveButton.isDisable()) {
            return;
        }

        try {
            if (isBlank(name.getText())) {
                showErrorAlert(I18n.tr("autoNameCannotBeEmpty"));
                return;
            }

            saveButton.setDisable(true);

            Account account = getAccountFromForm();
            service.save(account);

            if (isFullSave) {
                // Keep the current account displayed instead of clearing
                // Refresh the account data to show updated values
                Account refreshedAccount = service.getByName(account.getAccountName());
                if (refreshedAccount != null) {
                    editAccount(refreshedAccount);
                }
                if (onSaveCallback != null) {
                    onSaveCallback.call(null);
                }
                showSaveSuccessAnimation();
            } else {
                saveButton.setDisable(false);
            }
        } catch (Exception e) {
            showErrorAlert(I18n.tr("autoFailedToSaveAccountPleaseTryAgain"));
            saveButton.setDisable(false);
        }
    }

    private void showSaveSuccessAnimation() {
        String originalText = saveButton.getText();
        saveButton.setText("✅");

        if (saveSuccessTimeline != null) {
            saveSuccessTimeline.stop();
        }

        // Reset button after 3 seconds
        saveSuccessTimeline = new Timeline(new KeyFrame(
                Duration.seconds(3),
                event -> {
                    saveButton.setText(originalText);
                    saveButton.setDisable(false);
                }
        ));
        saveSuccessTimeline.setCycleCount(1);
        saveSuccessTimeline.setOnFinished(event -> {
            saveButton.setText(originalText);
            saveButton.setDisable(false);
        });
        saveSuccessTimeline.play();
    }

    private void addSubmitButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> saveAccount(true));
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
        ButtonType result = showDialog(I18n.tr("manageDeleteThisAccount", name));
        if (result == ButtonType.YES) {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    service.delete(accountId);
                    return null;
                }
            };

            task.setOnSucceeded(event -> {
                onSaveCallback.call(null);
                clearAll();
            });

            task.setOnFailed(event -> showErrorAlert(I18n.tr("autoFailed")));

            new Thread(task).start();
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
        httpMethodCombo.setValue(isNotBlank(account.getHttpMethod()) ? account.getHttpMethod() : "GET");
        timezoneCombo.setValue(isNotBlank(account.getTimezone()) ? account.getTimezone() : DEFAULT_TIMEZONE);
        accountType.setValue(account.getType().getDisplay());
        updateButtonState();
    }
}
