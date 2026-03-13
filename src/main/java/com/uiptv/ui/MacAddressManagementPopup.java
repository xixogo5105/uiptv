package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.service.HandshakeService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static com.uiptv.widget.UIptvAlert.okButtonType;
import static com.uiptv.util.StringUtils.SPACE;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class MacAddressManagementPopup extends VBox {

    private final Stage stage;
    private final ListView<MacItem> macListView = new ListView<>();
    private final TextField addMacField = new TextField();
    private final Button addButton = new Button(I18n.tr("autoAdd"));
    private final Button removeButton = new Button(I18n.tr("autoRemoveSelected"));
    private final Button setDefaultButton = new Button(I18n.tr("autoSetAsDefault"));
    private final Button verifyInfoButton = new Button(I18n.tr("autoVerify"));
    private final Button saveButton = new Button(I18n.tr("autoSaveClose"));
    private final Button closeButton = new Button(I18n.tr("autoCancel"));
    private final CheckBox selectAllCheckBox = new CheckBox(I18n.tr("autoSelectAll"));

    private ObservableList<MacItem> macItems;
    private String defaultMac;
    private final Account baseAccount;
    private final BiConsumer<List<String>, String> onSave;

    public MacAddressManagementPopup(Stage owner, Account baseAccount, List<String> initialMacs, String currentDefaultMac, BiConsumer<List<String>, String> onSave) {
        this.defaultMac = currentDefaultMac;
        this.baseAccount = baseAccount;
        this.onSave = onSave;
        this.macItems = FXCollections.observableArrayList(initialMacs.stream().map(MacItem::new).toList());
        stage = createStage(owner);
        configureLayout();
        configureSelectionHandling();
        configureListView();
        configureButtons();
        buildContent();
        stage.setScene(createScene(owner));
    }

    public void show() {
        stage.show();
    }

    private Stage createStage(Stage owner) {
        Stage popupStage = new Stage();
        popupStage.initOwner(owner);
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle(I18n.tr("autoManageMACAddresses"));
        return popupStage;
    }

    private void configureLayout() {
        setPadding(new Insets(10));
        setSpacing(10);
    }

    private void configureSelectionHandling() {
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            for (MacItem item : macItems) {
                item.setSelected(selected);
            }
        });
    }

    private void configureListView() {
        macListView.setItems(macItems);
        macListView.setCellFactory(param -> new MacListCell());
        VBox.setVgrow(macListView, Priority.ALWAYS);
        addMacField.setPromptText(I18n.tr("autoAddMACAddressEsCommaSeparated"));
    }

    private void configureButtons() {
        addButton.setOnAction(e -> addMacs());
        removeButton.setOnAction(e -> removeMacs());
        setDefaultButton.setOnAction(e -> setDefaultMac());
        verifyInfoButton.setOnAction(e -> verifyMacInfo());
        saveButton.setOnAction(e -> saveAndClose());
        closeButton.setOnAction(e -> stage.close());
    }

    private void buildContent() {
        HBox addBox = new HBox(10, addMacField, addButton);
        HBox.setHgrow(addMacField, Priority.ALWAYS);
        HBox actionBox = new HBox(10, removeButton, setDefaultButton, verifyInfoButton);
        HBox bottomBox = new HBox(10, saveButton, closeButton);
        bottomBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        getChildren().addAll(selectAllCheckBox, macListView, actionBox, new Separator(), new Label(I18n.tr("autoAddNew")), addBox, new Separator(), bottomBox);
    }

    private Scene createScene(Stage owner) {
        Scene scene = new Scene(this, 450, 500);
        I18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        return scene;
    }

    private void addMacs() {
        String text = addMacField.getText();
        if (isBlank(text)) return;

        List<String> newMacs = Arrays.stream(text.replace(SPACE, "").split(","))
                .filter(s -> !isBlank(s))
                .toList();

        for (String mac : newMacs) {
            boolean exists = macItems.stream().anyMatch(item -> item.getMac().equalsIgnoreCase(mac));
            if (!exists) {
                macItems.add(new MacItem(mac));
            }
        }
        addMacField.clear();
    }

    private void removeMacs() {
        List<MacItem> selectedItems = macItems.stream().filter(MacItem::isSelected).toList();

        if (selectedItems.isEmpty()) {
            showAlert(I18n.tr("autoNoItemsSelectedForRemoval"));
            return;
        }

        if (selectedItems.size() == macItems.size()) {
            showAlert(I18n.tr("autoCannotRemoveAllMacAddresses"));
            return;
        }

        boolean removingDefault = selectedItems.stream().anyMatch(item -> item.getMac().equalsIgnoreCase(defaultMac));

        if (removingDefault) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    I18n.tr("autoRemovingDefaultMacWarning"),
                    ButtonType.YES, ButtonType.NO);
            alert.initOwner(stage);
            if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
        }

        macItems.removeAll(selectedItems);

        if (removingDefault) {
            if (!macItems.isEmpty()) {
                defaultMac = macItems.getFirst().getMac();
            } else {
                defaultMac = null; // Should not happen due to check above
            }
        }
        
        // Uncheck select all if items removed
        selectAllCheckBox.setSelected(false);

        macListView.refresh();
    }

    private void setDefaultMac() {
        MacItem selected = macListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            defaultMac = selected.getMac();
            macListView.refresh();
        } else {
            showAlert(I18n.tr("autoPleaseSelectMacAddressToSetDefault"));
        }
    }

    private void saveAndClose() {
        if (onSave != null) {
            List<String> macStrings = macItems.stream().map(MacItem::getMac).toList();
            onSave.accept(macStrings, defaultMac);
        }
        stage.close();
    }

    private void verifyMacInfo() {
        if (baseAccount == null || baseAccount.getType() != com.uiptv.util.AccountType.STALKER_PORTAL) {
            showAlert(I18n.tr("autoFailed"));
            return;
        }
        List<MacItem> targets = macItems.stream().filter(MacItem::isSelected).toList();
        if (targets.isEmpty()) {
            targets = macItems;
        }
        if (targets.isEmpty()) {
            return;
        }
        verifyInfoButton.setDisable(true);
        final List<MacItem> finalTargets = new java.util.ArrayList<>(targets);
        new Thread(() -> {
            try {
                for (MacItem item : finalTargets) {
                    Account account = buildAccountForMac(item.getMac());
                    AccountInfo info = HandshakeService.getInstance().fetchAccountInfo(account);
                    updateMacItemInfo(item, info);
                }
            } finally {
                javafx.application.Platform.runLater(() -> {
                    verifyInfoButton.setDisable(false);
                    macListView.refresh();
                });
            }
        }).start();
    }

    private Account buildAccountForMac(String mac) {
        Account account = new Account(
                baseAccount.getAccountName(),
                baseAccount.getUsername(),
                baseAccount.getPassword(),
                baseAccount.getUrl(),
                mac,
                baseAccount.getMacAddressList(),
                baseAccount.getSerialNumber(),
                baseAccount.getDeviceId1(),
                baseAccount.getDeviceId2(),
                baseAccount.getSignature(),
                baseAccount.getType(),
                baseAccount.getEpg(),
                baseAccount.getM3u8Path(),
                baseAccount.isPinToTop()
        );
        account.setHttpMethod(baseAccount.getHttpMethod());
        account.setTimezone(baseAccount.getTimezone());
        account.setServerPortalUrl(baseAccount.getServerPortalUrl());
        account.setAction(baseAccount.getAction());
        return account;
    }

    private void updateMacItemInfo(MacItem item, AccountInfo info) {
        String statusText = info != null && info.getAccountStatus() != null
                ? info.getAccountStatus().toDisplay()
                : "";
        String expireText = info != null ? formatDate(info.getExpireDate()) : "";
        AccountInfoUiUtil.ExpiryState expiryState = AccountInfoUiUtil.resolveExpiryState(info != null ? info.getExpireDate() : "");
        AccountInfoUiUtil.StatusState statusState = AccountInfoUiUtil.resolveStatusState(statusText);
        javafx.application.Platform.runLater(() -> {
            item.setStatusText(statusText);
            item.setExpiryText(expireText);
            item.setExpiryState(expiryState);
            item.setStatusState(statusState);
        });
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, okButtonType());
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private static class MacItem {
        private final StringProperty mac = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final StringProperty statusText = new SimpleStringProperty("");
        private final StringProperty expiryText = new SimpleStringProperty("");
        private final ObjectProperty<AccountInfoUiUtil.ExpiryState> expiryState = new SimpleObjectProperty<>(AccountInfoUiUtil.ExpiryState.UNKNOWN);
        private final ObjectProperty<AccountInfoUiUtil.StatusState> statusState = new SimpleObjectProperty<>(AccountInfoUiUtil.StatusState.UNKNOWN);

        public MacItem(String mac) {
            this.mac.set(mac);
        }

        public String getMac() {
            return mac.get();
        }

        public StringProperty macProperty() {
            return mac;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        public String getStatusText() {
            return statusText.get();
        }

        public void setStatusText(String statusText) {
            this.statusText.set(statusText);
        }

        public String getExpiryText() {
            return expiryText.get();
        }

        public void setExpiryText(String expiryText) {
            this.expiryText.set(expiryText);
        }

        public AccountInfoUiUtil.ExpiryState getExpiryState() {
            return expiryState.get();
        }

        public void setExpiryState(AccountInfoUiUtil.ExpiryState expiryState) {
            this.expiryState.set(expiryState);
        }

        public AccountInfoUiUtil.StatusState getStatusState() {
            return statusState.get();
        }

        public void setStatusState(AccountInfoUiUtil.StatusState statusState) {
            this.statusState.set(statusState);
        }
    }

    private class MacListCell extends ListCell<MacItem> {
        private final CheckBox checkBox = new CheckBox();
        private final TextFlow textFlow = new TextFlow();
        private final Label statusLabel = new Label();
        private final Label expiryLabel = new Label();
        private final Region statusIndicator = new Region();
        private final Region expiryIndicator = new Region();
        private BooleanProperty currentBoundProperty;

        @Override
        protected void updateItem(MacItem item, boolean empty) {
            super.updateItem(item, empty);
            unbindCurrentProperty();
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            bindSelection(item);
            setGraphic(buildGraphic(item));
        }

        private void unbindCurrentProperty() {
            if (currentBoundProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(currentBoundProperty);
                currentBoundProperty = null;
            }
        }

        private void bindSelection(MacItem item) {
            currentBoundProperty = item.selectedProperty();
            checkBox.selectedProperty().bindBidirectional(currentBoundProperty);
        }

        private HBox buildGraphic(MacItem item) {
            textFlow.getChildren().setAll(new Text(item.getMac()));
            if (item.getMac().equalsIgnoreCase(defaultMac)) {
                textFlow.getChildren().addAll(new Text(" ("), defaultTextNode(), new Text(")"));
            }
            statusIndicator.setMinSize(8, 8);
            statusIndicator.setPrefSize(8, 8);
            statusIndicator.setMaxSize(8, 8);
            statusIndicator.setStyle("-fx-background-radius: 6px;");

            expiryIndicator.setMinSize(8, 8);
            expiryIndicator.setPrefSize(8, 8);
            expiryIndicator.setMaxSize(8, 8);
            expiryIndicator.setStyle("-fx-background-radius: 6px;");

            statusLabel.setText(item.getStatusText());
            expiryLabel.setText(item.getExpiryText());

            applyStatusIndicator(item.getStatusState());
            applyExpiryIndicator(item.getExpiryState());

            HBox statusBox = new HBox(6, statusIndicator, statusLabel);
            statusBox.setAlignment(Pos.CENTER_LEFT);
            HBox expiryBox = new HBox(6, expiryIndicator, expiryLabel);
            expiryBox.setAlignment(Pos.CENTER_LEFT);
            VBox infoBox = new VBox(2, statusBox, expiryBox);

            boolean hasStatus = isNotBlank(item.getStatusText());
            boolean hasExpiry = isNotBlank(item.getExpiryText());
            statusBox.setVisible(hasStatus);
            statusBox.setManaged(hasStatus);
            expiryBox.setVisible(hasExpiry);
            expiryBox.setManaged(hasExpiry);
            infoBox.setVisible(hasStatus || hasExpiry);
            infoBox.setManaged(hasStatus || hasExpiry);

            HBox topRow = new HBox(10, checkBox, textFlow);
            topRow.setAlignment(Pos.CENTER_LEFT);
            VBox container = new VBox(4, topRow, infoBox);
            return new HBox(container);
        }

        private Text defaultTextNode() {
            Text defaultText = new Text("Default");
            defaultText.getStyleClass().add("default-text");
            return defaultText;
        }

        private void applyStatusIndicator(AccountInfoUiUtil.StatusState state) {
            String color = AccountInfoUiUtil.colorForStatus(state);
            boolean visible = state != AccountInfoUiUtil.StatusState.UNKNOWN;
            AccountInfoUiUtil.applyIndicator(statusIndicator, color, visible);
        }

        private void applyExpiryIndicator(AccountInfoUiUtil.ExpiryState state) {
            String color = AccountInfoUiUtil.colorForExpiry(state);
            boolean visible = state != AccountInfoUiUtil.ExpiryState.UNKNOWN;
            AccountInfoUiUtil.applyIndicator(expiryIndicator, color, visible);
        }
    }

    private String formatDate(String value) {
        return AccountInfoUiUtil.formatDate(value);
    }
}
