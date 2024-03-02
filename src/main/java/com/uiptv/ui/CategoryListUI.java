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
    private final Account account;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    SearchableTableView table = new SearchableTableView();
    TableColumn<CategoryItem, String> categoryTitle = new TableColumn("Categories");
    TableColumn<CategoryItem, String> categoryId = new TableColumn("");

    public CategoryListUI(List<Category> list, Account account, BookmarkChannelListUI bookmarkChannelListUI) {
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        initWidgets();
        List<CategoryItem> catList = new ArrayList<>();
        list.forEach(i -> catList.add(new CategoryItem(new SimpleStringProperty(i.getDbId()), new SimpleStringProperty(i.getTitle()), new SimpleStringProperty(i.getCategoryId()))));
        table.setItems(FXCollections.observableArrayList(catList));
        this.account = account;
        categoryTitle.setText(account.getAccountName());
        table.addTextFilter();
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
        table.setRowFactory(tv -> {
            TableRow<CategoryItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    primaryStage.getScene().setCursor(Cursor.WAIT);
                    new Thread(() -> {
                        try {
                            Platform.runLater(() -> {
                                retrieveChannels(row);
                            });
                        } catch (Throwable ignored) {
                        } finally {
                            Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
                        }

                    }).start();

                }
            });
            return row;
        });
    }

    private synchronized void retrieveChannels(TableRow<CategoryItem> row) {
        CategoryItem clickedRow = row.getItem();
        try {
            this.getChildren().clear();

            getChildren().addAll(new VBox(5, table.getSearchTextField(), table), new ChannelListUI(ChannelService.getInstance().get(account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? clickedRow.getCategoryId() : clickedRow.getCategoryTitle(), account, clickedRow.getId()), account, clickedRow.getCategoryTitle(), bookmarkChannelListUI, account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? clickedRow.getCategoryId() : clickedRow.getCategoryTitle()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public class CategoryItem {

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