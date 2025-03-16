package com.uiptv.ui;

import com.uiptv.model.BookmarkCategory;
import com.uiptv.service.BookmarkService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

public class CategoryManagementPopup extends VBox {
    private ListView<BookmarkItem> categoryListView = new ListView<>();
    private TextField categoryNameField = new TextField();
    private Button addButton = new Button("Add");
    private Button removeButton = new Button("Remove");
    private BookmarkChannelListUI parent;

    public CategoryManagementPopup(BookmarkChannelListUI parent) {
        this.parent = parent;
        setPadding(new Insets(10));
        setSpacing(10);

        categoryListView.setItems(FXCollections.observableArrayList(getBookmarkItems()));
        categoryNameField.setPromptText("Category Name");

        addButton.setOnAction(event -> addCategory());
        removeButton.setOnAction(event -> removeCategory());
        categoryListView.setCellFactory(new Callback<ListView<BookmarkItem>, ListCell<BookmarkItem>>() {
            @Override
            public ListCell<BookmarkItem> call(ListView<BookmarkItem> listView) {
                return new ListCell<BookmarkItem>() {
                    @Override
                    protected void updateItem(BookmarkItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getName());
                        }
                    }
                };
            }
        });
        getChildren().addAll(new Label("Manage Categories"), categoryListView, categoryNameField, addButton, removeButton);
    }

    private void addCategory() {
        String categoryName = categoryNameField.getText();
        if (!categoryName.isEmpty()) {
            BookmarkService.getInstance().addCategory(new BookmarkCategory(null, categoryName));
            categoryListView.setItems(FXCollections.observableArrayList(getBookmarkItems()));
            categoryNameField.clear();
            parent.populateCategoryTabPane();
        }
    }

    private void removeCategory() {
        BookmarkItem selectedItem = categoryListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            BookmarkService.getInstance().removeCategory(new BookmarkCategory(selectedItem.getId(), selectedItem.getName()));
            categoryListView.setItems(FXCollections.observableArrayList(getBookmarkItems()));
            parent.populateCategoryTabPane();
        }
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