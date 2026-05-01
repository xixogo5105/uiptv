package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.service.AccountInfoService;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeCredentialsJson;
import com.uiptv.widget.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.uiptv.model.Account.CACHE_SUPPORTED;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;
import static com.uiptv.util.StringUtils.*;
import static com.uiptv.widget.DialogAlert.showDialog;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ManageAccountUI extends VBox {
    public static final String PRIMARY_MAC_ADDRESS_HINT_KEY = "managePrimaryMacAddressHint";
    private static final String STYLE_CLASS_DIM_LABEL = "dim-label";
    private static final String DEFAULT_TIMEZONE = "Europe/London";
    private static final String PROFILE_DATA_TITLE = "Profile data";
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
    private final UIptvCombo httpMethodCombo = new UIptvCombo("httpMethod", "manageHttpMethodPrompt", 150);
    private final UIptvCombo timezoneCombo = new UIptvCombo("timezone", "manageTimezonePrompt", 250);
    private final ProminentButton saveButton = new ProminentButton(I18n.tr("commonSave"));
    private final DangerousButton deleteButton = new DangerousButton(I18n.tr("autoDeleteAccount"));
    private final Button clearButton = new Button(I18n.tr("autoClearData"));
    private final Button refreshChannelsButton = new Button(I18n.tr("autoReloadCache"));
    private final CacheService cacheService = new CacheServiceImpl();
    private final AccountInfoService accountInfoService = AccountInfoService.getInstance();
    private final VBox formContainer = new VBox();
    private HBox macAddressContainer;
    private VBox actionSection;
    private final Label accountInfoExpireDate = new Label();
    private final Label accountInfoStatus = new Label();
    private final Label accountInfoBalance = new Label();
    private final Label accountInfoTariffName = new Label();
    private final Label accountInfoTariffPlan = new Label();
    private final Label accountInfoDefaultTimezone = new Label();
    private final Button accountInfoProfileCopyButton = new Button(I18n.tr("autoCopy"));
    private final Label accountInfoProfileTitleLabel = new Label(PROFILE_DATA_TITLE);
    private final VBox accountInfoProfileContainer = new VBox(6);
    private final BorderPane accountInfoProfileBox = new BorderPane();
    private final VBox accountInfoProfileLines = new VBox(4);
    private final StackPane accountInfoProfileToggle = new StackPane();
    private final javafx.scene.shape.SVGPath accountInfoProfileToggleIcon = new javafx.scene.shape.SVGPath();
    private String accountInfoProfileRawJson;
    private final Region accountInfoStatusIndicator = new Region();
    private final Region accountInfoExpiryIndicator = new Region();
    private boolean accountInfoHasProfileJson;
    private AccountInfoRow accountInfoExpireDateRow;
    private AccountInfoRow accountInfoStatusRow;
    private AccountInfoRow accountInfoBalanceRow;
    private AccountInfoRow accountInfoTariffNameRow;
    private AccountInfoRow accountInfoTariffPlanRow;
    private AccountInfoRow accountInfoDefaultTimezoneRow;
    private BorderPane accountInfoPane;
    AccountService service = AccountService.getInstance();
    private String accountId;
    private Callback<Object> onSaveCallback;
    private Timeline saveSuccessTimeline;
    private List<XtremeCredentialsJson.Entry> xtremeCredentials = new ArrayList<>();
    private String xtremeDefaultUsername;

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
        xtremeUsername.setMaxWidth(Double.MAX_VALUE);

        macAddressList.textProperty().addListener((observable, oldVal, newVal) -> setupMacAddressByList(newVal));

        verifyMacsLink.setVisible(false);
        verifyMacsLink.setOnAction(event -> verifyMacAddresses());

        manageMacsLink.setVisible(false);
        manageMacsLink.setOnAction(event -> openManageMacsPopup());

        manageXtremeCredentialsLink.setVisible(false);
        manageXtremeCredentialsLink.setManaged(false);
        manageXtremeCredentialsLink.setOnAction(event -> openManageXtremeCredentialsPopup());
        xtremeUsernameContainer.setAlignment(Pos.CENTER_LEFT);
        xtremeUsername.valueProperty().addListener((obs, oldVal, newVal) -> handleXtremeUsernameSelection(newVal));

        pipeLabel.visibleProperty().bind(verifyMacsLink.visibleProperty());
        manageMacsLink.visibleProperty().bind(verifyMacsLink.visibleProperty());

        macAddressContainer = new HBox(5, macAddress, verifyMacsLink, pipeLabel, manageMacsLink);
        macAddressContainer.setAlignment(Pos.CENTER_LEFT);

        HBox actionButtonRow = new HBox(5, refreshChannelsButton, clearButton, deleteButton);
        HBox.setHgrow(refreshChannelsButton, Priority.ALWAYS);
        HBox.setHgrow(clearButton, Priority.ALWAYS);
        HBox.setHgrow(deleteButton, Priority.ALWAYS);
        refreshChannelsButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        actionSection = new VBox(12, saveButton, actionButtonRow);

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

        configureProfileJsonArea();
        accountInfoPane = buildAccountInfoPane();

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
                formContainer.getChildren().addAll(accountType, name, url, macAddressContainer, macAddressList, serialNumber, deviceId1, deviceId2, signature, username, password, httpMethodCombo, timezoneCombo, pinToTopCheckBox, resolveChainAndDeepRedirectsCheckBox);
                break;
            case M3U8_LOCAL:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, browserButtonM3u8Path, pinToTopCheckBox, resolveChainAndDeepRedirectsCheckBox);
                break;
            case M3U8_URL:
            case RSS_FEED:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, epg, pinToTopCheckBox, resolveChainAndDeepRedirectsCheckBox);
                break;
            case XTREME_API:
                formContainer.getChildren().addAll(accountType, name, m3u8Path, xtremeUsernameContainer, password, epg, pinToTopCheckBox, resolveChainAndDeepRedirectsCheckBox);
                break;
        }

        configureXtremeControls(type);
        boolean cacheSupported = CACHE_SUPPORTED.contains(type);
        ensureAccountInfoSectionVisibility(type);
        refreshChannelsButton.setManaged(cacheSupported);
        refreshChannelsButton.setVisible(cacheSupported);
        formContainer.getChildren().add(actionSection);
    }

    private void configureXtremeControls(AccountType type) {
        boolean isXtreme = type == AccountType.XTREME_API;
        password.setEditable(!isXtreme);
        xtremeUsernameContainer.setVisible(isXtreme);
        xtremeUsernameContainer.setManaged(isXtreme);
        manageXtremeCredentialsLink.setVisible(isXtreme);
        manageXtremeCredentialsLink.setManaged(isXtreme);
        if (isXtreme) {
            seedXtremeCredentialsFromFieldsIfNeeded();
            refreshXtremeUsernameItems();
        }
    }

    private void ensureAccountInfoSectionVisibility(AccountType type) {
        if (accountInfoPane == null) {
            return;
        }
        boolean showAccountInfo = type == STALKER_PORTAL && isNotBlank(accountId) && accountInfoHasProfileJson;
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

    private void seedXtremeCredentialsFromFieldsIfNeeded() {
        if (!xtremeCredentials.isEmpty()) {
            return;
        }
        String currentUsername = isNotBlank(xtremeUsername.getValue()) ? xtremeUsername.getValue() : username.getText();
        String currentPassword = password.getText();
        if (isBlank(currentUsername) || isBlank(currentPassword)) {
            return;
        }
        xtremeCredentials = new ArrayList<>();
        xtremeCredentials.add(new XtremeCredentialsJson.Entry(currentUsername, currentPassword, true));
        xtremeDefaultUsername = currentUsername;
    }

    private void refreshXtremeUsernameItems() {
        if (xtremeCredentials.isEmpty()) {
            xtremeUsername.getItems().clear();
            xtremeUsername.setValue(null);
            return;
        }
        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(xtremeCredentials, xtremeDefaultUsername);
        xtremeCredentials = normalized;
        xtremeUsername.getItems().setAll(normalized.stream().map(XtremeCredentialsJson.Entry::username).toList());

        String selection = xtremeDefaultUsername;
        if (isBlank(selection)) {
            selection = xtremeUsername.getValue();
        }
        if (isBlank(selection) && !normalized.isEmpty()) {
            selection = normalized.getFirst().username();
        }
        if (selection != null) {
            xtremeUsername.setValue(selection);
            handleXtremeUsernameSelection(selection);
        }
    }

    private void handleXtremeUsernameSelection(String usernameValue) {
        if (isBlank(usernameValue)) {
            return;
        }
        XtremeCredentialsJson.Entry entry = resolveXtremeCredentialByUsername(usernameValue);
        if (entry == null) {
            return;
        }
        xtremeDefaultUsername = entry.username();
        username.setText(entry.username());
        password.setText(entry.password());
    }

    private XtremeCredentialsJson.Entry resolveXtremeCredentialByUsername(String usernameValue) {
        if (isBlank(usernameValue)) {
            return null;
        }
        for (XtremeCredentialsJson.Entry entry : xtremeCredentials) {
            if (entry != null && usernameValue.equals(entry.username())) {
                return entry;
            }
        }
        return null;
    }

    private void openManageXtremeCredentialsPopup() {
        XtremeCredentialsManagementPopup popup = new XtremeCredentialsManagementPopup(
                (Stage) getScene().getWindow(),
                xtremeCredentials,
                xtremeDefaultUsername,
                (newEntries, newDefault) -> {
                    xtremeCredentials = newEntries != null ? newEntries : new ArrayList<>();
                    xtremeDefaultUsername = newDefault;
                    refreshXtremeUsernameItems();
                    saveAccount(false);
                }
        );
        popup.show();
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
        progressDialog.addProgressText(I18n.tr("manageVerifyingMacProgress", index + 1, total, mac));
        boolean isValid = cacheService.verifyMacAddress(accountToVerify, mac);
        progressDialog.addResult(isValid);
        progressDialog.addProgressText(I18n.tr(isValid ? "manageResultValid" : "manageResultInvalid"));
        AccountInfo info = accountInfoService.getByAccountId(accountToVerify.getDbId());
        String expiry = info != null ? AccountInfoUiUtil.formatDate(info.getExpireDate()) : "";
        if (isBlank(expiry)) {
            expiry = "Unlimited";
        }
        String status = info != null && info.getAccountStatus() != null ? info.getAccountStatus().toDisplay() : "unknown";
        progressDialog.addProgressText(I18n.tr("manageAccountInfoExpireDate") + ": " + expiry);
        progressDialog.addProgressText(I18n.tr("manageAccountInfoStatus") + ": " + status);
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
        xtremeUsername.getItems().clear();
        xtremeUsername.setValue(null);
        xtremeCredentials = new ArrayList<>();
        xtremeDefaultUsername = null;
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
        clearAccountInfoFields();
        ensureAccountInfoSectionVisibility(getAccountTypeByDisplay(accountType.getValue()));
        updateButtonState();
    }

    private void updateButtonState() {
        // Disable cache and delete buttons when no account is loaded
        boolean accountLoaded = isNotBlank(accountId);
        clearButton.setDisable(!accountLoaded);
        deleteButton.setDisable(!accountLoaded);
        refreshChannelsButton.setDisable(!accountLoaded);
    }

    private void configureProfileJsonArea() {
        accountInfoProfileLines.setManaged(false);
        accountInfoProfileLines.setVisible(false);
        accountInfoProfileToggle.setMinSize(18, 18);
        accountInfoProfileToggle.setPrefSize(18, 18);
        accountInfoProfileToggle.setMaxSize(18, 18);
        accountInfoProfileToggle.setStyle("-fx-cursor: hand;");
        accountInfoProfileToggleIcon.setStyle("-fx-fill: -fx-text-base-color;");
        accountInfoProfileToggle.getChildren().setAll(accountInfoProfileToggleIcon);
        updateProfileToggleIcon(false);
        accountInfoProfileTitleLabel.setText(PROFILE_DATA_TITLE);
        accountInfoProfileToggle.setOnMouseClicked(event -> toggleProfileLines());
        accountInfoProfileCopyButton.setOnAction(event -> {
            String raw = accountInfoProfileRawJson != null ? accountInfoProfileRawJson : "";
            if (isBlank(raw)) {
                return;
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(raw);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    private void toggleProfileLines() {
        boolean show = !(accountInfoProfileLines.isVisible() && accountInfoProfileLines.isManaged());
        accountInfoProfileLines.setVisible(show);
        accountInfoProfileLines.setManaged(show);
        accountInfoProfileBox.setVisible(show);
        accountInfoProfileBox.setManaged(show);
        updateProfileToggleIcon(show);
        accountInfoProfileTitleLabel.setText(show ? "Hide profile data" : PROFILE_DATA_TITLE);
    }

    private void updateProfileToggleIcon(boolean expanded) {
        if (expanded) {
            accountInfoProfileToggleIcon.setContent("M4 11 H20 V13 H4 Z");
        } else {
            accountInfoProfileToggleIcon.setContent("M4 11 H20 V13 H4 Z M11 4 H13 V20 H11 Z");
        }
    }

    private static class AccountInfoRow {
        private final Label label;
        private final javafx.scene.Node value;

        private AccountInfoRow(Label label, javafx.scene.Node value) {
            this.label = label;
            this.value = value;
        }
    }

    private BorderPane buildAccountInfoPane() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        accountInfoStatusIndicator.setMinSize(10, 10);
        accountInfoStatusIndicator.setPrefSize(10, 10);
        accountInfoStatusIndicator.setMaxSize(10, 10);
        accountInfoStatusIndicator.setStyle("-fx-background-radius: 6px;");

        accountInfoExpiryIndicator.setMinSize(10, 10);
        accountInfoExpiryIndicator.setPrefSize(10, 10);
        accountInfoExpiryIndicator.setMaxSize(10, 10);
        accountInfoExpiryIndicator.setStyle("-fx-background-radius: 6px;");

        HBox expiryValue = new HBox(6, accountInfoExpiryIndicator, accountInfoExpireDate);
        expiryValue.setAlignment(Pos.CENTER_LEFT);

        HBox statusValue = new HBox(6, accountInfoStatusIndicator, accountInfoStatus);
        statusValue.setAlignment(Pos.CENTER_LEFT);

        accountInfoExpireDateRow = addAccountInfoRow(grid, 0, "manageAccountInfoExpireDate", expiryValue);
        accountInfoStatusRow = addAccountInfoRow(grid, 1, "manageAccountInfoStatus", statusValue);
        accountInfoBalanceRow = addAccountInfoRow(grid, 2, "manageAccountInfoBalance", accountInfoBalance);
        accountInfoTariffNameRow = addAccountInfoRow(grid, 3, "manageAccountInfoTariffName", accountInfoTariffName);
        accountInfoTariffPlanRow = addAccountInfoRow(grid, 4, "manageAccountInfoTariffPlan", accountInfoTariffPlan);
        accountInfoDefaultTimezoneRow = addAccountInfoRow(grid, 5, "manageAccountInfoDefaultTimezone", accountInfoDefaultTimezone);

        Region profileSpacer = new Region();
        profileSpacer.setMinHeight(2);
        HBox profileHeader = new HBox(8, accountInfoProfileToggle, accountInfoProfileTitleLabel, profileSpacer, accountInfoProfileCopyButton);
        HBox.setHgrow(profileSpacer, Priority.ALWAYS);
        accountInfoProfileBox.setCenter(accountInfoProfileLines);
        accountInfoProfileBox.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 6;");
        accountInfoProfileBox.setVisible(false);
        accountInfoProfileBox.setManaged(false);
        accountInfoProfileContainer.getChildren().setAll(profileHeader, accountInfoProfileBox);
        accountInfoProfileContainer.setVisible(false);
        accountInfoProfileContainer.setManaged(false);
        VBox content = new VBox(10, grid, accountInfoProfileContainer);
        content.setMaxWidth(Double.MAX_VALUE);

        BorderPane pane = createCollapsibleGroupPane(
                I18n.tr("manageAccountInfoTitle"),
                I18n.tr("manageAccountInfoDescription"),
                content,
                true
        );
        pane.setMaxWidth(Double.MAX_VALUE);
        return pane;
    }

    private AccountInfoRow addAccountInfoRow(GridPane grid, int row, String labelKey, javafx.scene.Node value) {
        Label label = new Label(I18n.tr(labelKey));
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        return new AccountInfoRow(label, value);
    }

    private BorderPane createCollapsibleGroupPane(String title, String description, javafx.scene.Node content, boolean collapsedByDefault) {
        BorderPane pane = new BorderPane(content);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("strong-label");
        VBox titleContainer = new VBox(4, titleLabel);
        titleContainer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);
        final Label descriptionLabel;
        if (description != null && !description.isBlank()) {
            Label label = new Label(description);
            label.setWrapText(true);
            label.getStyleClass().add(STYLE_CLASS_DIM_LABEL);
            titleContainer.getChildren().add(label);
            descriptionLabel = label;
        } else {
            descriptionLabel = null;
        }

        Hyperlink toggleLink = new Hyperlink();
        toggleLink.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleContainer, spacer, toggleLink);

        final Runnable refreshToggleLabel = () -> {
            boolean expanded = content.isVisible() && content.isManaged();
            toggleLink.setText(expanded ? I18n.tr("commonHide") : I18n.tr("commonShow"));
            if (descriptionLabel != null) {
                descriptionLabel.setVisible(expanded);
                descriptionLabel.setManaged(expanded);
            }
        };
        content.setVisible(!collapsedByDefault);
        content.setManaged(!collapsedByDefault);
        refreshToggleLabel.run();
        toggleLink.setOnAction(event -> {
            boolean expand = !(content.isVisible() && content.isManaged());
            content.setVisible(expand);
            content.setManaged(expand);
            refreshToggleLabel.run();
        });

        BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
        pane.setTop(header);
        pane.setPadding(new Insets(10));
        pane.getStyleClass().add("uiptv-card");
        return pane;
    }

    private void applyAccountInfo(AccountInfo info) {
        String profileJson = info != null ? safeText(info.getProfileJson()) : "";
        accountInfoHasProfileJson = isNotBlank(profileJson);
        if (!accountInfoHasProfileJson) {
            clearAccountInfoFields();
            ensureAccountInfoSectionVisibility(getAccountTypeByDisplay(accountType.getValue()));
            return;
        }

        String rawExpire = info != null ? safeText(info.getExpireDate()) : "";
        boolean unlimited = isBlank(rawExpire) || rawExpire.startsWith("0000-00-00");
        if (unlimited) {
            setAccountInfoValue(accountInfoExpireDateRow, accountInfoExpireDate, "Unlimited");
            AccountInfoUiUtil.applyIndicator(accountInfoExpiryIndicator, AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.OK), true);
        } else {
            AccountInfoUiUtil.ParsedDate parsedExpire = AccountInfoUiUtil.parseDateValue(rawExpire);
            setAccountInfoValue(accountInfoExpireDateRow, accountInfoExpireDate, parsedExpire.display());
            updateExpiryIndicator(parsedExpire.instant(), isNotBlank(parsedExpire.display()));
        }

        com.uiptv.model.AccountStatus status = info != null ? info.getAccountStatus() : null;
        String statusText = safeStatus(status);
        setAccountInfoValue(accountInfoStatusRow, accountInfoStatus, statusText);
        updateStatusIndicator(statusText);

        setAccountInfoValue(accountInfoBalanceRow, accountInfoBalance, info != null ? safeText(info.getAccountBalance()) : "");
        setAccountInfoValue(accountInfoTariffNameRow, accountInfoTariffName, info != null ? safeText(info.getTariffName()) : "");
        setAccountInfoValue(accountInfoTariffPlanRow, accountInfoTariffPlan, info != null ? safeText(info.getTariffPlan()) : "");
        setAccountInfoValue(accountInfoDefaultTimezoneRow, accountInfoDefaultTimezone, info != null ? safeText(info.getDefaultTimezone()) : "");
        setAccountInfoProfileJson(profileJson);
        ensureAccountInfoSectionVisibility(getAccountTypeByDisplay(accountType.getValue()));
    }

    private void clearAccountInfoFields() {
        accountInfoHasProfileJson = false;
        setAccountInfoValue(accountInfoExpireDateRow, accountInfoExpireDate, "");
        setAccountInfoValue(accountInfoStatusRow, accountInfoStatus, "");
        setAccountInfoValue(accountInfoBalanceRow, accountInfoBalance, "");
        setAccountInfoValue(accountInfoTariffNameRow, accountInfoTariffName, "");
        setAccountInfoValue(accountInfoTariffPlanRow, accountInfoTariffPlan, "");
        setAccountInfoValue(accountInfoDefaultTimezoneRow, accountInfoDefaultTimezone, "");
        setAccountInfoProfileJson("");
        updateStatusIndicator("");
        updateExpiryIndicator(null, false);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeStatus(com.uiptv.model.AccountStatus status) {
        return status == null ? "" : status.toDisplay();
    }

    private void setAccountInfoValue(AccountInfoRow row, Label label, String value) {
        String safeValue = safeText(value);
        boolean visible = isNotBlank(safeValue);
        label.setText(safeValue);
        setRowVisible(row, visible);
    }

    private void setRowVisible(AccountInfoRow row, boolean visible) {
        if (row == null) {
            return;
        }
        row.label.setVisible(visible);
        row.label.setManaged(visible);
        row.value.setVisible(visible);
        row.value.setManaged(visible);
    }

    private void setAccountInfoProfileJson(String value) {
        String safeValue = safeText(value);
        boolean visible = isNotBlank(safeValue);
        accountInfoProfileRawJson = safeValue;
        accountInfoProfileLines.getChildren().clear();
        if (visible) {
            String formatted = formatProfileJsonForDisplay(safeValue);
            if (isNotBlank(formatted)) {
                for (String line : formatted.split("\\R")) {
                    if (isBlank(line)) {
                        continue;
                    }
                    Label label = new Label(line);
                    label.setWrapText(true);
                    accountInfoProfileLines.getChildren().add(label);
                }
            }
        }
        accountInfoProfileContainer.setVisible(visible);
        accountInfoProfileContainer.setManaged(visible);
        if (visible) {
            accountInfoProfileLines.setVisible(false);
            accountInfoProfileLines.setManaged(false);
            accountInfoProfileBox.setVisible(false);
            accountInfoProfileBox.setManaged(false);
            updateProfileToggleIcon(false);
            accountInfoProfileTitleLabel.setText(PROFILE_DATA_TITLE);
        }
    }

    private String formatProfileJsonForDisplay(String rawJson) {
        if (isBlank(rawJson)) {
            return "";
        }
        String trimmed = rawJson.trim();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                List<String> lines = new ArrayList<>();
                flattenJson("", array, lines);
                return String.join("\n", lines);
            }
            JSONObject obj = new JSONObject(trimmed);
            List<String> lines = new ArrayList<>();
            flattenJson("", obj, lines);
            return String.join("\n", lines);
        } catch (Exception _) {
            return trimmed;
        }
    }

    private void flattenJson(String path, Object value, List<String> lines) {
        if (value == null || value == JSONObject.NULL) {
            addLine(path, "null", lines);
            return;
        }
        if (value instanceof JSONObject obj) {
            for (String key : obj.keySet().stream().sorted().toList()) {
                String newPath = path.isBlank() ? key : path + "." + key;
                flattenJson(newPath, obj.opt(key), lines);
            }
            return;
        }
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                String newPath = path + "[" + i + "]";
                flattenJson(newPath, array.opt(i), lines);
            }
            return;
        }
        addLine(path, String.valueOf(value), lines);
    }

    private void addLine(String key, String value, List<String> lines) {
        if (isBlank(key)) {
            return;
        }
        lines.add(key + ": " + value);
    }

    private void updateStatusIndicator(String statusText) {
        AccountInfoUiUtil.StatusState state = AccountInfoUiUtil.resolveStatusState(statusText);
        String color = AccountInfoUiUtil.colorForStatus(state);
        AccountInfoUiUtil.applyIndicator(accountInfoStatusIndicator, color, state != AccountInfoUiUtil.StatusState.UNKNOWN);
    }

    private void updateExpiryIndicator(Instant instant, boolean hasValue) {
        if (!hasValue || instant == null) {
            AccountInfoUiUtil.applyIndicator(accountInfoExpiryIndicator, AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.UNKNOWN), false);
            return;
        }
        AccountInfoUiUtil.ExpiryState state = AccountInfoUiUtil.resolveExpiryState(instant);
        String color = AccountInfoUiUtil.colorForExpiry(state);
        AccountInfoUiUtil.applyIndicator(accountInfoExpiryIndicator, color, true);
    }

    private void loadXtremeCredentialsFromAccount(Account account) {
        if (account == null) {
            return;
        }
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(account.getXtremeCredentialsJson());
        if (entries.isEmpty() && isNotBlank(account.getUsername()) && isNotBlank(account.getPassword())) {
            entries = new ArrayList<>();
            entries.add(new XtremeCredentialsJson.Entry(account.getUsername(), account.getPassword(), true));
        } else {
            entries = XtremeCredentialsJson.normalize(entries, account.getUsername());
        }
        xtremeCredentials = entries;
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        xtremeDefaultUsername = defaultEntry != null ? defaultEntry.username() : account.getUsername();
        refreshXtremeUsernameItems();
    }

    private void applyXtremeCredentialsToAccount(Account account) {
        if (account == null || account.getType() != AccountType.XTREME_API) {
            return;
        }
        String selectedUsername = xtremeUsername.getValue();
        String selectedPassword = password.getText();
        List<XtremeCredentialsJson.Entry> entries = new ArrayList<>(xtremeCredentials);
        if (entries.isEmpty() && isNotBlank(selectedUsername) && isNotBlank(selectedPassword)) {
            entries.add(new XtremeCredentialsJson.Entry(selectedUsername, selectedPassword, true));
        } else if (isNotBlank(selectedUsername) && isNotBlank(selectedPassword)) {
            boolean exists = entries.stream().anyMatch(entry ->
                    entry.username().equals(selectedUsername) && entry.password().equals(selectedPassword));
            if (!exists) {
                entries.add(new XtremeCredentialsJson.Entry(selectedUsername, selectedPassword, entries.isEmpty()));
            }
        }

        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(entries, isNotBlank(selectedUsername) ? selectedUsername : xtremeDefaultUsername);
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(normalized);
        if (defaultEntry != null) {
            account.setUsername(defaultEntry.username());
            account.setPassword(defaultEntry.password());
            xtremeDefaultUsername = defaultEntry.username();
        }
        account.setXtremeCredentialsJson(XtremeCredentialsJson.toJson(normalized));
        xtremeCredentials = normalized;
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

            saveButton.setDisable(true);

            Account account = getAccountFromForm();
            applyXtremeCredentialsToAccount(account);
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
        } catch (Exception _) {
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
            Platform.runLater(() -> loadXtremeCredentialsFromAccount(account));
        }
        applyAccountInfo(accountInfoService.getByAccountId(accountId));
        ensureAccountInfoSectionVisibility(account.getType());
        updateButtonState();
    }
}
