package com.uiptv.ui;

import com.uiptv.util.I18n;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

public class MacAddressManagementPopup extends VBox {

    private final Stage stage;
    private final ListView<MacItem> macListView = new ListView<>();
    private final TextField addMacField = new TextField();
    private final Button addButton = new Button(I18n.tr("autoAdd"));
    private final Button removeButton = new Button(I18n.tr("autoRemoveSelected"));
    private final Button setDefaultButton = new Button(I18n.tr("autoSetAsDefault"));
    private final Button saveButton = new Button(I18n.tr("autoSaveClose"));
    private final Button closeButton = new Button(I18n.tr("autoCancel"));
    private final CheckBox selectAllCheckBox = new CheckBox(I18n.tr("autoSelectAll"));

    private ObservableList<MacItem> macItems;
    private String defaultMac;
    private final BiConsumer<List<String>, String> onSave;

    public MacAddressManagementPopup(Stage owner, List<String> initialMacs, String currentDefaultMac, BiConsumer<List<String>, String> onSave) {
        this.defaultMac = currentDefaultMac;
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
        saveButton.setOnAction(e -> saveAndClose());
        closeButton.setOnAction(e -> stage.close());
    }

    private void buildContent() {
        HBox addBox = new HBox(10, addMacField, addButton);
        HBox.setHgrow(addMacField, Priority.ALWAYS);
        HBox actionBox = new HBox(10, removeButton, setDefaultButton);
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
                defaultMac = macItems.get(0).getMac();
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

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, okButtonType());
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private static class MacItem {
        private final StringProperty mac = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

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
    }

    private class MacListCell extends ListCell<MacItem> {
        private final CheckBox checkBox = new CheckBox();
        private final TextFlow textFlow = new TextFlow();
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
            HBox hBox = new HBox(10, checkBox, textFlow);
            hBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            return hBox;
        }

        private Text defaultTextNode() {
            Text defaultText = new Text("Default");
            defaultText.getStyleClass().add("default-text");
            return defaultText;
        }
    }
}
