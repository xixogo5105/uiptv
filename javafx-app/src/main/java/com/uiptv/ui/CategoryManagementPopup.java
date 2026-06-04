package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.BookmarkCategory;
import com.uiptv.service.BookmarkService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class CategoryManagementPopup extends VBox {
    private final ListView<BookmarkItem> categoryListView = new ListView<>();
    private final TextField categoryNameField = new TextField();
    private final Button addButton = new Button(I18n.tr("autoAdd"));
    private final Button removeButton = new Button(I18n.tr("autoRemove"));
    private final BookmarkChannelListUI parent;

    public CategoryManagementPopup(BookmarkChannelListUI parent) {
        this.parent = parent;
        configureLayout();
        configureControls();
        buildContent();
    }

    private void configureLayout() {
        getStyleClass().addAll("management-popup-root", "category-management-popup");
        setPadding(new Insets(18));
        setSpacing(14);
        setFillWidth(true);
    }

    private void configureControls() {
        categoryListView.setItems(FXCollections.observableArrayList(getBookmarkItems()));
        categoryListView.getStyleClass().addAll("management-list-view", "category-management-list");
        categoryListView.setPlaceholder(createPlaceholderLabel());
        VBox.setVgrow(categoryListView, Priority.ALWAYS);

        categoryNameField.setPromptText(I18n.tr("autoCategoryName"));
        categoryNameField.getStyleClass().add("management-popup-text-field");
        categoryNameField.setOnAction(event -> addCategory());

        addButton.getStyleClass().add("prominent");
        addButton.setOnAction(event -> addCategory());
        addButton.setDefaultButton(true);

        removeButton.getStyleClass().add("dangerous");
        removeButton.setOnAction(event -> removeCategory());
        removeButton.setDisable(true);

        categoryListView.getSelectionModel().selectedItemProperty().addListener((_, _, selected) ->
                removeButton.setDisable(selected == null));
        categoryListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(BookmarkItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    setGraphic(createCategoryCellGraphic(item));
                }
            }
        });
    }

    private void buildContent() {
        Label title = new Label(I18n.tr("autoManageCategories"));
        title.getStyleClass().add("management-popup-title");

        VBox header = new VBox(2, title);
        header.getStyleClass().add("management-popup-header");

        VBox listCard = new VBox(10, categoryListView);
        listCard.getStyleClass().add("management-popup-card");
        VBox.setVgrow(listCard, Priority.ALWAYS);

        HBox addRow = new HBox(10, categoryNameField, addButton);
        addRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(categoryNameField, Priority.ALWAYS);

        VBox addCard = new VBox(10, addRow);
        addCard.getStyleClass().add("management-popup-card");

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(10, footerSpacer, removeButton);
        footer.getStyleClass().add("management-popup-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(header, listCard, addCard, footer);
    }

    private Label createPlaceholderLabel() {
        Label label = new Label(I18n.tr("autoNothingFoundFor", I18n.tr("autoCategories")));
        label.getStyleClass().add("management-popup-placeholder");
        return label;
    }

    private HBox createCategoryCellGraphic(BookmarkItem item) {
        Label name = new Label(item.getName());
        name.getStyleClass().add("category-management-name");
        name.setMaxWidth(Double.MAX_VALUE);

        Label chip = new Label(I18n.tr("autoCategories"));
        chip.getStyleClass().add("management-popup-chip");

        HBox row = new HBox(10, name, chip);
        row.getStyleClass().add("category-management-cell-content");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(name, Priority.ALWAYS);
        return row;
    }

    private void addCategory() {
        String categoryName = categoryNameField.getText();
        if (categoryName != null && !categoryName.isBlank()) {
            BookmarkService.getInstance().addCategory(new BookmarkCategory(null, categoryName.trim()));
            refreshCategories();
            categoryNameField.clear();
            parent.populateCategoryTabPane();
        }
    }

    private void removeCategory() {
        BookmarkItem selectedItem = categoryListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            BookmarkService.getInstance().removeCategory(new BookmarkCategory(selectedItem.getId(), selectedItem.getName()));
            refreshCategories();
            parent.populateCategoryTabPane();
        }
    }

    private void refreshCategories() {
        categoryListView.setItems(FXCollections.observableArrayList(getBookmarkItems()));
        removeButton.setDisable(categoryListView.getSelectionModel().getSelectedItem() == null);
    }

    private List<BookmarkItem> getBookmarkItems() {
        List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
        List<BookmarkItem> items = new ArrayList<>();
        for (BookmarkCategory category : categories) {
            items.add(new BookmarkItem(category.getId(), category.getName()));
        }
        return items;
    }

    private class BookmarkItem {
        private final String id;
        private final String name;

        public BookmarkItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
