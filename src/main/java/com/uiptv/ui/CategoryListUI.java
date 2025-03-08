package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.ChannelService;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Cursor;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.ui.RootApplication.primaryStage;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;

public class CategoryListUI extends HBox {
    private Account account;
    private BookmarkChannelListUI bookmarkChannelListUI;
    SearchableTableView table = new SearchableTableView();
    TableColumn<CategoryItem, String> categoryTitle = new TableColumn("Categories");
    TableColumn<CategoryItem, String> categoryId = new TableColumn("");
    private static CategoryListUI instance;

    public static synchronized CategoryListUI getInstance(List<Category> list, Account account, BookmarkChannelListUI bookmarkChannelListUI) {
        if (instance == null) {
            instance = new CategoryListUI();
        }

        instance.bookmarkChannelListUI = bookmarkChannelListUI;
        List<CategoryItem> catList = new ArrayList<>();
        list.forEach(i -> catList.add(new CategoryItem(new SimpleStringProperty(i.getDbId()), new SimpleStringProperty(i.getTitle()), new SimpleStringProperty(i.getCategoryId()))));

        instance.table.setItems(FXCollections.observableArrayList(catList));
        instance.account = account;
        instance.categoryTitle.setText(account.getAccountName());
        instance.table.addTextFilter();
        return instance;
    }

    private CategoryListUI() {
        initWidgets();
    }

    private void initWidgets() {
        setSpacing(10);
        table.setEditable(true);
        table.getColumns().addAll(categoryTitle);
        categoryTitle.setVisible(true);
        categoryId.setVisible(false);
        categoryTitle.setCellValueFactory(cellData -> cellData.getValue().categoryTitleProperty());
        categoryId.setCellValueFactory(cellData -> cellData.getValue().categoryIdProperty());
        categoryTitle.setSortType(TableColumn.SortType.ASCENDING);
        categoryTitle.setSortable(true);
        getChildren().addAll(new AutoGrowVBox(5, table.getSearchTextField(), table));
        addChannelClickHandler();
    }

    private void addChannelClickHandler() {
        table.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                doRetrieveChannels((CategoryItem) table.getFocusModel().getFocusedItem());
            }
        });
        table.setRowFactory(tv -> {
            TableRow<CategoryItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    doRetrieveChannels(row.getItem());

                }
            });
            return row;
        });
    }

    private void doRetrieveChannels(CategoryItem item) {
        primaryStage.getScene().setCursor(Cursor.WAIT);
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    retrieveChannels(item);
                });
            } catch (Throwable ignored) {
            } finally {
                Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
            }

        }).start();
    }

    private synchronized void retrieveChannels(CategoryItem item) {
        try {
            this.getChildren().clear();

            getChildren().addAll(new VBox(5, table.getSearchTextField(), table), new ChannelListUI(ChannelService.getInstance().get(account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle(), account, item.getId()), account, item.getCategoryTitle(), bookmarkChannelListUI, account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class CategoryItem {

        private final SimpleStringProperty categoryTitle;
        private final SimpleStringProperty categoryId;
        private final SimpleStringProperty id;


        public CategoryItem(SimpleStringProperty id, SimpleStringProperty categoryTitle, SimpleStringProperty categoryId) {
            this.id = id;
            this.categoryTitle = categoryTitle;
            this.categoryId = categoryId;
        }

        public String getId() {
            return id.get();
        }

        public SimpleStringProperty idProperty() {
            return id;
        }

        public void setId(String id) {
            this.id.set(id);
        }

        public String getCategoryTitle() {
            return categoryTitle.get();
        }

        public void setCategoryTitle(String categoryTitle) {
            this.categoryTitle.set(categoryTitle);
        }

        public String getCategoryId() {
            return categoryId.get();
        }

        public void setCategoryId(String categoryId) {
            this.categoryId.set(categoryId);
        }

        public SimpleStringProperty categoryTitleProperty() {
            return categoryTitle;
        }

        public SimpleStringProperty categoryIdProperty() {
            return categoryId;
        }
    }
}