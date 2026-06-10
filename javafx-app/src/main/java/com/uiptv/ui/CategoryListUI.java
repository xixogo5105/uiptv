package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.model.AccountView;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.service.CategoryCacheRemovalService;
import com.uiptv.service.CategoryResolver;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.util.I18n;
import com.uiptv.widget.CloseIconButton;
import com.uiptv.widget.InlinePanelService;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.ResponsiveCardGrid;
import com.uiptv.widget.SearchableTableView;
import com.uiptv.widget.ThemedDialogSupport;
import com.uiptv.widget.UIptvAlert;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.model.Account.VOD_AND_SERIES_SUPPORTED;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class CategoryListUI extends HBox implements SearchTarget {
    private static final String ALL_CATEGORY_SENTINEL = "all";
    private static final String PARENTAL_LOCK_LOG_PREFIX = "[ParentalLock] ";
    private static final String LOG_ACCOUNT_START = "account=";
    private static final String LOG_ACCOUNT = " account=";
    private static final String LOG_TYPE = " type=";
    private static final String LOG_ACTION = " action=";
    private static final String LOG_CATEGORY_ID = " categoryId=";
    private static final String LOG_TITLE = " title=";
    private static final String LOG_CHANNELS = " channels: ";
    private final AccountMediaContext mediaContext;
    private final AccountView account;
    private final AtomicReference<Thread> currentLoadingThread = new AtomicReference<>();
    private final VBox leftPane = new VBox(5);
    private final VBox detailPane = new VBox(8);
    private final HBox headerRow = new HBox(8);
    private final HBox detailHeader = new HBox(8);
    private final Label detailTitle = new Label();
    private final VBox detailContent = new VBox();
    private final Button closeButton = new CloseIconButton(I18n.tr("autoClose"));
    private final Button detailCloseButton = new CloseIconButton(I18n.tr("autoClose"));
    private final Button detailBackButton = new Button(I18n.tr("autoBack"));
    private final Button accountsBackButton = new Button(I18n.tr("autoBack"));
    private final PillBar<String> modePillBar = new PillBar<>(this::modePillLabel, mode -> mode);
    private final Label categoryHeading = new Label();
    private final ResponsiveCardGrid<CategoryItem> categoryCardGrid = new ResponsiveCardGrid<>(this::createCategoryCard);
    private final ScrollPane categoryScrollPane = new ScrollPane(categoryCardGrid);
    private final ObservableList<CategoryItem> categoryItems = FXCollections.observableArrayList();
    private final List<CategoryItem> selectedCategoryItems = new ArrayList<>();
    private final List<Node> detailHeaderActions = new ArrayList<>();
    private final EnumMap<Account.AccountAction, ModeState> modeStates = new EnumMap<>(Account.AccountAction.class);
    private static final String MODE_ACCOUNTS = "accounts";
    private static final String MODE_ITV = "itv";
    private static final String MODE_VOD = "vod";
    private static final String MODE_SERIES = "series";
    SearchableTableView<CategoryItem> table = new SearchableTableView<>();
    TableColumn<CategoryItem, String> categoryTitle = new TableColumn<>(I18n.tr("autoCategories"));
    TableColumn<CategoryItem, String> categoryId = new TableColumn<>("");
    private AtomicBoolean currentRequestCancelled;
    private Account.AccountAction activeMode;
    private Runnable accountsNavigationHandler;
    private Runnable closeHandler;
    private Consumer<String> headerSearchTextHandler;
    private CategoryItem focusedCategoryItem;
    private boolean mediaDrawerMode;
    private boolean categoryDataLoaded;
    private boolean retainTransientStateOnDetach;
    private boolean transientStateReleasePending;
    private String searchText = "";

    public CategoryListUI(Account account) {
        this(AccountMediaContext.from(account));
    }

    public CategoryListUI(AccountMediaContext mediaContext) {
        this.mediaContext = mediaContext == null
                ? new AccountMediaContext(null, Account.AccountAction.itv)
                : mediaContext;
        this.account = this.mediaContext.account();
        this.activeMode = this.mediaContext.action();
        initWidgets();
        refreshCategoryColumnTitle();
        table.setPlaceholder(new Label(I18n.tr("autoLoadingCategories")));
    }

    private AccountMediaContext contextForMode(Account.AccountAction mode) {
        return mediaContext.withAction(mode == null ? Account.AccountAction.itv : mode);
    }

    private Account accountForMode(Account.AccountAction mode) {
        return contextForMode(mode).toAccount();
    }

    private String accountName() {
        return account == null || account.accountName() == null ? "" : account.accountName();
    }

    private com.uiptv.util.AccountType accountType() {
        return account == null ? null : account.type();
    }

    private String accountDbId() {
        return account == null || account.dbId() == null ? "" : account.dbId();
    }

    public void setItems(List<Category> list) {
        categoryDataLoaded = true;
        List<Category> processedList = new CategoryResolver().resolveCategories(accountForMode(activeMode), list);
        long censoredCount = processedList.stream().filter(category -> category != null && category.getCensored() == 1).count();
        com.uiptv.util.AppLog.addInfoLog(CategoryListUI.class,
                PARENTAL_LOCK_LOG_PREFIX + LOG_ACCOUNT_START + accountName()
                        + LOG_TYPE + accountType()
                        + LOG_ACTION + activeMode
                        + " categoriesLoaded=" + processedList.size()
                        + " censoredCategories=" + censoredCount);

        List<CategoryItem> catList = new ArrayList<>();
        processedList.forEach(i -> catList.add(new CategoryItem(
                new SimpleStringProperty(i.getDbId()),
                new SimpleStringProperty(i.getTitle()),
                new SimpleStringProperty(i.getCategoryId()),
                i.getCensored() == 1
        )));
        ModeState state = modeStates.computeIfAbsent(activeMode, k -> new ModeState());
        state.categories = new ArrayList<>(processedList);
        categoryItems.setAll(catList);
        selectedCategoryItems.removeIf(item -> !categoryItems.contains(item));
        focusedCategoryItem = categoryItems.contains(focusedCategoryItem) ? focusedCategoryItem : null;
        rebuildCategoryCards();
        table.setItems(FXCollections.observableArrayList(catList));
        table.setPlaceholder(null);
        maybeShowCachedChannelPane(state);
        if (catList.size() == 1) {
            doRetrieveChannels(catList.getFirst());
        }
    }

    private void initWidgets() {
        setSpacing(5);
        getStyleClass().add("account-category-panel");
        setFillHeight(true);
        setMaxWidth(Double.MAX_VALUE);
        setMinWidth(0);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getColumns().add(categoryTitle);
        categoryTitle.setVisible(true);
        categoryId.setVisible(false);
        categoryTitle.setCellValueFactory(cellData -> cellData.getValue().categoryTitleProperty());
        categoryId.setCellValueFactory(cellData -> cellData.getValue().categoryIdProperty());
        categoryTitle.setSortType(TableColumn.SortType.ASCENDING);
        categoryTitle.setSortable(true);
        setupModePillBar();
        setupPanelHeaders();
        setupCategoryCardList();
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        leftPane.getStyleClass().add("account-category-list-pane");
        leftPane.setMaxHeight(Double.MAX_VALUE);
        leftPane.setMinHeight(0);
        VBox.setVgrow(leftPane, Priority.ALWAYS);
        HBox.setHgrow(leftPane, Priority.ALWAYS);
        updateLeftPaneLayout();
        initDetailPane();
        getChildren().setAll(leftPane);
        registerSceneCleanupListener();
    }

    private void initDetailPane() {
        detailBackButton.setOnAction(_ -> navigateBackFromDetail());
        detailCloseButton.setOnAction(_ -> closePanel());
        detailHeader.getStyleClass().add("account-category-detail-header");
        detailHeader.setAlignment(Pos.CENTER_LEFT);
        detailHeader.setMaxWidth(Double.MAX_VALUE);
        detailTitle.getStyleClass().add("account-category-detail-title");
        detailTitle.setMinWidth(0);
        detailTitle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(detailTitle, Priority.ALWAYS);
        updateDetailHeaderChildren();
        detailContent.setSpacing(5);
        detailContent.setFillWidth(true);
        detailContent.setMinWidth(0);
        detailContent.setMaxWidth(Double.MAX_VALUE);
        detailContent.setMinHeight(0);
        detailContent.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(detailContent, Priority.ALWAYS);
        detailPane.setFillWidth(true);
        detailPane.setMinWidth(0);
        detailPane.setMaxWidth(Double.MAX_VALUE);
        detailPane.setMinHeight(0);
        detailPane.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(detailPane, Priority.ALWAYS);
        detailPane.getChildren().setAll(detailContent);
    }

    private void setupCategoryCardList() {
        categoryHeading.getStyleClass().add("account-category-heading");
        categoryHeading.setMinWidth(0);
        categoryHeading.setMaxWidth(Double.MAX_VALUE);
        categoryCardGrid.getStyleClass().add("account-category-card-list");
        categoryCardGrid.setSingleColumn(true);
        categoryCardGrid.setCardMinHeight(44);
        categoryCardGrid.setCardWidthRange(180, 960);
        categoryCardGrid.setGaps(0, 6);
        categoryCardGrid.setOnItemActivated(this::doRetrieveChannels);
        categoryCardGrid.setContextMenuFactory((item, selectedItems, owner) -> {
            selectedCategoryItems.clear();
            selectedCategoryItems.addAll(selectedItems);
            focusedCategoryItem = item;
            ContextMenu contextMenu = createCategoryContextMenu(item);
            MenuItem removeSelected = contextMenu.getItems().isEmpty() ? null : contextMenu.getItems().getFirst();
            if (removeSelected != null) {
                removeSelected.setDisable(selectedRemovableCategoryItems().isEmpty());
            }
            return contextMenu;
        });
        categoryCardGrid.getSelectedItems().addListener((ListChangeListener<CategoryItem>) _ -> syncCategorySelectionFromGrid());
        categoryCardGrid.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                doRetrieveChannels(resolveFocusedCategoryItem());
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                removeSelectedCachedCategories();
                event.consume();
            }
        });
        categoryScrollPane.getStyleClass().addAll("account-category-scroll", "transparent-scroll-pane");
        categoryScrollPane.setFitToWidth(true);
        categoryScrollPane.setPannable(true);
        categoryScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        categoryScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        categoryScrollPane.setFocusTraversable(true);
        categoryScrollPane.setMinSize(0, 0);
        categoryScrollPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(categoryScrollPane, Priority.ALWAYS);
        categoryScrollPane.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                doRetrieveChannels(resolveFocusedCategoryItem());
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                removeSelectedCachedCategories();
                event.consume();
            } else if (event.getCode() == KeyCode.A && event.isShortcutDown()) {
                categoryCardGrid.selectItems(filteredCategoryItems());
                syncCategorySelectionFromGrid();
                event.consume();
            }
        });
        showCategoryPlaceholder(I18n.tr("autoLoadingCategories"));
    }

    private void rebuildCategoryCards() {
        if (categoryItems.isEmpty()) {
            showCategoryPlaceholder(categoryDataLoaded
                    ? I18n.tr("autoNothingFoundFor", I18n.tr("autoCategories"))
                    : I18n.tr("autoLoadingCategories"));
            return;
        }
        List<CategoryItem> visibleItems = filteredCategoryItems();
        if (visibleItems.isEmpty()) {
            String target = searchText == null || searchText.isBlank()
                    ? I18n.tr("autoCategories")
                    : searchText.trim();
            showCategoryPlaceholder(I18n.tr("autoNothingFoundFor", target));
            return;
        }
        List<CategoryItem> selectedBeforeRefresh = selectedCategoryItems.stream()
                .filter(visibleItems::contains)
                .toList();
        CategoryItem focusedBeforeRefresh = visibleItems.contains(focusedCategoryItem) ? focusedCategoryItem : null;
        categoryCardGrid.setPlaceholderNode(null);
        categoryCardGrid.setItems(FXCollections.observableArrayList(visibleItems));
        selectedCategoryItems.clear();
        selectedCategoryItems.addAll(selectedBeforeRefresh);
        focusedCategoryItem = focusedBeforeRefresh;
        updateCategorySelectionStyles();
    }

    private List<CategoryItem> filteredCategoryItems() {
        String normalizedSearch = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        if (normalizedSearch.isBlank()) {
            return new ArrayList<>(categoryItems);
        }
        return categoryItems.stream()
                .filter(item -> matchesCategorySearch(item, normalizedSearch))
                .toList();
    }

    private boolean matchesCategorySearch(CategoryItem item, String normalizedSearch) {
        if (item == null) {
            return false;
        }
        String title = item.getCategoryTitle() == null ? "" : item.getCategoryTitle();
        String id = item.getCategoryId() == null ? "" : item.getCategoryId();
        return title.toLowerCase(Locale.ROOT).contains(normalizedSearch)
                || id.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private void showCategoryPlaceholder(String text) {
        selectedCategoryItems.clear();
        focusedCategoryItem = null;
        Label placeholder = new Label(text == null ? "" : text);
        placeholder.getStyleClass().add("account-category-placeholder");
        placeholder.setWrapText(true);
        placeholder.setMaxWidth(Double.MAX_VALUE);
        categoryCardGrid.setPlaceholderNode(placeholder);
        categoryCardGrid.setItems(FXCollections.observableArrayList());
        categoryCardGrid.clearSelection();
    }

    private HBox createCategoryCard(CategoryItem item) {
        Label title = new Label(item == null ? "" : item.getCategoryTitle());
        title.getStyleClass().add("account-category-card-title");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setWrapText(true);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox card = new HBox(8, title);
        card.getStyleClass().addAll("account-category-card", "watching-now-episode-card", "watching-now-episode-card-compact");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setFocusTraversable(true);
        card.setUserData(item);

        if (item != null && item.isCensored()) {
            Label lockBadge = new Label(I18n.tr("filterLockStateLocked"));
            lockBadge.getStyleClass().add("account-category-lock-badge");
            card.getChildren().add(lockBadge);
        }
        return card;
    }

    private ContextMenu createCategoryContextMenu(CategoryItem item) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeSelected = new MenuItem(I18n.tr("categoryRemoveSelectedFromCache"));
        removeSelected.setOnAction(_ -> removeSelectedCachedCategories());
        contextMenu.getItems().add(removeSelected);
        return contextMenu;
    }

    private void selectCategoryItem(CategoryItem item, boolean toggle) {
        if (item == null) {
            return;
        }
        if (toggle) {
            if (selectedCategoryItems.contains(item)) {
                selectedCategoryItems.remove(item);
            } else {
                selectedCategoryItems.add(item);
            }
        } else {
            selectedCategoryItems.clear();
            selectedCategoryItems.add(item);
        }
        focusedCategoryItem = item;
        updateCategorySelectionStyles();
    }

    private void syncCategorySelectionFromGrid() {
        selectedCategoryItems.clear();
        selectedCategoryItems.addAll(categoryCardGrid.getSelectedItems());
        focusedCategoryItem = categoryCardGrid.getFocusedItem();
    }

    private CategoryItem resolveFocusedCategoryItem() {
        if (focusedCategoryItem != null && categoryItems.contains(focusedCategoryItem)) {
            return focusedCategoryItem;
        }
        if (!selectedCategoryItems.isEmpty()) {
            return selectedCategoryItems.getLast();
        }
        List<CategoryItem> visibleItems = filteredCategoryItems();
        return visibleItems.isEmpty() ? null : visibleItems.getFirst();
    }

    private void updateCategorySelectionStyles() {
        List<CategoryItem> selectedVisibleItems = selectedCategoryItems.stream()
                .filter(categoryCardGrid.getItems()::contains)
                .toList();
        if (selectedVisibleItems.isEmpty()) {
            categoryCardGrid.clearSelection();
        } else {
            categoryCardGrid.selectItems(selectedVisibleItems);
        }
    }

    private void showListView() {
        showListView(false);
    }

    private void showListView(boolean abandonActiveLoad) {
        if (getChildren().contains(detailPane)) {
            replaceSearchText("", true);
        }
        if (abandonActiveLoad) {
            abandonActiveChannelView();
        }
        restoreCategoryItemsFromStateIfNeeded();
        rebuildCategoryCards();
        HBox.setHgrow(leftPane, Priority.ALWAYS);
        getChildren().setAll(leftPane);
    }

    private void restoreCategoryItemsFromStateIfNeeded() {
        if (!categoryItems.isEmpty()) {
            return;
        }
        ModeState state = modeStates.get(activeMode);
        if (state == null || state.categories.isEmpty()) {
            return;
        }
        categoryDataLoaded = true;
        List<CategoryItem> restoredItems = new ArrayList<>();
        for (Category category : state.categories) {
            if (category == null) {
                continue;
            }
            restoredItems.add(new CategoryItem(
                    new SimpleStringProperty(category.getDbId()),
                    new SimpleStringProperty(category.getTitle()),
                    new SimpleStringProperty(category.getCategoryId()),
                    category.getCensored() == 1
            ));
        }
        categoryItems.setAll(restoredItems);
        table.setItems(FXCollections.observableArrayList(restoredItems));
        table.setPlaceholder(null);
    }

    private void showDetailView(ChannelListUI ui, String title) {
        if (ui == null) {
            return;
        }
        detailHeaderActions.clear();
        ui.setDetailHeaderActionsHandler(this::setDetailHeaderActions);
        replaceSearchText("", true);
        rebuildCategoryCards();
        detailTitle.setText(title);
        updateDetailHeaderChildren();
        detailContent.getChildren().setAll(ui);
        ui.setSearchQuery(searchText);
        ui.setMinWidth(0);
        ui.setMaxWidth(Double.MAX_VALUE);
        ui.setMinHeight(0);
        ui.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(ui, Priority.ALWAYS);
        detailPane.setMaxHeight(Double.MAX_VALUE);
        detailPane.setMinHeight(0);
        if (!detailPane.getStyleClass().contains("account-category-detail-pane")) {
            detailPane.getStyleClass().add("account-category-detail-pane");
        }
        detailPane.getChildren().setAll(detailHeader, detailContent);
        HBox.setHgrow(detailPane, Priority.ALWAYS);
        getChildren().setAll(detailPane);
    }

    public boolean navigateBackEmbedded() {
        if (!getChildren().contains(detailPane)) {
            return false;
        }
        return navigateBackWithinDetail();
    }

    private void navigateBackFromDetail() {
        navigateBackWithinDetail();
    }

    private boolean navigateBackWithinDetail() {
        if (!detailContent.getChildren().isEmpty()) {
            Node content = detailContent.getChildren().getFirst();
            if (content instanceof ChannelListUI channelListUI && channelListUI.navigateBackEmbedded()) {
                return true;
            }
        }
        showListView(true);
        return true;
    }

    private void setDetailHeaderActions(List<Node> actions) {
        detailHeaderActions.clear();
        if (actions != null) {
            detailHeaderActions.addAll(actions.stream()
                    .filter(Objects::nonNull)
                    .toList());
        }
        updateDetailHeaderChildren();
    }

    private void updateDetailHeaderChildren() {
        List<Node> children = new ArrayList<>();
        children.add(detailBackButton);
        children.addAll(detailHeaderActions);
        children.add(detailTitle);
        children.add(detailCloseButton);
        detailHeader.getChildren().setAll(children);
    }

    @Override
    public void setSearchQuery(String searchText) {
        String value = searchText == null ? "" : searchText;
        if (Objects.equals(this.searchText, value)) {
            return;
        }
        replaceSearchText(value, false);
        ModeState state = modeStates.get(activeMode);
        if (getChildren().contains(detailPane) && state != null && state.channelListUI != null) {
            state.channelListUI.setSearchQuery(value);
            return;
        }
        rebuildCategoryCards();
    }

    private void replaceSearchText(String value, boolean updateHeader) {
        this.searchText = value == null ? "" : value;
        if (updateHeader && headerSearchTextHandler != null) {
            headerSearchTextHandler.accept(this.searchText);
        }
    }

    private void registerSceneCleanupListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                Platform.runLater(() -> {
                    if (getScene() == null) {
                        releaseTransientStateIfAllowed();
                    }
                });
            } else {
                transientStateReleasePending = false;
            }
        });
    }

    public void setRetainTransientStateOnDetach(boolean retainTransientStateOnDetach) {
        this.retainTransientStateOnDetach = retainTransientStateOnDetach;
        if (retainTransientStateOnDetach) {
            return;
        }
        if (getScene() == null && transientStateReleasePending) {
            transientStateReleasePending = false;
            releaseTransientState();
        } else if (getScene() != null) {
            transientStateReleasePending = false;
        }
    }

    private void releaseTransientStateIfAllowed() {
        if (retainTransientStateOnDetach || InlinePanelService.hasOpenPanel()) {
            transientStateReleasePending = true;
            return;
        }
        transientStateReleasePending = false;
        releaseTransientState();
    }

    private void releaseTransientState() {
        cancelCurrentLoadingRequest();

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
        categoryItems.clear();
        selectedCategoryItems.clear();
        focusedCategoryItem = null;
        categoryDataLoaded = false;
        categoryCardGrid.setItems(FXCollections.observableArrayList());
        categoryCardGrid.clearSelection();
    }

    public void dispose() {
        retainTransientStateOnDetach = false;
        transientStateReleasePending = false;
        releaseTransientState();
    }

    public void setMediaDrawerMode(boolean enabled) {
        if (mediaDrawerMode == enabled) {
            scrollFocusedContentIntoView();
            return;
        }
        mediaDrawerMode = enabled;
        updateStyleClass(this, "account-category-panel-drawer", enabled);
        updateStyleClass(leftPane, "account-category-list-pane-drawer", enabled);
        updateStyleClass(detailPane, "account-category-detail-pane-drawer", enabled);
        setupPanelHeaders();
        updateLeftPaneLayout();
        refreshCategoryColumnTitle();
        for (ModeState state : modeStates.values()) {
            if (state.channelListUI != null) {
                state.channelListUI.setMediaDrawerMode(enabled);
            }
        }
        scrollFocusedContentIntoView();
    }

    public void scrollFocusedContentIntoView() {
        categoryCardGrid.scrollFocusedItemIntoView();
        ModeState state = modeStates.get(activeMode);
        if (state != null && state.channelListUI != null) {
            state.channelListUI.scrollFocusedChannelIntoView();
        }
    }

    public boolean handleActiveChannelNavigationKey(KeyEvent event) {
        if (!mediaDrawerMode || event == null || !getChildren().contains(detailPane)) {
            return false;
        }
        ModeState state = modeStates.get(activeMode);
        if (state == null || state.channelListUI == null) {
            return false;
        }
        return state.channelListUI.handleChannelNavigationKey(event);
    }

    public void setAccountsNavigationHandler(Runnable accountsNavigationHandler) {
        this.accountsNavigationHandler = accountsNavigationHandler;
        updateCloseButtonVisibility();
    }

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler;
        updateCloseButtonVisibility();
    }

    public void setHeaderSearchTextHandler(Consumer<String> headerSearchTextHandler) {
        this.headerSearchTextHandler = headerSearchTextHandler;
    }

    private void setupPanelHeaders() {
        updateStyleClass(closeButton, "account-category-close-button", true);
        updateStyleClass(detailCloseButton, "account-category-close-button", true);
        updateStyleClass(accountsBackButton, "account-category-back-button", true);
        closeButton.setMinWidth(Region.USE_PREF_SIZE);
        closeButton.setMaxWidth(Region.USE_PREF_SIZE);
        detailCloseButton.setMinWidth(Region.USE_PREF_SIZE);
        detailCloseButton.setMaxWidth(Region.USE_PREF_SIZE);
        accountsBackButton.setMinWidth(Region.USE_PREF_SIZE);
        accountsBackButton.setMaxWidth(Region.USE_PREF_SIZE);
        closeButton.setOnAction(_ -> closePanel());
        accountsBackButton.setOnAction(_ -> navigateToAccounts());
        updateStyleClass(headerRow, "account-category-header", true);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setMaxWidth(Double.MAX_VALUE);
        modePillBar.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        if (mediaDrawerMode) {
            modePillBar.setPrefWidth(Region.USE_COMPUTED_SIZE);
            modePillBar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(modePillBar, Priority.ALWAYS);
            headerRow.getChildren().setAll(accountsBackButton, spacer, closeButton);
        } else {
            modePillBar.setPrefWidth(400);
            modePillBar.setMaxWidth(Region.USE_PREF_SIZE);
            HBox.setHgrow(modePillBar, Priority.NEVER);
            headerRow.getChildren().setAll(modePillBar, spacer, closeButton);
        }
        updateCloseButtonVisibility();
    }

    private void updateLeftPaneLayout() {
        if (mediaDrawerMode) {
            leftPane.getChildren().setAll(headerRow, modePillBar, categoryHeading, categoryScrollPane);
            return;
        }
        leftPane.getChildren().setAll(headerRow, categoryHeading, categoryScrollPane);
    }

    private void updateCloseButtonVisibility() {
        boolean visible = closeHandler != null || accountsNavigationHandler != null;
        closeButton.setVisible(visible);
        closeButton.setManaged(visible);
        detailCloseButton.setVisible(visible);
        detailCloseButton.setManaged(visible);
        accountsBackButton.setVisible(mediaDrawerMode && accountsNavigationHandler != null);
        accountsBackButton.setManaged(mediaDrawerMode && accountsNavigationHandler != null);
    }

    private void closePanel() {
        if (closeHandler != null) {
            closeHandler.run();
            releaseTransientState();
            return;
        }
        if (accountsNavigationHandler != null) {
            accountsNavigationHandler.run();
            releaseTransientState();
            return;
        }
        releaseTransientState();
    }

    private void navigateToAccounts() {
        if (accountsNavigationHandler != null) {
            accountsNavigationHandler.run();
            releaseTransientState();
            return;
        }
        releaseTransientState();
    }

    private void setupModePillBar() {
        boolean supportsVodSeries = VOD_AND_SERIES_SUPPORTED.contains(accountType());
        List<String> modes = new ArrayList<>();
        modes.add(MODE_ACCOUNTS);
        modes.add(MODE_ITV);
        if (supportsVodSeries) {
            modes.add(MODE_VOD);
            modes.add(MODE_SERIES);
        }
        modePillBar.setItems(modes);
        modePillBar.selectedItemProperty().addListener((_, _, selectedMode) -> {
            if (selectedMode == null) {
                return;
            }
            if (MODE_ACCOUNTS.equals(selectedMode)) {
                navigateToAccounts();
                Platform.runLater(this::selectActiveModePill);
                return;
            }
            Account.AccountAction mode = actionForMode(selectedMode);
            if (mode != null && mode != activeMode) {
                switchMode(mode);
            }
        });
        selectActiveModePill();
    }

    private String modePillLabel(String mode) {
        return switch (mode) {
            case MODE_ACCOUNTS -> I18n.tr("autoAccount");
            case MODE_VOD -> I18n.tr("autoVod");
            case MODE_SERIES -> I18n.tr("autoSeries");
            default -> I18n.tr("autoTvChannels");
        };
    }

    private Account.AccountAction actionForMode(String mode) {
        return switch (mode) {
            case MODE_VOD -> Account.AccountAction.vod;
            case MODE_SERIES -> Account.AccountAction.series;
            case MODE_ITV -> Account.AccountAction.itv;
            default -> null;
        };
    }

    private void switchMode(Account.AccountAction mode) {
        if (mode == null || mode == activeMode) {
            return;
        }
        cancelCurrentLoadingRequest();
        disposeChannelListState(modeStates.get(activeMode));
        activeMode = mode;
        refreshCategoryColumnTitle();
        selectActiveModePill();

        ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        if (!state.categories.isEmpty()) {
            setItems(state.categories);
            return;
        }

        table.setItems(FXCollections.observableArrayList());
        categoryItems.clear();
        selectedCategoryItems.clear();
        focusedCategoryItem = null;
        categoryDataLoaded = false;
        showCategoryPlaceholder(I18n.tr("autoLoadingCategories"));
        table.setPlaceholder(new Label(I18n.tr("autoLoadingCategories")));
        showListView();

        new Thread(() -> {
            try {
                Account modeAccount = accountForMode(mode);
                List<Category> categories = CategoryService.getInstance().get(modeAccount, true,
                        message -> logCategoryFetch(mode, message));
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

    private void selectActiveModePill() {
        String target = switch (activeMode) {
            case vod -> MODE_VOD;
            case series -> MODE_SERIES;
            case itv -> MODE_ITV;
        };
        if (!Objects.equals(modePillBar.getSelectedItem(), target)) {
            modePillBar.setSelectedItem(target);
        }
    }

    private void refreshCategoryColumnTitle() {
        String accountName = accountName().trim();
        String baseTitle = I18n.tr("autoCategories");
        String title = accountName.isEmpty() ? baseTitle : baseTitle + " - " + accountName;
        if (mediaDrawerMode) {
            String modeTitle = modePillLabel(switch (activeMode) {
                case vod -> MODE_VOD;
                case series -> MODE_SERIES;
                case itv -> MODE_ITV;
            });
            title = accountName.isEmpty()
                    ? I18n.tr("autoAccount") + " / " + modeTitle
                    : I18n.tr("autoAccount") + " / " + accountName + " / " + modeTitle;
        }
        categoryTitle.setText(title);
        categoryHeading.setText(title);
    }

    private void maybeShowCachedChannelPane(ModeState state) {
        if (state == null || state.channelListUI == null) {
            showListView();
            return;
        }
        String title = state.selectedCategory == null ? "" : state.selectedCategory.getCategoryTitle();
        showDetailView(state.channelListUI, title);
    }

    private void removeSelectedCachedCategories() {
        List<CategoryItem> selected = selectedRemovableCategoryItems();
        if (selected.isEmpty()) {
            showErrorAlert(I18n.tr("categoryRemoveCachedNoSelection"));
            return;
        }
        int selectedCount = selected.size();
        if (!confirmCachedCategoryRemoval(selectedCount)) {
            return;
        }

        Account.AccountAction mode = activeMode;
        Account modeAccount = accountForMode(mode);
        List<String> categoryDbIds = selected.stream().map(CategoryItem::getId).toList();
        try {
            cancelCurrentLoadingRequest();
            CategoryCacheRemovalService.getInstance().removeCachedCategories(modeAccount, categoryDbIds);
            discardRemovedCategoryState(mode, selected);
            List<Category> categories = CategoryService.getInstance().getCached(modeAccount);
            modeStates.computeIfAbsent(mode, _ -> new ModeState()).categories = new ArrayList<>(categories);
            setItems(categories);
            selectedCategoryItems.clear();
            updateCategorySelectionStyles();
        } catch (Exception e) {
            showErrorAlert(I18n.tr("autoFailed") + ": " + e.getMessage());
        }
    }

    private List<CategoryItem> selectedRemovableCategoryItems() {
        Map<String, CategoryItem> uniqueItems = new LinkedHashMap<>();
        for (CategoryItem item : selectedCategoryItems) {
            if (isRemovableCategoryItem(item)) {
                uniqueItems.putIfAbsent(item.getId().trim(), item);
            }
        }
        return new ArrayList<>(uniqueItems.values());
    }

    private boolean isRemovableCategoryItem(CategoryItem item) {
        return item != null
                && item.getId() != null
                && !item.getId().trim().isEmpty()
                && !isAllCategory(item);
    }

    private boolean confirmCachedCategoryRemoval(int selectedCount) {
        ButtonType okButton = UIptvAlert.okButtonType();
        ButtonType closeButton = UIptvAlert.closeButtonType();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.tr("categoryRemoveCachedConfirmMessage",
                        I18n.formatNumber(String.valueOf(selectedCount)),
                        removalContentLabel(activeMode)),
                okButton,
                closeButton);
        alert.setTitle(I18n.tr("categoryRemoveCachedTitle"));
        alert.setHeaderText(I18n.tr("categoryRemoveCachedTitle"));
        javafx.stage.Window ownerWindow = getScene() == null
                ? ThemedDialogSupport.activeOwnerWindow()
                : getScene().getWindow();
        ThemedDialogSupport.prepare(alert, ownerWindow, "uiptv-alert-dialog");
        Optional<ButtonType> result = ThemedDialogSupport.showAndWait(alert, ownerWindow);
        return result.isPresent() && result.get() == okButton;
    }

    private String removalContentLabel(Account.AccountAction mode) {
        return switch (mode) {
            case vod -> I18n.tr("categoryRemoveCachedVodItems");
            case series -> I18n.tr("categoryRemoveCachedSeriesItems");
            case itv -> I18n.tr("categoryRemoveCachedLiveItems");
        };
    }

    private void cancelCurrentLoadingRequest() {
        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        Thread runningThread = currentLoadingThread.getAndSet(null);
        if (runningThread != null && runningThread.isAlive()) {
            runningThread.interrupt();
        }
    }

    private void abandonActiveChannelView() {
        cancelCurrentLoadingRequest();
        disposeChannelListState(modeStates.get(activeMode));
        detailContent.getChildren().clear();
        detailPane.getChildren().clear();
    }

    private void discardRemovedCategoryState(Account.AccountAction mode, List<CategoryItem> removedItems) {
        ModeState state = modeStates.computeIfAbsent(mode, _ -> new ModeState());
        if (state.selectedCategory == null) {
            return;
        }
        boolean selectedCategoryRemoved = removedItems.stream()
                .anyMatch(item -> sameCategorySelection(item, state.selectedCategory));
        if (!selectedCategoryRemoved) {
            return;
        }
        disposeChannelListState(state);
        showListView();
    }

    private void doRetrieveChannels(CategoryItem item) {
        if (item == null) {
            return;
        }
        if (!ensureCategoryAccess(item)) {
            return;
        }
        final Account.AccountAction mode = activeMode;
        final ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        if (state.selectedCategory != null
                && state.channelListUI != null
                && sameCategorySelection(state.selectedCategory, item)) {
            showDetailView(state.channelListUI, item.getCategoryTitle());
            return;
        }
        // Check if channels are already loaded for this account
        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(mode);

        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }

        Thread runningThread = currentLoadingThread.get();
        if (runningThread != null && runningThread.isAlive()) {
            RootApplication.getPrimaryStage().getScene().setCursor(Cursor.WAIT);
            runningThread.interrupt();
        }

        currentRequestCancelled = new AtomicBoolean(false);
        AtomicBoolean isCancelled = currentRequestCancelled;

        RootApplication.getPrimaryStage().getScene().setCursor(Cursor.WAIT);
        Thread loadingThread = new Thread(() -> {
            Thread worker = Thread.currentThread();
            try {
                retrieveChannels(item, noCachingNeeded, isCancelled::get, mode);
            } finally {
                if (currentLoadingThread.compareAndSet(worker, null)) {
                    Platform.runLater(() -> RootApplication.getPrimaryStage().getScene().setCursor(null));
                }
            }
        });
        loadingThread.setDaemon(true);
        currentLoadingThread.set(loadingThread);
        loadingThread.start();
    }

    private void retrieveChannels(CategoryItem item, boolean noCachingNeeded, BooleanSupplier isCancelled, Account.AccountAction mode) {
        if (item == null) {
            return;
        }
        Account modeAccount = accountForMode(mode);
        final ModeState state = modeStates.computeIfAbsent(mode, k -> new ModeState());
        final String selectedCategoryKey = selectedCategoryKey(item);
        final ChannelListUI[] channelListUIHolder = new ChannelListUI[1];
        final List<CategoryItem> allItems = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            if (isLoadingCancelled(isCancelled)) {
                latch.countDown();
                return;
            }
            initializeChannelListView(item, selectedCategoryKey, state, channelListUIHolder, allItems, latch, mode);
        });

        try {
            latch.await();
            if (isLoadingCancelled(isCancelled)) {
                return;
            }
            ChannelListUI channelListUI = channelListUIHolder[0];
            if (channelListUI == null) {
                return;
            }

            try {
                channelListUI.startLoadingProgressIfNeeded();
                loadChannelsIntoUi(item, noCachingNeeded, isCancelled, selectedCategoryKey, channelListUI, allItems, mode, modeAccount);
            } finally {
                if (!isLoadingCancelled(isCancelled)) {
                    channelListUI.setLoadingComplete();
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isLoadingCancelled(isCancelled)) {
                Platform.runLater(() -> showErrorAlert(I18n.tr("autoErrorLoadingChannels", e.getMessage())));
            }
        }
    }

    private String selectedCategoryKey(CategoryItem item) {
        if (item == null) {
            return "";
        }
        String key = accountType() == STALKER_PORTAL || accountType() == XTREME_API
                ? item.getCategoryId()
                : item.getCategoryTitle();
        return key != null ? key : "";
    }

    private void initializeChannelListView(CategoryItem item, String selectedCategoryKey, ModeState state,
                                           ChannelListUI[] channelListUIHolder, List<CategoryItem> allItems,
                                           CountDownLatch latch, Account.AccountAction mode) {
        String title = item.getCategoryTitle() != null ? item.getCategoryTitle() : "";
        ChannelListUI ui = new ChannelListUI(contextForMode(mode), title, selectedCategoryKey, mode);
        ui.setMediaDrawerMode(mediaDrawerMode);
        disposeChannelListState(state);
        channelListUIHolder[0] = ui;
        state.channelListUI = ui;
        state.selectedCategory = item;
        showDetailView(ui, title);
        if (isAllCategory(item)) {
            allItems.addAll(categoryItems);
        }
        latch.countDown();
    }

    private void loadChannelsIntoUi(CategoryItem item, boolean noCachingNeeded, BooleanSupplier isCancelled,
                                    String selectedCategoryKey, ChannelListUI channelListUI, List<CategoryItem> allItems,
                                    Account.AccountAction mode, Account modeAccount) throws IOException {
        boolean cachingNeeded = !noCachingNeeded;
        if (cachingNeeded && isAllCategory(item)) {
            loadAllCategoryChannels(item, isCancelled, channelListUI, allItems, mode, modeAccount);
            return;
        }
        if (isLoadingCancelled(isCancelled)) {
            return;
        }
        ChannelService.getInstance().get(selectedCategoryKey, modeAccount, item.getId(),
                message -> logChannelFetch(item, mode, message),
                channelListUI::addItems, isCancelled::getAsBoolean,
                progress -> channelListUI.updateLoadingProgress(progress.fetchedItems(), progress.totalItems(), progress.pageNumber(), progress.pageCount()));
    }

    private void loadAllCategoryChannels(CategoryItem item, BooleanSupplier isCancelled,
                                         ChannelListUI channelListUI, List<CategoryItem> allItems,
                                         Account.AccountAction mode, Account modeAccount) throws IOException {
        int existingChannelCount = ChannelService.getInstance().getChannelCountForAccount(accountDbId());
        if (existingChannelCount == 0) {
            return;
        }
        if (allItems.size() == 1 && isAllCategory(allItems.getFirst())) {
            ChannelService.getInstance().get(selectedCategoryKey(item), modeAccount, item.getId(),
                    message -> logChannelFetch(item, mode, message),
                    channelListUI::addItems, isCancelled::getAsBoolean,
                    progress -> channelListUI.updateLoadingProgress(progress.fetchedItems(), progress.totalItems(), progress.pageNumber(), progress.pageCount()));
            return;
        }
        for (CategoryItem categoryItem : allItems) {
            if (isLoadingCancelled(isCancelled)) {
                return;
            }
            if (categoryItem != null && !isAllCategory(categoryItem)) {
                ChannelService.getInstance().get(selectedCategoryKey(categoryItem), modeAccount, categoryItem.getId(),
                        message -> logChannelFetch(categoryItem, mode, message),
                        channelListUI::addItems, isCancelled::getAsBoolean,
                        progress -> channelListUI.updateLoadingProgress(progress.fetchedItems(), progress.totalItems(), progress.pageNumber(), progress.pageCount()));
            }
        }
    }

    private boolean ensureCategoryAccess(CategoryItem item) {
        if (accountType() != STALKER_PORTAL || !item.isCensored()) {
            return true;
        }
        boolean passwordConfigured = com.uiptv.service.FilterLockService.getInstance().hasPasswordConfigured();
        boolean sessionUnlocked = com.uiptv.service.FilterLockService.getInstance().isUnlocked();
        com.uiptv.util.AppLog.addInfoLog(CategoryListUI.class,
                PARENTAL_LOCK_LOG_PREFIX + "categoryAccessCheck"
                        + LOG_ACCOUNT + accountName()
                        + LOG_CATEGORY_ID + item.getCategoryId()
                        + LOG_TITLE + item.getCategoryTitle()
                        + " censored=true"
                        + " passwordConfigured=" + passwordConfigured
                        + " sessionUnlocked=" + sessionUnlocked);
        if (!FilterLockDialogs.ensureUnlocked(this, "filterLockUnlockCensoredCategoryReason")) {
            com.uiptv.util.AppLog.addWarningLog(CategoryListUI.class,
                    PARENTAL_LOCK_LOG_PREFIX + "categoryAccessDenied"
                            + LOG_ACCOUNT + accountName()
                            + LOG_CATEGORY_ID + item.getCategoryId()
                            + LOG_TITLE + item.getCategoryTitle());
            return false;
        }
        com.uiptv.util.AppLog.addInfoLog(CategoryListUI.class,
                PARENTAL_LOCK_LOG_PREFIX + "categoryAccessGranted"
                        + LOG_ACCOUNT + accountName()
                        + LOG_CATEGORY_ID + item.getCategoryId()
                        + LOG_TITLE + item.getCategoryTitle());
        return true;
    }

    private void logCategoryFetch(Account.AccountAction mode, String message) {
        com.uiptv.util.AppLog.addInfoLog(CategoryListUI.class,
                PARENTAL_LOCK_LOG_PREFIX + LOG_ACCOUNT_START + accountName()
                        + LOG_TYPE + accountType()
                        + LOG_ACTION + mode
                        + " categories: " + message);
    }

    private void logChannelFetch(CategoryItem item, Account.AccountAction mode, String message) {
        com.uiptv.util.AppLog.addInfoLog(CategoryListUI.class,
                PARENTAL_LOCK_LOG_PREFIX + LOG_ACCOUNT_START + accountName()
                        + LOG_TYPE + accountType()
                        + LOG_ACTION + mode
                        + LOG_CATEGORY_ID + item.getCategoryId()
                        + LOG_CHANNELS + message);
    }

    private boolean isLoadingCancelled(BooleanSupplier isCancelled) {
        return Thread.currentThread().isInterrupted() || isCancelled.getAsBoolean();
    }

    private boolean isAllCategory(CategoryItem item) {
        if (item == null) {
            return false;
        }
        String id = item.getId();
        String candidateCategoryId = item.getCategoryId();
        String title = item.getCategoryTitle();
        String commonAll = I18n.tr("commonAll");
        return (id != null && ALL_CATEGORY_SENTINEL.equalsIgnoreCase(id.trim()))
                || (candidateCategoryId != null && ALL_CATEGORY_SENTINEL.equalsIgnoreCase(candidateCategoryId.trim()))
                || (title != null && commonAll != null && title.equalsIgnoreCase(commonAll))
                || (title != null && title.equalsIgnoreCase(CategoryType.ALL.displayName()));
    }

    private boolean sameCategorySelection(CategoryItem left, CategoryItem right) {
        return Objects.equals(stableCategorySelectionKey(left), stableCategorySelectionKey(right));
    }

    private void disposeChannelListState(ModeState state) {
        if (state == null) {
            return;
        }
        if (state.channelListUI != null) {
            state.channelListUI.dispose();
            state.channelListUI = null;
        }
        state.selectedCategory = null;
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

    private void updateStyleClass(javafx.scene.Node node, String styleClass, boolean enabled) {
        if (node == null) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
            return;
        }
        node.getStyleClass().remove(styleClass);
    }

    private static class ModeState {
        private List<Category> categories = new ArrayList<>();
        private CategoryItem selectedCategory;
        private ChannelListUI channelListUI;
    }

    public static class CategoryItem {

        private final SimpleStringProperty categoryTitle;
        private final SimpleStringProperty categoryId;
        private final SimpleStringProperty id;
        private final boolean censored;


        public CategoryItem(SimpleStringProperty id, SimpleStringProperty categoryTitle, SimpleStringProperty categoryId, boolean censored) {
            this.id = id;
            this.categoryTitle = categoryTitle;
            this.categoryId = categoryId;
            this.censored = censored;
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

        public boolean isCensored() {
            return censored;
        }
    }
}
