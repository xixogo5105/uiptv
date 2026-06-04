package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.service.AccountInfoService;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.AccountType;
import com.uiptv.util.AccountCopyUtil;
import com.uiptv.widget.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
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
    private static final double ACTION_BUTTON_GAP = 8;
    private static final double ACTION_ROW_EXTRA_SPACE = 24;
    final FileChooser fileChooser = new FileChooser();
    final Button browserButtonM3u8Path = new Button(I18n.tr("autoBrowse"));
    final UIptvText m3u8Path = new UIptvText("m3u8Path", "manageM3u8FilePathUrlPrompt", 5);
    private final ComboBox<String> accountType = new ComboBox<>();
    private final UIptvText name = new UIptvText("name", "manageAccountNameMustBeUniquePrompt", 5);
    private final UIptvText username = new UIptvText("username", "manageUserNamePrompt", 5);
    private final UIptvText password = new UIptvText("password", "managePasswordPrompt", 5);
    private final UIptvCombo xtremeUsername = new UIptvCombo("xtremeUsername", "manageUserNamePrompt", 350);
    private final Hyperlink manageXtremeCredentialsLink = new Hyperlink(I18n.tr("autoManage"));
    private final HBox xtremeUsernameContainer = new HBox(5, xtremeUsername, manageXtremeCredentialsLink);
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
    private final CheckBox resolveChainAndDeepRedirectsCheckBox = new CheckBox(I18n.tr("manageResolveChainAndDeepRedirects"));
    private final SwitchToggle pinToTopSwitch = new SwitchToggle();
    private final SwitchToggle resolveChainAndDeepRedirectsSwitch = new SwitchToggle();
    private final UIptvCombo httpMethodCombo = new UIptvCombo("httpMethod", "manageHttpMethodPrompt", 150);
    private final UIptvCombo timezoneCombo = new UIptvCombo("timezone", "manageTimezonePrompt", 250);
    private final ProminentButton saveButton = new ProminentButton(I18n.tr("commonSave"));
    private final DangerousButton deleteButton = new DangerousButton(I18n.tr("autoDeleteAccount"));
    private final Button clearButton = new Button(I18n.tr("autoClearData"));
    private final Button refreshChannelsButton = new Button(I18n.tr("autoReloadCache"));
    private final CacheService cacheService = new CacheServiceImpl();
    private final AccountInfoService accountInfoService = AccountInfoService.getInstance();
    private final VBox formContainer = new VBox();
    private final ManageAccountInfoPane accountInfoPane = new ManageAccountInfoPane();
    private final ManageAccountXtremeCredentialsHelper xtremeCredentialsHelper =
            new ManageAccountXtremeCredentialsHelper(xtremeUsername, username, password, manageXtremeCredentialsLink, xtremeUsernameContainer);
    private HBox macAddressContainer;
    private VBox actionSection;
    private HBox wideActionRow;
    private FlowPane wrappedActionPane;
    private Region actionSpacer;
    private boolean actionLayoutWrapped;
    private Node pinToTopSwitchRow;
    private Node resolveChainAndDeepRedirectsSwitchRow;
    AccountService service = AccountService.getInstance();
    private String accountId;
    private String originalAccountName;
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

        saveButton.setMinWidth(112);
        saveButton.setPrefWidth(126);
        saveButton.setMaxWidth(Region.USE_PREF_SIZE);
        saveButton.setMinHeight(38);
        refreshChannelsButton.setMinWidth(150);
        refreshChannelsButton.setPrefWidth(156);
        refreshChannelsButton.setMaxWidth(Region.USE_PREF_SIZE);
        m3u8Path.setMinWidth(180);
        accountType.setMinWidth(250);
        macAddress.setPrefWidth(235); // Reduced by ~33% from 350

        clearButton.setMinWidth(112);
        clearButton.setPrefWidth(126);
        clearButton.setMaxWidth(Region.USE_PREF_SIZE);
        deleteButton.setMinWidth(156);
        deleteButton.setPrefWidth(166);
        deleteButton.setMaxWidth(Region.USE_PREF_SIZE);
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
        xtremeUsername.setMaxWidth(Double.MAX_VALUE);

        macAddressList.textProperty().addListener((observable, oldVal, newVal) -> setupMacAddressByList(newVal));

        verifyMacsLink.setVisible(false);
        verifyMacsLink.managedProperty().bind(verifyMacsLink.visibleProperty());
        verifyMacsLink.getStyleClass().add("manage-mac-action-link");
        verifyMacsLink.setOnAction(event -> verifyMacAddresses());

        manageMacsLink.setVisible(false);
        manageMacsLink.managedProperty().bind(manageMacsLink.visibleProperty());
        manageMacsLink.getStyleClass().add("manage-mac-action-link");
        manageMacsLink.setOnAction(event -> openManageMacsPopup());

        manageXtremeCredentialsLink.setVisible(false);
        manageXtremeCredentialsLink.setManaged(false);
        manageXtremeCredentialsLink.setOnAction(event -> openManageXtremeCredentialsPopup());
        xtremeUsernameContainer.setAlignment(Pos.CENTER_LEFT);

        pinToTopCheckBox.setManaged(false);
        pinToTopCheckBox.setVisible(false);
        resolveChainAndDeepRedirectsCheckBox.setManaged(false);
        resolveChainAndDeepRedirectsCheckBox.setVisible(false);
        pinToTopSwitch.selectedProperty().bindBidirectional(pinToTopCheckBox.selectedProperty());
        resolveChainAndDeepRedirectsSwitch.selectedProperty().bindBidirectional(resolveChainAndDeepRedirectsCheckBox.selectedProperty());
        pinToTopSwitchRow = createManageAccountSwitchRow("autoPinAccountOnTop", pinToTopSwitch);
        resolveChainAndDeepRedirectsSwitchRow = createManageAccountSwitchRow("manageResolveChainAndDeepRedirects", resolveChainAndDeepRedirectsSwitch);

        pipeLabel.visibleProperty().bind(verifyMacsLink.visibleProperty());
        pipeLabel.managedProperty().bind(pipeLabel.visibleProperty());
        pipeLabel.getStyleClass().add("manage-mac-divider");
        manageMacsLink.visibleProperty().bind(verifyMacsLink.visibleProperty());

        macAddressContainer = new HBox(8, macAddress, verifyMacsLink, pipeLabel, manageMacsLink);
        macAddressContainer.getStyleClass().add("manage-mac-action-row");
        macAddressContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(macAddress, Priority.ALWAYS);

        configureActionSection();

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
                    populateForm(type);
                });
            }
        });
        populateForm(STALKER_PORTAL);
    }

    private void populateForm(AccountType type) {
        formContainer.getChildren().clear();
        switch (type) {
            case STALKER_PORTAL:
                formContainer.getChildren().addAll(accountType, name, url, macAddressContainer, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, httpMethodCombo, timezoneCombo, pinToTopSwitchRow, resolveChainAndDeepRedirectsSwitchRow);
                break;
            case M3U8_LOCAL:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, browserButtonM3u8Path, pinToTopSwitchRow, resolveChainAndDeepRedirectsSwitchRow);
                break;
            case M3U8_URL:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, epg, pinToTopSwitchRow, resolveChainAndDeepRedirectsSwitchRow);
                break;
            case XTREME_API:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, xtremeUsernameContainer, password, epg, pinToTopSwitchRow, resolveChainAndDeepRedirectsSwitchRow);
                break;
        }

        configureXtremeControls(type);
        boolean cacheSupported = CACHE_SUPPORTED.contains(type);
        ensureAccountInfoSectionVisibility(type);
        refreshChannelsButton.setManaged(cacheSupported);
        refreshChannelsButton.setVisible(cacheSupported);
        formContainer.getChildren().add(actionSection);
        Platform.runLater(() -> updateActionSectionLayout(actionSection.getWidth()));
    }

    private void configureActionSection() {
        actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        wideActionRow = new HBox(ACTION_BUTTON_GAP);
        wideActionRow.getStyleClass().add("manage-account-action-wide-row");
        wideActionRow.setAlignment(Pos.CENTER_LEFT);
        wideActionRow.setMinWidth(0);
        wideActionRow.setMaxWidth(Double.MAX_VALUE);

        wrappedActionPane = new FlowPane(ACTION_BUTTON_GAP, ACTION_BUTTON_GAP);
        wrappedActionPane.getStyleClass().add("manage-account-action-wrap");
        wrappedActionPane.setAlignment(Pos.CENTER_LEFT);
        wrappedActionPane.setMinWidth(0);
        wrappedActionPane.setMaxWidth(Double.MAX_VALUE);

        actionSection = new VBox();
        actionSection.getStyleClass().add("manage-account-action-row");
        actionSection.setMinWidth(0);
        actionSection.setMaxWidth(Double.MAX_VALUE);
        actionSection.widthProperty().addListener((_, _, width) -> updateActionSectionLayout(width.doubleValue()));
        showWideActionLayout();
    }

    private void updateActionSectionLayout(double width) {
        if (actionSection == null || width <= 0) {
            return;
        }
        if (width >= requiredWideActionWidth()) {
            showWideActionLayout();
        } else {
            showWrappedActionLayout();
        }
    }

    private double requiredWideActionWidth() {
        double buttonWidth = saveButton.getPrefWidth() + clearButton.getPrefWidth() + deleteButton.getPrefWidth();
        int visibleButtonCount = 3;
        if (refreshChannelsButton.isManaged()) {
            buttonWidth += refreshChannelsButton.getPrefWidth();
            visibleButtonCount++;
        }
        return buttonWidth + (ACTION_BUTTON_GAP * visibleButtonCount) + ACTION_ROW_EXTRA_SPACE;
    }

    private void showWideActionLayout() {
        if (!actionLayoutWrapped && actionSection.getChildren().contains(wideActionRow)) {
            return;
        }
        wrappedActionPane.getChildren().clear();
        wideActionRow.getChildren().setAll(saveButton, refreshChannelsButton, clearButton, actionSpacer, deleteButton);
        actionSection.getChildren().setAll(wideActionRow);
        actionLayoutWrapped = false;
    }

    private void showWrappedActionLayout() {
        if (actionLayoutWrapped && actionSection.getChildren().contains(wrappedActionPane)) {
            return;
        }
        wideActionRow.getChildren().clear();
        wrappedActionPane.getChildren().setAll(saveButton, refreshChannelsButton, clearButton, deleteButton);
        actionSection.getChildren().setAll(wrappedActionPane);
        actionLayoutWrapped = true;
    }

    private Node createManageAccountSwitchRow(String labelKey, SwitchToggle switchToggle) {
        Label label = new Label(I18n.tr(labelKey));
        label.getStyleClass().add("manage-account-switch-label");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);

        HBox row = new HBox(12, label, switchToggle);
        row.getStyleClass().add("manage-account-switch-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        return row;
    }

    private void configureXtremeControls(AccountType type) {
        xtremeCredentialsHelper.configureForType(type);
    }

    private void openManageXtremeCredentialsPopup() {
        xtremeCredentialsHelper.openManagementPopup((Stage) getScene().getWindow(), () -> saveAccount(false));
    }

    private void ensureAccountInfoSectionVisibility(AccountType type) {
        boolean showAccountInfo = type == STALKER_PORTAL && isNotBlank(accountId) && accountInfoPane.hasProfileJson();
        if (!showAccountInfo) {
            formContainer.getChildren().remove(accountInfoPane);
            accountInfoPane.setManaged(false);
            accountInfoPane.setVisible(false);
            return;
        }
        accountInfoPane.setManaged(true);
        accountInfoPane.setVisible(true);
        if (!formContainer.getChildren().contains(accountInfoPane)) {
            int actionIndex = actionSection != null ? formContainer.getChildren().indexOf(actionSection) : -1;
            if (actionIndex < 0) {
                formContainer.getChildren().add(accountInfoPane);
            } else {
                formContainer.getChildren().add(actionIndex, accountInfoPane);
            }
        }
    }

    private void openManageMacsPopup() {
        String currentMacs = macAddressList.getText();
        List<String> macList = new ArrayList<>();
        if (isNotBlank(currentMacs)) {
            macList.addAll(Arrays.stream(currentMacs.replace(SPACE, "").split(",")).toList());
        }

        String currentDefault = macAddress.getValue();

        Account baseAccount = getAccountFromForm();
        MacAddressManagementPopup popup = new MacAddressManagementPopup((Stage) getScene().getWindow(), baseAccount, macList, currentDefault, (newMacs, newDefault) -> {
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
            List<String> invalidMacs = task.getValue();
            handleVerificationResults(progressDialog, macList, invalidMacs, stopRequested.get());
            progressDialog.markCompleted();
        });

        task.setOnFailed(e -> {
            progressDialog.addProgressText(I18n.tr("autoVerificationFailed", task.getException().getMessage()));
            showErrorAlert(I18n.tr("autoVerificationFailed", task.getException().getMessage()));
            progressDialog.markCompleted();
        });

        task.setOnCancelled(e -> {
            progressDialog.addProgressText(I18n.tr("autoVerificationCancelled"));
            showMessageAlert(I18n.tr("autoVerificationCancelled"));
            progressDialog.markCompleted();
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
        appendVerificationSectionHeader(progressDialog, mac, index, total);
        boolean isValid = cacheService.verifyMacAddress(accountToVerify, mac);
        progressDialog.addResult(isValid);
        progressDialog.addProgressText("  " + I18n.tr(isValid ? "manageResultValid" : "manageResultInvalid"));
        AccountInfo info = HandshakeService.getInstance().fetchAccountInfo(AccountCopyUtil.copyForMac(accountToVerify, mac));
        String expiry = info != null ? AccountInfoUiUtil.formatDate(info.getExpireDate()) : "";
        if (isBlank(expiry)) {
            expiry = "Unknown";
        }
        String status = info != null && info.getAccountStatus() != null ? info.getAccountStatus().toDisplay() : "unknown";
        progressDialog.addProgressText("  " + I18n.tr("manageAccountInfoExpireDate") + ": " + expiry);
        progressDialog.addProgressText("  " + I18n.tr("manageAccountInfoStatus") + ": " + status);
        return isValid;
    }

    private void appendVerificationSectionHeader(ProgressDialog progressDialog, String mac, int index, int total) {
        if (index > 0) {
            progressDialog.addProgressText("--------------------------------------------------");
        }
        progressDialog.addProgressText(I18n.tr("manageVerifyingMacProgress", index + 1, total, mac));
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

    private void handleVerificationResults(ProgressDialog progressDialog, List<String> allMacs, List<String> invalidMacs, boolean wasStopped) {
        if (invalidMacs.isEmpty()) {
            if (!wasStopped) {
                progressDialog.addProgressText(I18n.tr("autoAllMACAddressesAreValid"));
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

        String currentDefault = macAddress.getValue() != null ? macAddress.getValue() : "";
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
                macAddress.setValue(validMacs.getFirst());
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
        if (macAddress.getValue() == null || isBlank(macAddress.getValue()) || !macAddress.getValue().toLowerCase().contains(newVal.toLowerCase())) {
            macAddress.setValue(newVal.split(",")[0].trim());
        } else {
            macAddress.setValue(macAddress.getValue());
        }
    }

    private void addBrowserButton1ClickHandler() {
        browserButtonM3u8Path.setOnAction(_ -> {
            File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
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
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return RootApplication.getPrimaryStage();
    }

    public void clearAll() {
        Arrays.stream(new UIptvText[]{name, username, password, url, serialNumber, deviceId1, deviceId2, signature, m3u8Path, epg}).forEach(TextInputControl::clear);
        xtremeCredentialsHelper.reset();
        macAddressList.clear();
        macAddress.getItems().clear();
        macAddress.setValue(null);
        macAddress.setPromptText(I18n.tr(PRIMARY_MAC_ADDRESS_HINT_KEY));
        accountType.setValue(STALKER_PORTAL.getDisplay());
        pinToTopCheckBox.setSelected(false);
        resolveChainAndDeepRedirectsCheckBox.setSelected(false);
        httpMethodCombo.setValue("GET");
        timezoneCombo.setValue(DEFAULT_TIMEZONE);
        verifyMacsLink.setVisible(false);
        accountId = null;
        originalAccountName = null;
        accountInfoPane.clear();
        ensureAccountInfoSectionVisibility(getAccountTypeByDisplay(accountType.getValue()));
        updateButtonState();
    }

    private void updateButtonState() {
        boolean accountLoaded = isNotBlank(accountId);
        clearButton.setDisable(!accountLoaded);
        deleteButton.setDisable(!accountLoaded);
        refreshChannelsButton.setDisable(!accountLoaded);
    }

    private Account getAccountFromForm() {
        String selectedAccountType = accountType.getValue();
        String accountTypeDisplay = isNotBlank(selectedAccountType) ? selectedAccountType : AccountType.STALKER_PORTAL.getDisplay();
        AccountType resolvedType = getAccountTypeByDisplay(accountTypeDisplay);
        String resolvedUsername = username.getText();
        if (resolvedType == AccountType.XTREME_API) {
            String xtremeSelectedUsername = xtremeUsername.getValue();
            if (isNotBlank(xtremeSelectedUsername)) {
                resolvedUsername = xtremeSelectedUsername;
            }
        }
        Account account = new Account(name.getText(), resolvedUsername, password.getText(), url.getText(),
                macAddress.getValue() != null ? macAddress.getValue() : "", macAddressList.getText(), serialNumber.getText(), deviceId1.getText(), deviceId2.getText(), signature.getText(),
                resolvedType, epg.getText(), m3u8Path.getText(), pinToTopCheckBox.isSelected());
        if (accountId != null) {
            account.setDbId(accountId);
        }
        account.setResolveChainAndDeepRedirects(resolveChainAndDeepRedirectsCheckBox.isSelected());
        account.setHttpMethod(httpMethodCombo.getValue() != null ? httpMethodCombo.getValue() : "GET");
        account.setTimezone(timezoneCombo.getValue() != null ? timezoneCombo.getValue() : DEFAULT_TIMEZONE);
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

            if (!confirmAccountRenameIfNeeded()) {
                return;
            }

            saveButton.setDisable(true);

            Account account = getAccountFromForm();
            xtremeCredentialsHelper.applyToAccount(account);
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
                refreshTrackedAccountIdentity(account);
                saveButton.setDisable(false);
            }
        } catch (Exception _) {
            showErrorAlert(I18n.tr("autoFailedToSaveAccountPleaseTryAgain"));
            saveButton.setDisable(false);
        }
    }

    private boolean confirmAccountRenameIfNeeded() {
        if (!isExistingAccountRename()) {
            return true;
        }
        ButtonType result = showDialog(I18n.tr("manageAccountRenameCreatesNewAccountConfirm", originalAccountName, name.getText()));
        return result == ButtonType.YES;
    }

    private boolean isExistingAccountRename() {
        return isNotBlank(accountId)
                && isNotBlank(originalAccountName)
                && isNotBlank(name.getText())
                && !Objects.equals(originalAccountName, name.getText());
    }

    private void refreshTrackedAccountIdentity(Account account) {
        Account refreshedAccount = service.getByName(account.getAccountName());
        if (refreshedAccount == null) {
            return;
        }
        accountId = refreshedAccount.getDbId();
        originalAccountName = refreshedAccount.getAccountName();
        updateButtonState();
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
        saveButton.setOnAction(_ -> saveAccount(true));
    }

    private void addDeleteButtonClickHandler() {
        deleteButton.setOnAction(_ -> deleteAccount(name.getText(), accountId));
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
        originalAccountName = account.getAccountName();
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
        resolveChainAndDeepRedirectsCheckBox.setSelected(account.isResolveChainAndDeepRedirects());
        httpMethodCombo.setValue(isNotBlank(account.getHttpMethod()) ? account.getHttpMethod() : "GET");
        timezoneCombo.setValue(isNotBlank(account.getTimezone()) ? account.getTimezone() : DEFAULT_TIMEZONE);
        accountType.setValue(account.getType().getDisplay());
        if (account.getType() == AccountType.XTREME_API) {
            Platform.runLater(() -> xtremeCredentialsHelper.loadFromAccount(account));
        }
        accountInfoPane.apply(accountInfoService.getByAccountId(accountId));
        ensureAccountInfoSectionVisibility(account.getType());
        updateButtonState();
    }
}
