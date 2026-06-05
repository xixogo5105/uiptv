package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.XtremeCredentialsJson;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.util.StringUtils.isBlank;

public class XtremeCredentialsManagementPopup extends VBox {
    private final Stage stage;
    private final ListView<CredentialItem> credentialListView = new ListView<>();
    private final TextField usernameField = new TextField();
    private final TextField passwordField = new TextField();
    private final Button addButton = new Button(I18n.tr("autoAdd"));
    private final Button updateButton = new Button(I18n.tr("commonSave"));
    private final Button removeButton = new Button(I18n.tr("autoRemoveSelected"));
    private final Button setDefaultButton = new Button(I18n.tr("autoSetAsDefault"));
    private final Button saveButton = new Button(I18n.tr("autoSaveClose"));
    private final Button closeButton = new Button(I18n.tr("autoCancel"));
    private final CheckBox selectAllCheckBox = new CheckBox(I18n.tr("autoSelectAll"));

    private ObservableList<CredentialItem> credentialItems;
    private String defaultUsername;
    private final BiConsumer<List<XtremeCredentialsJson.Entry>, String> onSave;

    public XtremeCredentialsManagementPopup(Stage owner,
                                            List<XtremeCredentialsJson.Entry> initialEntries,
                                            String currentDefaultUsername,
                                            BiConsumer<List<XtremeCredentialsJson.Entry>, String> onSave) {
        this.defaultUsername = currentDefaultUsername;
        this.onSave = onSave;
        List<CredentialItem> items = new ArrayList<>();
        if (initialEntries != null) {
            for (XtremeCredentialsJson.Entry entry : initialEntries) {
                if (entry == null || isBlank(entry.username()) || isBlank(entry.password())) {
                    continue;
                }
                items.add(new CredentialItem(entry.username(), entry.password()));
                if (entry.isDefault()) {
                    defaultUsername = entry.username();
                }
            }
        }
        this.credentialItems = FXCollections.observableArrayList(items);
        stage = createStage(owner);
        configureLayout();
        configureSelectionHandling();
        configureListView();
        configureButtons();
        buildContent();
        updateActionButtons();
        stage.setScene(createScene(owner));
    }

    public void show() {
        stage.show();
    }

    private Stage createStage(Stage owner) {
        Stage popupStage = new Stage();
        popupStage.initOwner(owner);
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle(I18n.tr("autoManage") + " Xtreme");
        return popupStage;
    }

    private void configureLayout() {
        getStyleClass().addAll("management-popup-root", "xtreme-credentials-popup");
        setPadding(new Insets(18));
        setSpacing(14);
    }

