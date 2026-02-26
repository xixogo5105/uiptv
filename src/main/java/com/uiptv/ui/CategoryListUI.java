package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.ChannelService;
import com.uiptv.service.CategoryService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.model.Account.VOD_AND_SERIES_SUPPORTED;
import static com.uiptv.ui.RootApplication.primaryStage;
import static com.uiptv.util.AccountType.M3U8_LOCAL;
import static com.uiptv.util.AccountType.M3U8_URL;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class CategoryListUI extends HBox {
    private final Account account;
    SearchableTableView table = new SearchableTableView();
    TableColumn<CategoryItem, String> categoryTitle = new TableColumn("Categories");
    TableColumn<CategoryItem, String> categoryId = new TableColumn("");
    private volatile Thread currentLoadingThread;
    private AtomicBoolean currentRequestCancelled;
    private final VBox leftPane = new VBox(5);
    private final TabPane modeTabs = new TabPane();
    private final EnumMap<Account.AccountAction, ModeState> modeStates = new EnumMap<>(Account.AccountAction.class);
    private final Tab itvTab = new Tab("Channels");
    private final Tab vodTab = new Tab("VOD");
    private final Tab seriesTab = new Tab("Series");
    private Account.AccountAction activeMode;

    public CategoryListUI(List<Category> list, Account account) { // Removed MediaPlayer argument
        this(account);
        setItems(list);
    }

    public CategoryListUI(Account account) {
        this.account = account;
        this.activeMode = account.getAction() != null ? account.getAction() : Account.AccountAction.itv;
        initWidgets();
        refreshCategoryColumnTitle();
        table.setPlaceholder(new Label("Loading categories..."));
    }

    public void setItems(List<Category> list) {
        List<Category> processedList = new ArrayList<>(list);

        // Filter out "Uncategorized" for M3U accounts if it has no channels
        if (account.getType() == M3U8_LOCAL || account.getType() == M3U8_URL) {
            processedList = processedList.stream()
                    .filter(category -> {
                        if ("Uncategorized".equalsIgnoreCase(category.getTitle())) {
                            // Keep Uncategorized only when it actually has cached channels.
                            return ChannelService.getInstance().hasCachedLiveChannelsByDbCategoryId(category.getDbId());
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        List<CategoryItem> catList = new ArrayList<>();
        boolean hasAllCategory = processedList.stream().anyMatch(c -> "All".equalsIgnoreCase(c.getTitle()));
        boolean shouldAddAll = !(account.getType() == STALKER_PORTAL || account.getType() == XTREME_API) || processedList.size() >= 2;
        if (!hasAllCategory && shouldAddAll) {
            catList.add(new CategoryItem(new SimpleStringProperty("all"), new SimpleStringProperty("All"), new SimpleStringProperty("all")));
        }
        processedList.forEach(i -> catList.add(new CategoryItem(new SimpleStringProperty(i.getDbId()), new SimpleStringProperty(i.getTitle()), new SimpleStringProperty(i.getCategoryId()))));
        ModeState state = modeStates.computeIfAbsent(activeMode, k -> new ModeState());
        state.categories = new ArrayList<>(processedList);
        table.setItems(FXCollections.observableArrayList(catList));
        table.addTextFilter();
        table.setPlaceholder(null);
        maybeShowCachedChannelPane(state);
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
        setupModeTabs();
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        leftPane.getChildren().addAll(modeTabs, table.getSearchTextField(), table);
        getChildren().addAll(leftPane);
        addChannelClickHandler();
    }

    private void setupModeTabs() {
        boolean supportsVodSeries = VOD_AND_SERIES_SUPPORTED.contains(account.getType());
        modeTabs.setVisible(supportsVodSeries);
        modeTabs.setManaged(supportsVodSeries);
        if (!supportsVodSeries) {
            return;
        }
        modeTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        modeTabs.setPrefHeight(36);
        modeTabs.setMinHeight(36);
        modeTabs.setMaxHeight(36);
        modeTabs.setTabMinHeight(28);
        modeTabs.setTabMaxHeight(28);
        configureModeTab(itvTab, Account.AccountAction.itv);
        configureModeTab(vodTab, Account.AccountAction.vod);
        configureModeTab(seriesTab, Account.AccountAction.series);
        modeTabs.getTabs().setAll(itvTab, vodTab, seriesTab);
        modeTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == null) {
                return;
            }
            Account.AccountAction mode = (Account.AccountAction) newTab.getUserData();
            if (mode != null && mode != activeMode) {
                switchMode(mode);
            }
        });
        selectActiveModeTab();
    }

    private void configureModeTab(Tab tab, Account.AccountAction mode) {
        tab.setClosable(false);
        tab.setUserData(mode);
    }

    private void switchMode(Account.AccountAction mode) {
        if (mode == null || mode == activeMode) {
            return;
        }
        activeMode = mode;
        account.setAction(mode);
        refreshCategoryColumnTitle();
        selectActiveModeTab();

        ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        if (!state.categories.isEmpty()) {
            setItems(state.categories);
            return;
        }

        table.setItems(FXCollections.observableArrayList());
        table.setPlaceholder(new Label("Loading categories..."));
        removeChannelPane();

        new Thread(() -> {
            try {
                account.setAction(mode);
                List<Category> categories = CategoryService.getInstance().get(account, true);
                Platform.runLater(() -> {
                    modeStates.computeIfAbsent(mode, k -> new ModeState()).categories = new ArrayList<>(categories);
                    if (activeMode == mode) {
                        setItems(categories);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showErrorAlert("Failed to load categories: " + e.getMessage()));
            }
        }).start();
    }

    private void selectActiveModeTab() {
        Tab target = itvTab;
        if (activeMode == Account.AccountAction.vod) {
            target = vodTab;
        } else if (activeMode == Account.AccountAction.series) {
            target = seriesTab;
        }
        if (modeTabs.getSelectionModel().getSelectedItem() != target) {
            modeTabs.getSelectionModel().select(target);
        }
    }

    private void refreshCategoryColumnTitle() {
        categoryTitle.setText(account.getAccountName() + " - " + modeLabel(activeMode));
    }

    private String modeLabel(Account.AccountAction mode) {
        if (mode == Account.AccountAction.vod) return "VOD";
        if (mode == Account.AccountAction.series) return "Series";
        return "Channels";
    }

    private void maybeShowCachedChannelPane(ModeState state) {
        if (state == null || state.channelListUI == null) {
            removeChannelPane();
            return;
        }
        showChannelPane(state.channelListUI);
    }

    private void showChannelPane(ChannelListUI ui) {
        if (ui == null) return;
        removeChannelPane();
        getChildren().add(ui);
    }

    private void removeChannelPane() {
        if (getChildren().size() > 1) {
            getChildren().remove(1, getChildren().size());
        }
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
        if (item == null) {
            return;
        }
        final Account.AccountAction mode = activeMode;
        account.setAction(mode);
        final ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        if (state.selectedCategory != null
                && state.channelListUI != null
                && sameCategorySelection(state.selectedCategory, item)) {
            showChannelPane(state.channelListUI);
            return;
        }
        // Check if channels are already loaded for this account
        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(mode) || account.getType() == AccountType.RSS_FEED;
        
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

        primaryStage.getScene().setCursor(Cursor.WAIT);
        currentLoadingThread = new Thread(() -> {
            try {
                retrieveChannels(item, noCachingNeeded, isCancelled::get, mode);
            } finally {
                Platform.runLater(() -> {
                    primaryStage.getScene().setCursor(Cursor.DEFAULT);
                });
            }
        });
        currentLoadingThread.start();
    }

    private void retrieveChannels(CategoryItem item, boolean noCachingNeeded, Supplier<Boolean> isCancelled, Account.AccountAction mode) {
        if (item == null) {
            return;
        }
        account.setAction(mode);
        final ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        final String categoryId = account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle();
        final ChannelListUI[] channelListUIHolder = new ChannelListUI[1];
        final List<CategoryItem> allItems = new ArrayList<>();
        
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            removeChannelPane();
            ChannelListUI ui = new ChannelListUI(account, item.getCategoryTitle(), categoryId);
            channelListUIHolder[0] = ui;
            state.channelListUI = ui;
            state.selectedCategory = item;
            getChildren().add(ui);
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
                    if (ChannelService.getInstance().getChannelCountForAccount(account.getDbId()) == 0) {
                        return;
                    }
                    if (allItems.size() == 1 && "All".equalsIgnoreCase(allItems.get(0).getCategoryTitle())) {
                         ChannelService.getInstance().get(
                                account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? item.getCategoryId() : item.getCategoryTitle(),
                                account, 
                                item.getId(), 
                                null,
                                channelListUI::addItems,
                                isCancelled
                            );
                    } else {
                        for (CategoryItem categoryItem : allItems) {
                            if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;
                            if (!"All".equalsIgnoreCase(categoryItem.getCategoryTitle())) {
                                ChannelService.getInstance().get(
                                    account.getType() == STALKER_PORTAL || account.getType() == XTREME_API ? categoryItem.getCategoryId() : categoryItem.getCategoryTitle(),
                                    account, 
                                    categoryItem.getId(), 
                                    null,
                                    channelListUI::addItems,
                                    isCancelled
                                );
                            }
                        }
                    }
                } else {
                    if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;
                    ChannelService.getInstance().get(categoryId, account, item.getId(), null, channelListUI::addItems, isCancelled);
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

    private static class ModeState {
        private List<Category> categories = new ArrayList<>();
        private CategoryItem selectedCategory;
        private ChannelListUI channelListUI;
    }

    private boolean sameCategorySelection(CategoryItem left, CategoryItem right) {
        return Objects.equals(stableCategorySelectionKey(left), stableCategorySelectionKey(right));
    }

    private String stableCategorySelectionKey(CategoryItem item) {
        if (item == null) {
            return "";
        }
        if (item.getId() != null && !item.getId().trim().isEmpty()) {
            return "id:" + item.getId().trim();
        }
        if (item.getCategoryId() != null && !item.getCategoryId().trim().isEmpty()) {
            return "categoryId:" + item.getCategoryId().trim();
        }
        return "title:" + (item.getCategoryTitle() == null ? "" : item.getCategoryTitle().trim().toLowerCase());
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
