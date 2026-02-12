package com.uiptv.ui;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.ChannelService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.LogPopupUI;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.ui.RootApplication.primaryStage;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class CategoryListUI extends HBox {
    private final Account account;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    SearchableTableView table = new SearchableTableView();
    TableColumn<CategoryItem, String> categoryTitle = new TableColumn("Categories");
    TableColumn<CategoryItem, String> categoryId = new TableColumn("");
    private volatile Thread currentLoadingThread;
    private AtomicBoolean currentRequestCancelled;

    public CategoryListUI(List<Category> list, Account account, BookmarkChannelListUI bookmarkChannelListUI) { // Removed MediaPlayer argument
        this(account, bookmarkChannelListUI);
        setItems(list);
    }

    public CategoryListUI(Account account, BookmarkChannelListUI bookmarkChannelListUI) {
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        initWidgets();
        categoryTitle.setText(account.getAccountName());
        table.addTextFilter();
        table.setPlaceholder(new Label("Loading categories..."));
    }

    public void setItems(List<Category> list) {
        List<CategoryItem> catList = new ArrayList<>();
        list.forEach(i -> catList.add(new CategoryItem(new SimpleStringProperty(i.getDbId()), new SimpleStringProperty(i.getTitle()), new SimpleStringProperty(i.getCategoryId()))));
        table.setItems(FXCollections.observableArrayList(catList));
        table.setPlaceholder(null);
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
        // Check if channels are already loaded for this account
        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(account.getAction()) || account.getType() == AccountType.RSS_FEED;
        boolean channelsAlreadyLoaded = noCachingNeeded || ChannelService.getInstance().getChannelCountForAccount(account.getDbId()) > 0;

        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        
        if (currentLoadingThread != null && currentLoadingThread.isAlive()) {
            primaryStage.getScene().setCursor(Cursor.WAIT);
            currentLoadingThread.interrupt();
            try {
                // Wait a bit for the thread to finish, but don't block forever
                currentLoadingThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        currentRequestCancelled = new AtomicBoolean(false);
        AtomicBoolean isCancelled = currentRequestCancelled;

        if (!channelsAlreadyLoaded) { // If no channels are loaded, show popup and reload
            LogPopupUI logPopup = new LogPopupUI("Caching channels. This will take a while...");
            logPopup.show();
            primaryStage.getScene().setCursor(Cursor.WAIT);

            currentLoadingThread = new Thread(() -> {
                try {
                    retrieveChannels(item, logPopup.getLogger(), noCachingNeeded, isCancelled::get);
                } finally {
                    primaryStage.getScene().setCursor(Cursor.DEFAULT);
                    logPopup.closeGracefully();
                }
            });
            currentLoadingThread.start();
        } else { // Channels are already loaded (even if count is 0), just display them
            primaryStage.getScene().setCursor(Cursor.WAIT);
            currentLoadingThread = new Thread(() -> {
                try {
                    retrieveChannels(item, null, noCachingNeeded, isCancelled::get);
                } finally {
                    Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
                }
            });
            currentLoadingThread.start();
        }
    }

    private void retrieveChannels(CategoryItem item, LoggerCallback logger, boolean noCachingNeeded, Supplier<Boolean> isCancelled) {
        final String categoryId = account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle();
        final ChannelListUI[] channelListUIHolder = new ChannelListUI[1];
        final List<CategoryItem> allItems = new ArrayList<>();
        
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            this.getChildren().clear();
            ChannelListUI ui = new ChannelListUI(account, item.getCategoryTitle(), bookmarkChannelListUI, categoryId);
            channelListUIHolder[0] = ui;
            getChildren().addAll(new VBox(5, table.getSearchTextField(), table), ui);
            if ("All".equalsIgnoreCase(item.getCategoryTitle())) {
                 allItems.addAll(table.getItems());
            }
            latch.countDown();
        });

        try {
            latch.await();
            ChannelListUI channelListUI = channelListUIHolder[0];

            try {
                boolean cachingNeeded = !noCachingNeeded;
                if (cachingNeeded && "All".equalsIgnoreCase(item.getCategoryTitle())) {
                    for (CategoryItem categoryItem : allItems) {
                        if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;
                        if (!"All".equalsIgnoreCase(categoryItem.getCategoryTitle())) {
                            ChannelService.getInstance().get(
                                account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? categoryItem.getCategoryId() : categoryItem.getCategoryTitle(),
                                account, 
                                categoryItem.getId(), 
                                logger,
                                channelListUI::addItems,
                                isCancelled
                            );
                        }
                    }
                } else {
                    if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;
                    ChannelService.getInstance().get(categoryId, account, item.getId(), logger, channelListUI::addItems, isCancelled);
                }
            } finally {
                channelListUI.setLoadingComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Platform.runLater(() -> showErrorAlert("Error loading channels: " + e.getMessage()));
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

        public void setId(String id) {
            this.id.set(id);
        }

        public SimpleStringProperty idProperty() {
            return id;
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