    private void configureSelectionHandling() {
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            for (CredentialItem item : credentialItems) {
                item.setSelected(selected);
            }
        });
        credentialListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            usernameField.setText(newVal.getUsername());
            passwordField.setText(newVal.getPassword());
        });
    }

    private void configureListView() {
        credentialListView.setItems(credentialItems);
        credentialListView.setCellFactory(param -> new CredentialListCell());
        credentialListView.getStyleClass().add("management-list-view");
        VBox.setVgrow(credentialListView, Priority.ALWAYS);
        usernameField.setPromptText(I18n.tr("manageUserNamePrompt"));
        passwordField.setPromptText(I18n.tr("managePasswordPrompt"));
        usernameField.getStyleClass().add("management-popup-text-field");
        passwordField.getStyleClass().add("management-popup-text-field");
        credentialItems.addListener((javafx.collections.ListChangeListener<CredentialItem>) change -> updateActionButtons());
    }

    private void configureButtons() {
        addButton.setOnAction(e -> addCredential());
        updateButton.setOnAction(e -> updateSelectedCredential());
        removeButton.setOnAction(e -> removeCredentials());
        setDefaultButton.setOnAction(e -> setDefaultCredential());
        saveButton.setOnAction(e -> saveAndClose());
        closeButton.setOnAction(e -> stage.close());
    }

    private void buildContent() {
        HBox actionBox = new HBox(10, removeButton, setDefaultButton);
        actionBox.getStyleClass().add("management-popup-action-strip");
        HBox bottomBox = new HBox(10, saveButton, closeButton);
        bottomBox.getStyleClass().add("management-popup-footer");
        bottomBox.setAlignment(Pos.CENTER_RIGHT);

        HBox inputRow = new HBox(10, usernameField, passwordField);
        HBox.setHgrow(usernameField, Priority.ALWAYS);
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        HBox inputActions = new HBox(10, addButton, updateButton);
        inputActions.getStyleClass().add("management-popup-action-strip");

        Label addTitle = new Label(I18n.tr("autoAddNew"));
        addTitle.getStyleClass().add("management-popup-section-title");

        VBox listCard = new VBox(10, selectAllCheckBox, credentialListView);
        listCard.getStyleClass().add("management-popup-card");
        listCard.setMaxWidth(Double.MAX_VALUE);
        listCard.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(listCard, Priority.ALWAYS);

        VBox addCard = new VBox(10, addTitle, inputRow, inputActions);
        addCard.getStyleClass().add("management-popup-card");
        addCard.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(
                buildHeader(),
                listCard,
                actionBox,
                addCard,
                bottomBox
        );
    }

    private VBox buildHeader() {
        Label title = new Label(I18n.tr("autoManage") + " Xtreme");
        title.getStyleClass().add("management-popup-title");

        VBox header = new VBox(2, title);
        header.getStyleClass().add("management-popup-header");
        return header;
    }

    private Scene createScene(Stage owner) {
        Scene scene = new Scene(this, 480, 520);
        UiI18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        return scene;
    }

    private void addCredential() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (isBlank(username) || isBlank(password)) {
            showAlert("Username and password are required.");
            return;
        }
        boolean exists = credentialItems.stream().anyMatch(item ->
                username.equals(item.getUsername()) && password.equals(item.getPassword()));
        if (exists) {
            return;
        }
        credentialItems.add(new CredentialItem(username, password));
        if (isBlank(defaultUsername)) {
            defaultUsername = username;
        }
        usernameField.clear();
        passwordField.clear();
        credentialListView.refresh();
        updateActionButtons();
    }

    private void updateSelectedCredential() {
        CredentialItem selected = credentialListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select an entry to update.");
            return;
        }
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (isBlank(username) || isBlank(password)) {
            showAlert("Username and password are required.");
            return;
        }
        boolean duplicate = credentialItems.stream()
                .anyMatch(item -> item != selected && username.equals(item.getUsername()) && password.equals(item.getPassword()));
        if (duplicate) {
            return;
        }
        boolean wasDefault = selected.getUsername().equals(defaultUsername);
        selected.setUsername(username);
        selected.setPassword(password);
        if (wasDefault) {
            defaultUsername = username;
        }
        credentialListView.refresh();
    }

    private void removeCredentials() {
        List<CredentialItem> selectedItems = credentialItems.stream().filter(CredentialItem::isSelected).toList();

        if (selectedItems.isEmpty()) {
            showAlert(I18n.tr("autoNoItemsSelectedForRemoval"));
            return;
        }

        if (selectedItems.size() == credentialItems.size()) {
            showAlert("Cannot remove all credentials.");
            return;
        }

        boolean removingDefault = selectedItems.stream().anyMatch(item -> item.getUsername().equals(defaultUsername));
        credentialItems.removeAll(selectedItems);

        if (removingDefault) {
            if (!credentialItems.isEmpty()) {
                defaultUsername = credentialItems.getFirst().getUsername();
            } else {
                defaultUsername = null;
            }
        }

        selectAllCheckBox.setSelected(false);
        credentialListView.refresh();
        updateActionButtons();
    }

    private void setDefaultCredential() {
        CredentialItem selected = credentialListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select an entry to set as default.");
            return;
        }
        defaultUsername = selected.getUsername();
        credentialListView.refresh();
        updateActionButtons();
    }

    private void updateActionButtons() {
        boolean singleOrLess = credentialItems.size() <= 1;
        removeButton.setDisable(singleOrLess);
        setDefaultButton.setDisable(singleOrLess);
        selectAllCheckBox.setDisable(singleOrLess);
    }

    private void saveAndClose() {
        if (onSave != null) {
            List<XtremeCredentialsJson.Entry> entries = credentialItems.stream()
                    .map(item -> new XtremeCredentialsJson.Entry(item.getUsername(), item.getPassword(),
                            item.getUsername().equals(defaultUsername)))
                    .toList();
            onSave.accept(entries, defaultUsername);
        }
        stage.close();
    }

    private void showAlert(String message) {
        showErrorAlert(message);
    }

    private static class CredentialItem {
        private final StringProperty username = new SimpleStringProperty();
        private final StringProperty password = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        CredentialItem(String username, String password) {
            this.username.set(username);
            this.password.set(password);
        }

        public String getUsername() {
            return username.get();
        }

        public void setUsername(String username) {
            this.username.set(username);
        }

        public String getPassword() {
            return password.get();
        }

        public void setPassword(String password) {
            this.password.set(password);
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
    }

    private class CredentialListCell extends ListCell<CredentialItem> {
        private final CheckBox checkBox = new CheckBox();
        private final TextFlow textFlow = new TextFlow();
        private BooleanProperty currentBoundProperty;

        @Override
        protected void updateItem(CredentialItem item, boolean empty) {
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

        private void bindSelection(CredentialItem item) {
            currentBoundProperty = item.selectedProperty();
            checkBox.selectedProperty().bindBidirectional(currentBoundProperty);
        }

        private HBox buildGraphic(CredentialItem item) {
            textFlow.getChildren().setAll(new Text(item.getUsername()));
            if (item.getUsername().equals(defaultUsername)) {
                textFlow.getChildren().addAll(new Text(" ("), defaultTextNode(), new Text(")"));
            }
            HBox topRow = new HBox(10, checkBox, textFlow);
            topRow.setAlignment(Pos.CENTER_LEFT);
            VBox container = new VBox(4, topRow);
            return new HBox(container);
        }

        private Text defaultTextNode() {
            Text defaultText = new Text("Default");
            defaultText.getStyleClass().add("default-text");
            return defaultText;
        }
    }

    void setInputForTest(String username, String password) {
        usernameField.setText(username);
        passwordField.setText(password);
    }

    void addCredentialForTest() {
        addCredential();
    }

    void selectIndexForTest(int index) {
        credentialListView.getSelectionModel().select(index);
    }

    void updateSelectedForTest() {
        updateSelectedCredential();
    }

    void setItemSelectedForTest(int index, boolean selected) {
        if (index < 0 || index >= credentialItems.size()) {
            return;
        }
        credentialItems.get(index).setSelected(selected);
    }

    void selectAllForTest(boolean selected) {
        selectAllCheckBox.setSelected(selected);
        selectAllCheckBox.fire();
    }

    void removeSelectedForTest() {
        removeCredentials();
    }

    void setDefaultForTest() {
        setDefaultCredential();
    }

    boolean isDeleteDisabledForTest() {
        return removeButton.isDisable();
    }

    boolean isDefaultDisabledForTest() {
        return setDefaultButton.isDisable();
    }

    int itemCountForTest() {
        return credentialItems.size();
    }

    String defaultUsernameForTest() {
        return defaultUsername;
    }

    List<XtremeCredentialsJson.Entry> entriesForTest() {
        return credentialItems.stream()
                .map(item -> new XtremeCredentialsJson.Entry(item.getUsername(), item.getPassword(), item.getUsername().equals(defaultUsername)))
                .toList();
    }
}
