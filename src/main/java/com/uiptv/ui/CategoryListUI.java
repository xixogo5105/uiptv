package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.ChannelService;
import com.uiptv.service.CategoryService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.model.Account.VOD_AND_SERIES_SUPPORTED;
import static com.uiptv.ui.RootApplication.primaryStage;
import static com.uiptv.util.AccountType.M3U8_LOCAL;
import static com.uiptv.util.AccountType.M3U8_URL;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class CategoryListUI extends HBox {
    private static final String ALL_CATEGORY_SENTINEL = "all";
    private final Account account;
    private final boolean embeddedMode;
    SearchableTableView<CategoryItem> table = new SearchableTableView<>();
    TableColumn<CategoryItem, String> categoryTitle = new TableColumn<>(I18n.tr("autoCategories"));
    TableColumn<CategoryItem, String> categoryId = new TableColumn<>("");
    private final AtomicReference<Thread> currentLoadingThread = new AtomicReference<>();
    private AtomicBoolean currentRequestCancelled;
    private final VBox leftPane = new VBox(5);
    private final VBox detailPane = new VBox(8);
    private final Label detailTitle = new Label();
    private final VBox detailContent = new VBox();
    private final TabPane modeTabs = new TabPane();
    private final EnumMap<Account.AccountAction, ModeState> modeStates = new EnumMap<>(Account.AccountAction.class);
    private final Tab itvTab = new Tab(I18n.tr("categoryTabLiveTv"));
    private final Tab vodTab = new Tab(I18n.tr("categoryTabVideoOnDemand"));
    private final Tab seriesTab = new Tab(I18n.tr("categoryTabTvSeries"));
    private Account.AccountAction activeMode;

    public CategoryListUI(List<Category> list, Account account) { // Removed MediaPlayer argument
        this(account, false);
        setItems(list);
    }

    public CategoryListUI(Account account) {
        this(account, false, null);
    }

    public CategoryListUI(Account account, boolean embeddedMode) {
        this(account, embeddedMode, null);
    }

    public CategoryListUI(Account account, boolean embeddedMode, Runnable ignoredOnHome) {
        this.account = account;
        this.embeddedMode = embeddedMode;
        this.activeMode = account.getAction() != null ? account.getAction() : Account.AccountAction.itv;
        initWidgets();
        refreshCategoryColumnTitle();
        table.setPlaceholder(new Label(I18n.tr("autoLoadingCategories")));
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
                    .toList();
        }

        List<CategoryItem> catList = new ArrayList<>();
        boolean hasAllCategory = processedList.stream().anyMatch(this::isAllCategory);
        boolean shouldAddAll = !(account.getType() == STALKER_PORTAL || account.getType() == XTREME_API) || processedList.size() >= 2;
        if (!hasAllCategory && shouldAddAll) {
            catList.add(new CategoryItem(
                    new SimpleStringProperty(ALL_CATEGORY_SENTINEL),
                    new SimpleStringProperty("All"),
                    new SimpleStringProperty(ALL_CATEGORY_SENTINEL)
            ));
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
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
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
        leftPane.setMaxHeight(Double.MAX_VALUE);
        leftPane.setMinHeight(0);
        VBox.setVgrow(leftPane, Priority.ALWAYS);
        initDetailPane();
        getChildren().setAll(leftPane);
        addChannelClickHandler();
        registerSceneCleanupListener();
    }

    private void initDetailPane() {
        detailContent.setSpacing(5);
        VBox.setVgrow(detailContent, Priority.ALWAYS);
        detailPane.getChildren().setAll(detailContent);
    }

    private void showListView() {
        if (!embeddedMode) {
            return;
        }
        getChildren().setAll(leftPane);
    }

    private void showDetailView(ChannelListUI ui, String title) {
        if (!embeddedMode || ui == null) {
            return;
        }
        detailTitle.setText(title);
        detailContent.getChildren().setAll(ui);
        VBox.setVgrow(ui, Priority.ALWAYS);
        detailPane.setMaxHeight(Double.MAX_VALUE);
        detailPane.setMinHeight(0);
        getChildren().setAll(detailPane);
    }

    public boolean navigateBackEmbedded() {
        if (!embeddedMode) {
            return false;
        }
        if (!getChildren().contains(detailPane)) {
            return false;
        }
        if (!detailContent.getChildren().isEmpty()) {
            javafx.scene.Node content = detailContent.getChildren().get(0);
            if (content instanceof ChannelListUI channelListUI && channelListUI.navigateBackEmbedded()) {
                return true;
            }
        }
        showListView();
        return true;
    }

    private void registerSceneCleanupListener() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                releaseTransientState();
            }
        });
    }

    private void releaseTransientState() {
        // Cancel any ongoing loading thread
        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        Thread loadingThread = currentLoadingThread.getAndSet(null);
        if (loadingThread != null && loadingThread.isAlive()) {
            loadingThread.interrupt();
        }

        // Clear all cached mode states to allow garbage collection
        // This is critical because modeStates holds ChannelListUI instances with data
        for (ModeState state : modeStates.values()) {
            if (state.channelListUI != null) {
                state.channelListUI.dispose();
            }
        }
        modeStates.clear();

        // SearchableTableView wraps items in SortedList/FilteredList, which is not directly mutable.
        table.setItems(FXCollections.observableArrayList());
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
        table.setPlaceholder(new Label(I18n.tr("autoLoadingCategories")));
        if (embeddedMode) {
            showListView();
        } else {
            removeChannelPane();
        }

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
                Platform.runLater(() -> showErrorAlert(I18n.tr("autoFailedToLoadCategories", e.getMessage())));
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
        categoryTitle.setText(I18n.tr("autoCategories"));
    }

    private void maybeShowCachedChannelPane(ModeState state) {
        if (state == null || state.channelListUI == null) {
            if (embeddedMode) {
                showListView();
            } else {
                removeChannelPane();
            }
            return;
        }
        if (embeddedMode) {
            String title = state.selectedCategory == null ? "" : state.selectedCategory.getCategoryTitle();
            showDetailView(state.channelListUI, title);
        } else {
            showChannelPane(state.channelListUI);
        }
    }

    private void showChannelPane(ChannelListUI ui) {
        if (ui == null) return;
        removeChannelPane();
        getChildren().add(ui);
        HBox.setHgrow(ui, Priority.ALWAYS);
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
            if (embeddedMode) {
                showDetailView(state.channelListUI, item.getCategoryTitle());
            } else {
                state.channelListUI.showChannelListView();
                showChannelPane(state.channelListUI);
            }
            return;
        }
        // Check if channels are already loaded for this account
        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(mode) || account.getType() == AccountType.RSS_FEED;
        
        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        
        Thread runningThread = currentLoadingThread.get();
        if (runningThread != null && runningThread.isAlive()) {
            primaryStage.getScene().setCursor(Cursor.WAIT);
            runningThread.interrupt();
            try {
                // Wait a bit for the thread to finish, but don't block forever
                runningThread.join(2000);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }

        currentRequestCancelled = new AtomicBoolean(false);
        AtomicBoolean isCancelled = currentRequestCancelled;

        primaryStage.getScene().setCursor(Cursor.WAIT);
        Thread loadingThread = new Thread(() -> {
            try {
                retrieveChannels(item, noCachingNeeded, isCancelled::get, mode);
            } finally {
                Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
                currentLoadingThread.compareAndSet(Thread.currentThread(), null);
            }
        });
        currentLoadingThread.set(loadingThread);
        loadingThread.start();
    }

    private void retrieveChannels(CategoryItem item, boolean noCachingNeeded, BooleanSupplier isCancelled, Account.AccountAction mode) {
        if (item == null) {
            return;
        }
        account.setAction(mode);
        final ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        final String selectedCategoryKey = selectedCategoryKey(item);
        final ChannelListUI[] channelListUIHolder = new ChannelListUI[1];
        final List<CategoryItem> allItems = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> initializeChannelListView(item, selectedCategoryKey, state, channelListUIHolder, allItems, latch));

        try {
            latch.await();
            ChannelListUI channelListUI = channelListUIHolder[0];

            try {
                loadChannelsIntoUi(item, noCachingNeeded, isCancelled, selectedCategoryKey, channelListUI, allItems);
            } finally {
                channelListUI.setLoadingComplete();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Platform.runLater(() -> showErrorAlert(I18n.tr("autoErrorLoadingChannels", e.getMessage())));
        }
    }

    private String selectedCategoryKey(CategoryItem item) {
        return account.getType() == STALKER_PORTAL || account.getType() == XTREME_API
                ? item.getCategoryId()
                : item.getCategoryTitle();
    }

    private void initializeChannelListView(CategoryItem item, String selectedCategoryKey, ModeState state,
                                           ChannelListUI[] channelListUIHolder, List<CategoryItem> allItems, CountDownLatch latch) {
        ChannelListUI ui = new ChannelListUI(account, item.getCategoryTitle(), selectedCategoryKey);
        if (embeddedMode) {
            ui.setEmbeddedMode(true);
        } else {
            ui.setInlineEpisodeNavigationEnabled(true);
        }
        channelListUIHolder[0] = ui;
        state.channelListUI = ui;
        state.selectedCategory = item;
        if (embeddedMode) {
            showDetailView(ui, item.getCategoryTitle());
        } else {
            removeChannelPane();
            getChildren().add(ui);
        }
        if (isAllCategory(item)) {
            allItems.addAll(table.getItems());
        }
        latch.countDown();
    }

    private void loadChannelsIntoUi(CategoryItem item, boolean noCachingNeeded, BooleanSupplier isCancelled,
                                    String selectedCategoryKey, ChannelListUI channelListUI, List<CategoryItem> allItems) throws IOException {
        boolean cachingNeeded = !noCachingNeeded;
        if (cachingNeeded && isAllCategory(item)) {
            loadAllCategoryChannels(item, isCancelled, channelListUI, allItems);
            return;
        }
        if (isLoadingCancelled(isCancelled)) {
            return;
        }
        ChannelService.getInstance().get(selectedCategoryKey, account, item.getId(), null, channelListUI::addItems, isCancelled::getAsBoolean);
    }

    private void loadAllCategoryChannels(CategoryItem item, BooleanSupplier isCancelled,
                                         ChannelListUI channelListUI, List<CategoryItem> allItems) throws IOException {
        if (ChannelService.getInstance().getChannelCountForAccount(account.getDbId()) == 0) {
            return;
        }
        if (allItems.size() == 1 && isAllCategory(allItems.get(0))) {
            ChannelService.getInstance().get(selectedCategoryKey(item), account, item.getId(), null,
                    channelListUI::addItems, isCancelled::getAsBoolean);
            return;
        }
        for (CategoryItem categoryItem : allItems) {
            if (isLoadingCancelled(isCancelled)) {
                return;
            }
            if (!isAllCategory(categoryItem)) {
                ChannelService.getInstance().get(selectedCategoryKey(categoryItem), account, categoryItem.getId(), null,
                        channelListUI::addItems, isCancelled::getAsBoolean);
            }
        }
    }

    private boolean isLoadingCancelled(BooleanSupplier isCancelled) {
        return Thread.currentThread().isInterrupted() || isCancelled.getAsBoolean();
    }

    private static class ModeState {
        private List<Category> categories = new ArrayList<>();
        private CategoryItem selectedCategory;
        private ChannelListUI channelListUI;
    }

    private boolean isAllCategory(Category category) {
        if (category == null) {
            return false;
        }
        String candidateCategoryId = category.getCategoryId();
        String title = category.getTitle();
        return (candidateCategoryId != null && ALL_CATEGORY_SENTINEL.equalsIgnoreCase(candidateCategoryId.trim()))
                || (title != null && title.equalsIgnoreCase(I18n.tr("commonAll")))
                || (title != null && title.equalsIgnoreCase("All"));
    }

    private boolean isAllCategory(CategoryItem item) {
        if (item == null) {
            return false;
        }
        String id = item.getId();
        String candidateCategoryId = item.getCategoryId();
        String title = item.getCategoryTitle();
        return (id != null && ALL_CATEGORY_SENTINEL.equalsIgnoreCase(id.trim()))
                || (candidateCategoryId != null && ALL_CATEGORY_SENTINEL.equalsIgnoreCase(candidateCategoryId.trim()))
                || (title != null && title.equalsIgnoreCase(I18n.tr("commonAll")))
                || (title != null && title.equalsIgnoreCase("All"));
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
