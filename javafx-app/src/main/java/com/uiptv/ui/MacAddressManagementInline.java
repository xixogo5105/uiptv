package com.uiptv.ui;

import com.uiptv.util.I18n;
import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.AccountCopyUtil;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.util.StringUtils.SPACE;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class MacAddressManagementInline extends VBox {

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
    private final Runnable closeHandler;

    public MacAddressManagementInline(Account baseAccount,
                                      List<String> initialMacs,
                                      String currentDefaultMac,
                                      BiConsumer<List<String>, String> onSave,
                                      Runnable closeHandler) {
        this.defaultMac = currentDefaultMac;
        this.baseAccount = baseAccount;
        this.onSave = onSave;
        this.closeHandler = closeHandler == null ? () -> { } : closeHandler;
        this.macItems = FXCollections.observableArrayList(initialMacs.stream().map(MacItem::new).toList());
        configureLayout();
        configureSelectionHandling();
        configureListView();
        configureButtons();
        buildContent();
        updateActionButtons();
    }

    private void configureLayout() {
        getStyleClass().addAll("management-popup-root", "mac-management-popup");
        setPadding(new Insets(18));
        setSpacing(14);
        setFillWidth(true);
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
        macListView.getStyleClass().addAll("management-list-view", "mac-management-list");
        macListView.setPlaceholder(createMacPlaceholder());
        VBox.setVgrow(macListView, Priority.ALWAYS);
        addMacField.setPromptText(I18n.tr("autoAddMACAddressEsCommaSeparated"));
        addMacField.getStyleClass().add("management-popup-text-field");
        macItems.addListener((javafx.collections.ListChangeListener<MacItem>) change -> updateActionButtons());
    }

    private void configureButtons() {
        addButton.getStyleClass().add("prominent");
        removeButton.getStyleClass().add("dangerous");
        saveButton.getStyleClass().add("prominent");
        addButton.setOnAction(e -> addMacs());
        removeButton.setOnAction(e -> removeMacs());
        setDefaultButton.setOnAction(e -> setDefaultMac());
        verifyInfoButton.setOnAction(e -> verifyMacInfo());
        saveButton.setOnAction(e -> saveAndClose());
        closeButton.setOnAction(e -> closeHandler.run());
    }

    private void buildContent() {
        VBox header = createHeader();

        HBox selectionRow = new HBox(10, selectAllCheckBox);
        selectionRow.setAlignment(Pos.CENTER_LEFT);
        selectionRow.getStyleClass().add("management-popup-toolbar");

        VBox listCard = new VBox(10, selectionRow, macListView);
        listCard.getStyleClass().add("management-popup-card");
        VBox.setVgrow(listCard, Priority.ALWAYS);

        HBox actionBox = new HBox(10, removeButton, setDefaultButton, verifyInfoButton);
        actionBox.getStyleClass().add("management-popup-action-strip");
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Label addLabel = new Label(I18n.tr("autoAddNew"));
        addLabel.getStyleClass().add("management-popup-section-title");
        HBox addBox = new HBox(10, addMacField, addButton);
        addBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addMacField, Priority.ALWAYS);

        VBox addCard = new VBox(10, addLabel, addBox);
        addCard.getStyleClass().add("management-popup-card");

        HBox bottomBox = new HBox(10, saveButton, closeButton);
        bottomBox.getStyleClass().add("management-popup-footer");
        bottomBox.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(header, listCard, actionBox, addCard, bottomBox);
    }

    private VBox createHeader() {
        Label title = new Label(I18n.tr("autoManageMACAddresses"));
        title.getStyleClass().add("management-popup-title");

        Label accountLabel = new Label(baseAccount == null ? "" : baseAccount.getAccountName());
        accountLabel.getStyleClass().add("management-popup-subtitle");
        accountLabel.setVisible(baseAccount != null && isNotBlank(baseAccount.getAccountName()));
        accountLabel.setManaged(accountLabel.isVisible());

        VBox header = new VBox(2, title, accountLabel);
        header.getStyleClass().add("management-popup-header");
        return header;
    }

    private Label createMacPlaceholder() {
        Label label = new Label(I18n.tr("autoNoMACAddressesToVerify"));
        label.getStyleClass().add("management-popup-placeholder");
        return label;
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
        updateActionButtons();
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
            if (!showConfirmationAlert(I18n.tr("autoRemovingDefaultMacWarning"))) {
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
        updateActionButtons();
    }

    private void setDefaultMac() {
        MacItem selected = macListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            defaultMac = selected.getMac();
            macListView.refresh();
            updateActionButtons();
        } else {
            showAlert(I18n.tr("autoPleaseSelectMacAddressToSetDefault"));
        }
    }

    private void updateActionButtons() {
        boolean singleOrLess = macItems.size() <= 1;
        removeButton.setDisable(singleOrLess);
        setDefaultButton.setDisable(singleOrLess);
        selectAllCheckBox.setDisable(singleOrLess);
    }

    private void saveAndClose() {
        if (onSave != null) {
            List<String> macStrings = macItems.stream().map(MacItem::getMac).toList();
            onSave.accept(macStrings, defaultMac);
        }
        closeHandler.run();
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
        return AccountCopyUtil.copyForMac(baseAccount, mac);
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
        showErrorAlert(message);
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
                getStyleClass().remove("mac-management-list-cell");
                return;
            }
            if (!getStyleClass().contains("mac-management-list-cell")) {
                getStyleClass().add("mac-management-list-cell");
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
            Label macLabel = new Label(item.getMac());
            macLabel.getStyleClass().add("mac-management-address");
            macLabel.setMinWidth(0);
            macLabel.setMaxWidth(Double.MAX_VALUE);

            Label defaultBadge = new Label("Default");
            defaultBadge.getStyleClass().add("mac-default-pill");
            boolean isDefault = item.getMac().equalsIgnoreCase(defaultMac);
            defaultBadge.setVisible(isDefault);
            defaultBadge.setManaged(isDefault);

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
            statusBox.getStyleClass().add("mac-management-meta-line");
            statusBox.setAlignment(Pos.CENTER_LEFT);
            HBox expiryBox = new HBox(6, expiryIndicator, expiryLabel);
            expiryBox.getStyleClass().add("mac-management-meta-line");
            expiryBox.setAlignment(Pos.CENTER_LEFT);
            VBox infoBox = new VBox(2, statusBox, expiryBox);
            infoBox.getStyleClass().add("mac-management-info");

            boolean hasStatus = isNotBlank(item.getStatusText());
            boolean hasExpiry = isNotBlank(item.getExpiryText());
            statusBox.setVisible(hasStatus);
            statusBox.setManaged(hasStatus);
            expiryBox.setVisible(hasExpiry);
            expiryBox.setManaged(hasExpiry);
            infoBox.setVisible(hasStatus || hasExpiry);
            infoBox.setManaged(hasStatus || hasExpiry);

            HBox topRow = new HBox(10, checkBox, macLabel, defaultBadge);
            topRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(macLabel, Priority.ALWAYS);
            VBox container = new VBox(4, topRow, infoBox);
            container.setMinWidth(0);
            container.setMaxWidth(Double.MAX_VALUE);

            HBox row = new HBox(container);
            row.getStyleClass().add("mac-management-row-content");
            HBox.setHgrow(container, Priority.ALWAYS);
            return row;
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
