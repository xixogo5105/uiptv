package com.uiptv.ui;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.SPACE;
import static com.uiptv.util.StringUtils.isBlank;

public class MacAddressManagementPopup extends Stage {

    private final ListView<MacItem> macListView = new ListView<>();
    private final TextField addMacField = new TextField();
    private final Button addButton = new Button("Add");
    private final Button removeButton = new Button("Remove Selected");
    private final Button setDefaultButton = new Button("Set as Default");
    private final Button saveButton = new Button("Save & Close");
    private final Button closeButton = new Button("Cancel");
    private final CheckBox selectAllCheckBox = new CheckBox("Select All");

    private ObservableList<MacItem> macItems;
    private String defaultMac;
    private final BiConsumer<List<String>, String> onSave;

    public MacAddressManagementPopup(List<String> initialMacs, String currentDefaultMac, BiConsumer<List<String>, String> onSave) {
        this.defaultMac = currentDefaultMac;
        this.onSave = onSave;
        this.macItems = FXCollections.observableArrayList(
                initialMacs.stream().map(MacItem::new).collect(Collectors.toList())
        );

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Manage MAC Addresses");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            for (MacItem item : macItems) {
                item.setSelected(selected);
            }
        });

        macListView.setItems(macItems);
        macListView.setCellFactory(param -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final TextFlow textFlow = new TextFlow();
            private BooleanProperty currentBoundProperty;

            @Override
            protected void updateItem(MacItem item, boolean empty) {
                super.updateItem(item, empty);

                if (currentBoundProperty != null) {
                    checkBox.selectedProperty().unbindBidirectional(currentBoundProperty);
                    currentBoundProperty = null;
                }

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    currentBoundProperty = item.selectedProperty();
                    checkBox.selectedProperty().bindBidirectional(currentBoundProperty);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().add(new Text(item.getMac()));

                    if (item.getMac().equalsIgnoreCase(defaultMac)) {
                        textFlow.getChildren().add(new Text(" ("));
                        Text defaultText = new Text("Default");
                        defaultText.setStyle("-fx-font-weight: bold; -fx-fill: darkgreen;");
                        textFlow.getChildren().add(defaultText);
                        textFlow.getChildren().add(new Text(")"));
                    }

                    HBox hBox = new HBox(10, checkBox, textFlow);
                    hBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(hBox);
                }
            }
        });
        VBox.setVgrow(macListView, Priority.ALWAYS);

        addMacField.setPromptText("Add MAC Address(es) (comma separated)");

        addButton.setOnAction(e -> addMacs());
        removeButton.setOnAction(e -> removeMacs());
        setDefaultButton.setOnAction(e -> setDefaultMac());
        saveButton.setOnAction(e -> saveAndClose());
        closeButton.setOnAction(e -> close());

        HBox addBox = new HBox(10, addMacField, addButton);
        HBox.setHgrow(addMacField, Priority.ALWAYS);

        HBox actionBox = new HBox(10, removeButton, setDefaultButton);
        HBox bottomBox = new HBox(10, saveButton, closeButton);
        bottomBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        layout.getChildren().addAll(selectAllCheckBox, macListView, actionBox, new Separator(), new Label("Add New:"), addBox, new Separator(), bottomBox);

        Scene scene = new Scene(layout, 450, 500);
        setScene(scene);
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
        List<MacItem> selectedItems = macItems.stream().filter(MacItem::isSelected).collect(Collectors.toList());

        if (selectedItems.isEmpty()) {
            showAlert("No items selected for removal.");
            return;
        }

        if (selectedItems.size() == macItems.size()) {
            showAlert("Cannot remove all MAC addresses. At least one must remain.");
            return;
        }

        boolean removingDefault = selectedItems.stream().anyMatch(item -> item.getMac().equalsIgnoreCase(defaultMac));

        if (removingDefault) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "You are removing the default MAC address. The first available MAC will become the new default. Continue?",
                    ButtonType.YES, ButtonType.NO);
            alert.initOwner(this);
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
            showAlert("Please select a MAC address from the list to set as default.");
        }
    }

    private void saveAndClose() {
        if (onSave != null) {
            List<String> macStrings = macItems.stream().map(MacItem::getMac).collect(Collectors.toList());
            onSave.accept(macStrings, defaultMac);
        }
        close();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.initOwner(this);
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
}