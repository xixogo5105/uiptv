package com.uiptv.ui;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.ChannelService;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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

    public CategoryListUI(List<Category> list, Account account, BookmarkChannelListUI bookmarkChannelListUI) { // Removed MediaPlayer argument
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        initWidgets();
        List<CategoryItem> catList = new ArrayList<>();
        list.forEach(i -> catList.add(new CategoryItem(new SimpleStringProperty(i.getDbId()), new SimpleStringProperty(i.getTitle()), new SimpleStringProperty(i.getCategoryId()))));
        table.setItems(FXCollections.observableArrayList(catList));
        this.account = account;
        categoryTitle.setText(account.getAccountName());
        table.addTextFilter();
        // After table.setItems(...) and other setup in the constructor
        if (catList.size() == 1) {
            doRetrieveChannels(catList.get(0));
        }
    }

    private void initWidgets() {
        setSpacing(5);
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
        // Only show popup if it's a STALKER_PORTAL account and the channels are not cached.
        // The isChannelListCached call is short-circuited if the account type is not STALKER_PORTAL.
        boolean showPopup = account.getType() == STALKER_PORTAL && !ChannelService.getInstance().isChannelListCached(item.getId());

        if (showPopup) {
            final Stage logPopup = showLogPopup();
            final TextArea logArea = (TextArea) logPopup.getScene().getRoot().lookup(".log-area");
            primaryStage.getScene().setCursor(Cursor.WAIT);

            new Thread(() -> {
                try {
                    retrieveChannels(item, logMessage -> Platform.runLater(() -> logArea.appendText(logMessage + "\n")));
                } finally {
                    Platform.runLater(() -> {
                        primaryStage.getScene().setCursor(Cursor.DEFAULT);
                        logPopup.close();
                    });
                }
            }).start();
        } else {
            primaryStage.getScene().setCursor(Cursor.WAIT);
            new Thread(() -> {
                try {
                    retrieveChannels(item, null);
                } finally {
                    Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
                }
            }).start();
        }
    }

    private synchronized void retrieveChannels(CategoryItem item, LoggerCallback logger) {
        try {
            List<Channel> channels = ChannelService.getInstance().get(account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle(), account, item.getId(), logger);
            Platform.runLater(() -> {
                this.getChildren().clear();
                getChildren().addAll(new VBox(5, table.getSearchTextField(), table), new ChannelListUI(channels, account, item.getCategoryTitle(), bookmarkChannelListUI, account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle()));
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Stage showLogPopup() {
        final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        VBox dialogVbox = new VBox(20);
        dialogVbox.setPadding(new Insets(10, 10, 10, 10));
        dialogVbox.getChildren().add(new Label("Caching channels. This will take a while..."));
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.getStyleClass().add("log-area");
        dialogVbox.getChildren().add(logArea);
        Scene dialogScene = new Scene(dialogVbox, 400, 300);
        dialog.setScene(dialogScene);
        dialog.show();
        return dialog;
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
