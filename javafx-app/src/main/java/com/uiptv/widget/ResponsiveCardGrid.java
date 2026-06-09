package com.uiptv.widget;

import com.uiptv.ui.util.UiI18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ResponsiveCardGrid<T> extends StackPane {
    @FunctionalInterface
    public interface ContextMenuFactory<T> {
        ContextMenu create(T item, List<T> selectedItems, Node owner);
    }

    private static final DataFormat CARD_INDEX_FORMAT = new DataFormat("application/x-uiptv-responsive-card-index");
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final double DEFAULT_MIN_CARD_WIDTH = 220;
    private static final double DEFAULT_MAX_CARD_WIDTH = 320;
    private static final double DEFAULT_HORIZONTAL_GAP = 14;
    private static final double DEFAULT_VERTICAL_GAP = 12;
    private static final double SCROLL_VISIBILITY_TOLERANCE = 4.0;
    private static final double SCROLL_VALUE_TOLERANCE = 0.001;
    private static final int DEFAULT_VIRTUALIZATION_THRESHOLD =
            Integer.getInteger("uiptv.cardGrid.virtualizationThreshold", 500);
    private static final int DEFAULT_VIRTUAL_ROW_BUFFER =
            Integer.getInteger("uiptv.cardGrid.virtualRowBuffer", 6);
    private static final double DEFAULT_VIRTUAL_VIEWPORT_HEIGHT = 900;
    private static final double MIN_VIRTUAL_CARD_HEIGHT = 32;

    private final Function<T, Region> cardFactory;
    private final FlowPane cardPane = new FlowPane();
    private final StackPane placeholder = new StackPane();
    private final Label placeholderLabel = new Label();
    private final Map<T, Region> cardsByItem = new LinkedHashMap<>();
    private final ObservableList<T> selectedItems = FXCollections.observableArrayList();
    private final ObservableList<T> readonlySelectedItems = FXCollections.unmodifiableObservableList(selectedItems);
    private final ListChangeListener<T> itemChangeListener = this::handleItemsChanged;
    private final ChangeListener<Number> virtualScrollValueListener = (_, _, _) -> scheduleVirtualWindowUpdate();
    private final ChangeListener<Bounds> virtualViewportBoundsListener = (_, _, _) -> scheduleVirtualWindowUpdate();
    private ObservableList<T> items = FXCollections.observableArrayList();
    private ContextMenuFactory<T> contextMenuFactory;
    private Consumer<T> itemActivatedHandler;
    private Consumer<List<T>> itemsReorderedHandler;
    private Predicate<T> singleClickActivationPredicate;
    private T focusedItem;
    private T anchorItem;
    private T mousePressedSelectionItem;
    private int focusedItemIndex = -1;
    private boolean reorderEnabled;
    private boolean activateOnSingleClick;
    private boolean mouseSelectionInProgress;
    private boolean suppressFocusSelection;
    private boolean singleColumn;
    private boolean virtualizationEnabled = true;
    private boolean virtualizedActive;
    private boolean virtualWindowUpdateQueued;
    private int columnCount = 1;
    private int virtualizationThreshold = DEFAULT_VIRTUALIZATION_THRESHOLD;
    private int virtualRowBuffer = DEFAULT_VIRTUAL_ROW_BUFFER;
    private int firstRenderedIndex = -1;
    private int lastRenderedExclusive = -1;
    private double minCardWidth = DEFAULT_MIN_CARD_WIDTH;
    private double maxCardWidth = DEFAULT_MAX_CARD_WIDTH;
    private double cardMinHeight = 76;
    private double horizontalGap = DEFAULT_HORIZONTAL_GAP;
    private double verticalGap = DEFAULT_VERTICAL_GAP;
    private double computedCardWidth = DEFAULT_MIN_CARD_WIDTH;
    private double measuredVirtualCardHeight;
    private double virtualContentHeight;
    private double renderedCardWidth = -1;
    private double renderedTranslateY = -1;
    private ScrollPane virtualScrollPane;

    public ResponsiveCardGrid(Function<T, Region> cardFactory) {
        this.cardFactory = Objects.requireNonNull(cardFactory, "cardFactory");
        getStyleClass().add("uiptv-responsive-card-grid");
        UiRenderQuality.optimizeLayout(this);
        setFocusTraversable(true);
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        cardPane.getStyleClass().add("uiptv-responsive-card-flow");
        UiRenderQuality.optimizeLayout(cardPane);
        cardPane.setPadding(new Insets(2, 2, 18, 2));
        cardPane.setHgap(horizontalGap);
        cardPane.setVgap(verticalGap);
        cardPane.setMinSize(0, 0);
        cardPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        placeholder.getStyleClass().add("uiptv-responsive-card-placeholder");
        placeholderLabel.getStyleClass().add("uiptv-responsive-card-placeholder-label");
        UiRenderQuality.optimizeTextNode(placeholderLabel);
        placeholder.setVisible(false);
        placeholder.setManaged(false);

        getChildren().addAll(cardPane, placeholder);
        items.addListener(itemChangeListener);
        widthProperty().addListener((_, _, _) -> updateCardWidths());
        parentProperty().addListener((_, _, _) -> scheduleVirtualScrollPaneHook());
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                scheduleInitialItemFocus();
                scheduleVirtualScrollPaneHook();
            } else {
                detachVirtualScrollPane();
            }
        });
        visibleProperty().addListener((_, _, visible) -> {
            if (Boolean.TRUE.equals(visible)) {
                scheduleInitialItemFocus();
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleGridTabTraversalKeyPressed);
        addEventHandler(KeyEvent.KEY_PRESSED, this::handleNavigationKeyPressed);
        rebuildCards();
    }

    public void setItems(ObservableList<T> items) {
        this.items.removeListener(itemChangeListener);
        this.items = items == null ? FXCollections.observableArrayList() : items;
        this.items.addListener(itemChangeListener);
        measuredVirtualCardHeight = 0;
        rebuildCards();
    }

    public ObservableList<T> getItems() {
        return items;
    }

    public ObservableList<T> getSelectedItems() {
        return readonlySelectedItems;
    }

    public T getFocusedItem() {
        if (focusedItem != null && items.contains(focusedItem)) {
            return focusedItem;
        }
        return selectedItems.isEmpty() ? null : selectedItems.getFirst();
    }

    public void setPlaceholderText(String text) {
        String safeText = text == null ? "" : text;
        placeholderLabel.setText(safeText);
        if (safeText.isBlank()) {
            placeholder.getChildren().clear();
        } else {
            placeholder.getChildren().setAll(placeholderLabel);
        }
        updatePlaceholderVisibility();
    }

    public void setPlaceholderNode(Node node) {
        if (node == null) {
            placeholder.getChildren().clear();
        } else {
            placeholder.getChildren().setAll(node);
        }
        updatePlaceholderVisibility();
    }

    public void setContextMenuFactory(ContextMenuFactory<T> contextMenuFactory) {
        this.contextMenuFactory = contextMenuFactory;
    }

    public void setOnItemActivated(Consumer<T> itemActivatedHandler) {
        this.itemActivatedHandler = itemActivatedHandler;
    }

    public void setActivateOnSingleClick(boolean activateOnSingleClick) {
        this.activateOnSingleClick = activateOnSingleClick;
    }

    public void setSingleClickActivationPredicate(Predicate<T> singleClickActivationPredicate) {
        this.singleClickActivationPredicate = singleClickActivationPredicate;
    }

    public void setSingleColumn(boolean singleColumn) {
        if (this.singleColumn == singleColumn) {
            return;
        }
        this.singleColumn = singleColumn;
        updateCardWidths();
    }

    public void setVirtualizationEnabled(boolean virtualizationEnabled) {
        if (this.virtualizationEnabled == virtualizationEnabled) {
            return;
        }
        this.virtualizationEnabled = virtualizationEnabled;
        rebuildCards();
    }

    public void setVirtualizationThreshold(int virtualizationThreshold) {
        int safeThreshold = Math.max(1, virtualizationThreshold);
        if (this.virtualizationThreshold == safeThreshold) {
            return;
        }
        this.virtualizationThreshold = safeThreshold;
        rebuildCards();
    }

    public void setVirtualRowBuffer(int virtualRowBuffer) {
        this.virtualRowBuffer = Math.max(1, virtualRowBuffer);
        scheduleVirtualWindowUpdate();
    }

    public void setOnItemsReordered(Consumer<List<T>> itemsReorderedHandler) {
        this.itemsReorderedHandler = itemsReorderedHandler;
    }

    public void setReorderEnabled(boolean reorderEnabled) {
        this.reorderEnabled = reorderEnabled;
        rebuildCards();
    }

    public void setCardWidthRange(double minCardWidth, double maxCardWidth) {
        this.minCardWidth = Math.max(120, minCardWidth);
        this.maxCardWidth = Math.max(this.minCardWidth, maxCardWidth);
        updateCardWidths();
    }

    public void setGaps(double horizontalGap, double verticalGap) {
        this.horizontalGap = Math.max(0, horizontalGap);
        this.verticalGap = Math.max(0, verticalGap);
        cardPane.setHgap(this.horizontalGap);
        cardPane.setVgap(this.verticalGap);
        updateCardWidths();
    }

    public void setCardMinHeight(double cardMinHeight) {
        this.cardMinHeight = Math.max(24, cardMinHeight);
        measuredVirtualCardHeight = 0;
        for (Region card : cardsByItem.values()) {
            card.setMinHeight(this.cardMinHeight);
        }
        updateCardWidths();
    }

    public void refresh() {
        rebuildCards();
    }

    public void requestContentFocus() {
        ensureInitialSelection();
        scheduleInitialItemFocus();
    }

    public void clearSelection() {
        selectedItems.clear();
        anchorItem = null;
        focusedItem = null;
        focusedItemIndex = -1;
        updateSelectionStyles();
    }

    public void selectItems(Collection<T> itemsToSelect) {
        selectedItems.clear();
        if (itemsToSelect != null) {
            for (T item : itemsToSelect) {
                if (items.contains(item) && !selectedItems.contains(item)) {
                    selectedItems.add(item);
                }
            }
        }
        focusedItem = selectedItems.isEmpty() ? null : selectedItems.getLast();
        rememberFocusedIndex(focusedItem);
        anchorItem = focusedItem;
        updateSelectionStyles();
    }

    private void rebuildCards() {
        pruneSelection();
        cardsByItem.clear();
        cardPane.getChildren().clear();
        firstRenderedIndex = -1;
        lastRenderedExclusive = -1;
        renderedCardWidth = -1;
        renderedTranslateY = -1;
        virtualizedActive = shouldUseVirtualization();
        if (virtualizedActive) {
            ensureInitialSelection();
            updatePlaceholderVisibility();
            updateCardWidths();
            updateVirtualContentHeight();
            installVirtualScrollPane();
            updateVirtualWindow();
            updateSelectionStyles();
            scheduleInitialItemFocus();
            return;
        }
        clearVirtualContentLayout();
        for (T item : items) {
            Region card = cardFactory.apply(item);
            configureCard(item, card);
            cardsByItem.put(item, card);
            cardPane.getChildren().add(card);
        }
        updatePlaceholderVisibility();
        updateCardWidths();
        updateSelectionStyles();
        ensureInitialSelection();
        scheduleInitialItemFocus();
    }

    private void handleItemsChanged(ListChangeListener.Change<? extends T> change) {
        if (virtualizedActive || shouldUseVirtualization()) {
            rebuildCards();
            return;
        }
        boolean changed = false;
        while (change.next()) {
            if (change.wasPermutated() || change.wasRemoved()) {
                rebuildCards();
                return;
            }
            if (change.wasUpdated()) {
                updateCards(change.getFrom(), change.getTo());
                changed = true;
                continue;
            }
            if (change.wasAdded()) {
                insertCards(change.getFrom(), new ArrayList<>(change.getAddedSubList()));
                changed = true;
            }
        }
        if (!changed) {
            updatePlaceholderVisibility();
            return;
        }
        pruneSelection();
        updatePlaceholderVisibility();
        updateCardWidths();
        updateSelectionStyles();
        ensureInitialSelection();
        scheduleInitialItemFocus();
    }

    private void insertCards(int index, List<T> newItems) {
        if (newItems.isEmpty()) {
            return;
        }
        int insertIndex = Math.max(0, Math.min(index, cardPane.getChildren().size()));
        for (T item : newItems) {
            Region card = cardFactory.apply(item);
            configureCard(item, card);
            cardsByItem.put(item, card);
            cardPane.getChildren().add(insertIndex++, card);
        }
    }

    private void updateCards(int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(to, Math.min(items.size(), cardPane.getChildren().size()));
        for (int index = start; index < end; index++) {
            T item = items.get(index);
            Region card = cardFactory.apply(item);
            configureCard(item, card);
            cardsByItem.put(item, card);
            cardPane.getChildren().set(index, card);
        }
    }

    private boolean shouldUseVirtualization() {
        return virtualizationEnabled && items.size() >= virtualizationThreshold;
    }

    private void clearVirtualContentLayout() {
        detachVirtualScrollPane();
        virtualContentHeight = 0;
        measuredVirtualCardHeight = 0;
        cardPane.setTranslateY(0);
        setPrefHeight(Region.USE_COMPUTED_SIZE);
        setMinHeight(0);
    }

    private void scheduleVirtualScrollPaneHook() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::scheduleVirtualScrollPaneHook);
            return;
        }
        Platform.runLater(this::installVirtualScrollPane);
    }

    private void installVirtualScrollPane() {
        if (!virtualizedActive || getScene() == null) {
            detachVirtualScrollPane();
            return;
        }
        ScrollPane scrollPane = findAncestorScrollPane();
        if (scrollPane == virtualScrollPane) {
            return;
        }
        detachVirtualScrollPane();
        virtualScrollPane = scrollPane;
        if (virtualScrollPane != null) {
            virtualScrollPane.vvalueProperty().addListener(virtualScrollValueListener);
            virtualScrollPane.viewportBoundsProperty().addListener(virtualViewportBoundsListener);
        }
        scheduleVirtualWindowUpdate();
    }

    private void detachVirtualScrollPane() {
        if (virtualScrollPane == null) {
            return;
        }
        virtualScrollPane.vvalueProperty().removeListener(virtualScrollValueListener);
        virtualScrollPane.viewportBoundsProperty().removeListener(virtualViewportBoundsListener);
        virtualScrollPane = null;
    }

    private void scheduleVirtualWindowUpdate() {
        if (!virtualizedActive || virtualWindowUpdateQueued) {
            return;
        }
        virtualWindowUpdateQueued = true;
        Platform.runLater(() -> {
            virtualWindowUpdateQueued = false;
            updateVirtualWindow();
        });
    }

    private void updateVirtualWindow() {
        if (!virtualizedActive) {
            return;
        }
        if (!shouldUseVirtualization()) {
            rebuildCards();
            return;
        }
        updateVirtualContentHeight();
        int totalRows = virtualRowCount();
        if (items.isEmpty() || totalRows <= 0) {
            renderVirtualWindow(0, 0, 0);
            return;
        }
        double viewportHeight = virtualViewportHeight();
        double scrollTop = virtualScrollTop(viewportHeight);
        Insets padding = cardPane.getPadding();
        double rowStride = virtualRowStride();
        double rowAreaTop = Math.max(0, scrollTop - padding.getTop());
        int firstVisibleRow = rowStride <= 0 ? 0 : (int) Math.floor(rowAreaTop / rowStride);
        int visibleRows = Math.max(1, (int) Math.ceil(viewportHeight / Math.max(1, rowStride)) + 1);
        int firstRow = Math.max(0, firstVisibleRow - virtualRowBuffer);
        int lastRow = Math.min(totalRows, firstVisibleRow + visibleRows + virtualRowBuffer);
        int firstIndex = Math.max(0, firstRow * Math.max(1, columnCount));
        int lastIndex = Math.min(items.size(), lastRow * Math.max(1, columnCount));
        renderVirtualWindow(firstIndex, lastIndex, firstRow * rowStride);
    }

    private void renderVirtualWindow(int firstIndex, int lastIndex, double translateY) {
        int safeFirst = Math.max(0, Math.min(firstIndex, items.size()));
        int safeLast = Math.max(safeFirst, Math.min(lastIndex, items.size()));
        if (safeFirst == firstRenderedIndex
                && safeLast == lastRenderedExclusive
                && Math.abs(renderedCardWidth - computedCardWidth) < 0.5
                && Math.abs(renderedTranslateY - translateY) < 0.5) {
            return;
        }
        cardsByItem.clear();
        cardPane.getChildren().clear();
        for (int index = safeFirst; index < safeLast; index++) {
            T item = items.get(index);
            Region card = cardFactory.apply(item);
            configureCard(item, card);
            applyComputedCardWidth(card);
            cardsByItem.put(item, card);
            cardPane.getChildren().add(card);
        }
        firstRenderedIndex = safeFirst;
        lastRenderedExclusive = safeLast;
        renderedCardWidth = computedCardWidth;
        renderedTranslateY = translateY;
        cardPane.setTranslateY(translateY);
        updateSelectionStyles();
        scheduleVirtualCardHeightMeasurement();
    }

    private void scheduleVirtualCardHeightMeasurement() {
        if (!virtualizedActive || cardsByItem.isEmpty()) {
            return;
        }
        Platform.runLater(this::measureRenderedVirtualCardHeight);
    }

    private void measureRenderedVirtualCardHeight() {
        if (!virtualizedActive || cardsByItem.isEmpty()) {
            return;
        }
        double maxHeight = 0;
        for (Region card : cardsByItem.values()) {
            double height = card.getLayoutBounds().getHeight();
            if (height <= 0) {
                height = card.prefHeight(computedCardWidth);
            }
            maxHeight = Math.max(maxHeight, height);
        }
        double measured = Math.max(cardMinHeight, Math.ceil(maxHeight));
        if (measured <= 0 || measured <= measuredVirtualCardHeight + 1) {
            return;
        }
        measuredVirtualCardHeight = measured;
        updateVirtualContentHeight();
        firstRenderedIndex = -1;
        scheduleVirtualWindowUpdate();
    }

    private void updateVirtualContentHeight() {
        if (!virtualizedActive) {
            return;
        }
        Insets padding = cardPane.getPadding();
        int rows = virtualRowCount();
        double cardHeight = virtualCardHeight();
        double height = padding.getTop() + padding.getBottom();
        if (rows > 0) {
            height += (rows * cardHeight) + (Math.max(0, rows - 1) * verticalGap);
        }
        virtualContentHeight = Math.max(height, 0);
        setMinHeight(virtualContentHeight);
        setPrefHeight(virtualContentHeight);
    }

    private int virtualRowCount() {
        int columns = Math.max(1, columnCount);
        return items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / columns);
    }

    private double virtualCardHeight() {
        return Math.max(MIN_VIRTUAL_CARD_HEIGHT, Math.max(cardMinHeight, measuredVirtualCardHeight));
    }

    private double virtualRowStride() {
        return virtualCardHeight() + verticalGap;
    }

    private double virtualViewportHeight() {
        ScrollPane scrollPane = virtualScrollPane == null ? findAncestorScrollPane() : virtualScrollPane;
        if (scrollPane != null && scrollPane.getViewportBounds().getHeight() > 0) {
            return scrollPane.getViewportBounds().getHeight();
        }
        if (getHeight() > 0) {
            return getHeight();
        }
        return DEFAULT_VIRTUAL_VIEWPORT_HEIGHT;
    }

    private double virtualScrollTop(double viewportHeight) {
        ScrollPane scrollPane = virtualScrollPane == null ? findAncestorScrollPane() : virtualScrollPane;
        if (scrollPane == null) {
            return 0;
        }
        double scrollableHeight = Math.max(0, virtualContentHeight - viewportHeight);
        return clamp(scrollPane.getVvalue(), 0, 1) * scrollableHeight;
    }

    private void scrollItemIndexIntoEstimatedView(int itemIndex) {
        if (!virtualizedActive || itemIndex < 0 || itemIndex >= items.size()) {
            return;
        }
        updateVirtualContentHeight();
        ScrollPane scrollPane = virtualScrollPane == null ? findAncestorScrollPane() : virtualScrollPane;
        if (scrollPane == null) {
            int row = itemIndex / Math.max(1, columnCount);
            int firstRow = Math.max(0, row - virtualRowBuffer);
            int visibleRows = Math.max(1, (int) Math.ceil(DEFAULT_VIRTUAL_VIEWPORT_HEIGHT / Math.max(1, virtualRowStride())));
            renderVirtualWindow(
                    firstRow * Math.max(1, columnCount),
                    Math.min(items.size(), (firstRow + visibleRows + virtualRowBuffer) * Math.max(1, columnCount)),
                    firstRow * virtualRowStride());
            return;
        }
        double viewportHeight = virtualViewportHeight();
        double scrollableHeight = Math.max(0, virtualContentHeight - viewportHeight);
        if (scrollableHeight <= 0) {
            return;
        }
        Insets padding = cardPane.getPadding();
        int row = itemIndex / Math.max(1, columnCount);
        double itemTop = padding.getTop() + (row * virtualRowStride());
        double itemBottom = itemTop + virtualCardHeight();
        double currentTop = clamp(scrollPane.getVvalue(), 0, 1) * scrollableHeight;
        double currentBottom = currentTop + viewportHeight;
        double nextTop = currentTop;
        if (itemTop < currentTop) {
            nextTop = Math.max(0, itemTop - verticalGap);
        } else if (itemBottom > currentBottom) {
            nextTop = Math.min(scrollableHeight, itemBottom - viewportHeight + verticalGap);
        }
        scrollPane.setVvalue(clamp(nextTop / scrollableHeight, 0, 1));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void configureCard(T item, Region card) {
        if (!card.getStyleClass().contains("uiptv-responsive-card")) {
            card.getStyleClass().add("uiptv-responsive-card");
        }
        card.setCursor(Cursor.HAND);
        UiRenderQuality.optimizeLayout(card);
        card.setFocusTraversable(false);
        card.setMinHeight(cardMinHeight);
        card.setMaxWidth(maxCardWidth);

        card.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            mouseSelectionInProgress = true;
            if (event.getButton() == MouseButton.SECONDARY) {
                requestGridFocusPreservingScroll();
                normalizeContextSelection(item);
            } else if (event.getButton() == MouseButton.PRIMARY && !isInteractiveChildEvent(card, event)) {
                requestGridFocusPreservingScroll();
                updateSelectionForClick(item, event);
                mousePressedSelectionItem = item;
            }
            Platform.runLater(() -> mouseSelectionInProgress = false);
        });
        card.focusedProperty().addListener((_, _, focused) -> {
            if (Boolean.TRUE.equals(focused) && !mouseSelectionInProgress && !suppressFocusSelection) {
                focusSelectionFromFocusEvent(item);
            }
        });
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> handleCardClicked(item, card, event));
        card.setOnContextMenuRequested(event -> showContextMenu(item, card, event));
        if (reorderEnabled) {
            configureDragHandlers(item, card);
        }
        registerInteractiveChildFocusListeners(item, card);
    }

    private void handleCardClicked(T item, Region card, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (isInteractiveChildEvent(card, event)) {
            return;
        }
        boolean selectionHandledOnPress = Objects.equals(mousePressedSelectionItem, item);
        mousePressedSelectionItem = null;
        if (!selectionHandledOnPress) {
            updateSelectionForClick(item, event);
        }
        requestGridFocusPreservingScroll();
        boolean shouldActivate = itemActivatedHandler != null
                && !isSelectionModifierDown(event)
                && shouldActivateForClick(item, event.getClickCount());
        if (shouldActivate) {
            itemActivatedHandler.accept(item);
        }
        event.consume();
    }

    private boolean shouldActivateForClick(T item, int clickCount) {
        if (activateOnSingleClick) {
            return clickCount == 1 && isSingleClickActivationAllowed(item);
        }
        return clickCount == 2;
    }

    private boolean isSingleClickActivationAllowed(T item) {
        return singleClickActivationPredicate == null || singleClickActivationPredicate.test(item);
    }

    private boolean isInteractiveChildEvent(Region card, MouseEvent event) {
        Object target = event.getTarget();
        while (target instanceof Node node && node != card) {
            if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof ComboBoxBase<?>) {
                return true;
            }
            target = node.getParent();
        }
        return false;
    }

    private void updateSelectionForClick(T item, MouseEvent event) {
        if (event.isShiftDown() && anchorItem != null && items.contains(anchorItem)) {
            selectRange(anchorItem, item);
        } else if (isAdditiveSelectionModifierDown(event)) {
            toggleSelection(item);
            anchorItem = item;
        } else {
            selectOnly(item);
            anchorItem = item;
        }
        focusedItem = item;
        rememberFocusedIndex(item);
        updateSelectionStyles();
    }

    private boolean isSelectionModifierDown(MouseEvent event) {
        return event != null && (event.isShiftDown() || isAdditiveSelectionModifierDown(event));
    }

    private boolean isAdditiveSelectionModifierDown(MouseEvent event) {
        return event != null && (event.isShortcutDown() || event.isControlDown() || event.isMetaDown());
    }

    private boolean isAdditiveSelectionModifierDown(KeyEvent event) {
        return event != null && (event.isShortcutDown() || event.isControlDown() || event.isMetaDown());
    }

    private void handleGridTabTraversalKeyPressed(KeyEvent event) {
        if (event == null || event.getCode() != KeyCode.TAB || items.isEmpty() || getScene() == null) {
            return;
        }
        Node focusOwner = getScene().getFocusOwner();
        T ownerItem = itemForNode(focusOwner);
        if (ownerItem != null && focusableDescendantWithinCard(focusOwner, cardsByItem.get(ownerItem)) != null) {
            if (event.isShiftDown()) {
                focusItem(ownerItem);
                event.consume();
                return;
            }
            int ownerIndex = items.indexOf(ownerItem);
            if (ownerIndex >= 0 && ownerIndex < items.size() - 1) {
                focusItem(items.get(ownerIndex + 1));
                event.consume();
            }
            return;
        }
        if (focusOwner != this) {
            return;
        }
        int currentIndex = currentKeyboardIndex();
        if (event.isShiftDown()) {
            if (currentIndex > 0 && focusInteractiveChild(items.get(currentIndex - 1), true)) {
                event.consume();
            }
            return;
        }
        T currentItem = items.get(Math.max(0, Math.min(currentIndex, items.size() - 1)));
        if (focusInteractiveChild(currentItem, false)) {
            event.consume();
        }
    }

    private void registerInteractiveChildFocusListeners(T item, Region card) {
        for (Node child : focusableDescendants(card)) {
            child.focusedProperty().addListener((_, _, focused) -> {
                if (Boolean.TRUE.equals(focused) && !mouseSelectionInProgress && !suppressFocusSelection) {
                    focusSelectionFromFocusEvent(item);
                }
            });
        }
    }

    private boolean focusInteractiveChild(T item, boolean last) {
        Region card = cardsByItem.get(item);
        if (card == null) {
            return false;
        }
        List<Node> descendants = focusableDescendants(card);
        if (descendants.isEmpty()) {
            return false;
        }
        Node target = last ? descendants.getLast() : descendants.getFirst();
        focusSelection(item);
        scrollIntoPageView(card);
        target.requestFocus();
        return true;
    }

    private Node focusableDescendantWithinCard(Node node, Region card) {
        if (node == null || card == null) {
            return null;
        }
        Node current = node;
        while (current != null && current != card) {
            if (isFocusableDescendant(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private T itemForNode(Node node) {
        if (node == null) {
            return null;
        }
        for (Map.Entry<T, Region> entry : cardsByItem.entrySet()) {
            if (entry.getValue() == node || isDescendantOf(node, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private List<Node> focusableDescendants(Node root) {
        List<Node> descendants = new ArrayList<>();
        collectFocusableDescendants(root, descendants);
        return descendants;
    }

    private void collectFocusableDescendants(Node node, List<Node> descendants) {
        if (node == null) {
            return;
        }
        if (isFocusableDescendant(node)) {
            descendants.add(node);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectFocusableDescendants(child, descendants);
            }
        }
    }

    private boolean isFocusableDescendant(Node node) {
        return node != null
                && node != this
                && node.isFocusTraversable()
                && node.isVisible()
                && !node.isDisabled();
    }

    private void normalizeContextSelection(T item) {
        if (!selectedItems.contains(item)) {
            selectOnly(item);
        }
        focusedItem = item;
        rememberFocusedIndex(item);
        anchorItem = item;
        updateSelectionStyles();
    }

    private void toggleSelection(T item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
    }

    private void selectRange(T fromItem, T toItem) {
        int from = items.indexOf(fromItem);
        int to = items.indexOf(toItem);
        if (from < 0 || to < 0) {
            selectOnly(toItem);
            return;
        }
        int start = Math.min(from, to);
        int end = Math.max(from, to);
        selectedItems.setAll(items.subList(start, end + 1));
    }

    private void showContextMenu(T item, Region card, ContextMenuEvent event) {
        normalizeContextSelection(item);
        if (contextMenuFactory == null) {
            return;
        }
        ContextMenu menu = contextMenuFactory.create(item, List.copyOf(selectedItems), card);
        if (menu == null) {
            return;
        }
        UiI18n.preparePopupControl(menu, card);
        menu.show(card, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private void configureDragHandlers(T item, Region card) {
        card.setOnDragDetected(event -> {
            int sourceIndex = items.indexOf(item);
            if (sourceIndex < 0) {
                return;
            }
            normalizeContextSelection(item);
            Dragboard dragboard = card.startDragAndDrop(TransferMode.MOVE);
            dragboard.setDragView(card.snapshot(new SnapshotParameters(), null));
            ClipboardContent content = new ClipboardContent();
            content.put(CARD_INDEX_FORMAT, sourceIndex);
            dragboard.setContent(content);
            event.consume();
        });
        card.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasContent(CARD_INDEX_FORMAT) && items.indexOf(item) != (Integer) dragboard.getContent(CARD_INDEX_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        card.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            if (!dragboard.hasContent(CARD_INDEX_FORMAT)) {
                event.setDropCompleted(false);
                return;
            }
            int sourceIndex = (Integer) dragboard.getContent(CARD_INDEX_FORMAT);
            int targetIndex = items.indexOf(item);
            boolean completed = moveItem(sourceIndex, targetIndex);
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private boolean moveItem(int sourceIndex, int targetIndex) {
        if (sourceIndex < 0 || sourceIndex >= items.size() || targetIndex < 0 || targetIndex >= items.size() || sourceIndex == targetIndex) {
            return false;
        }
        T moved = items.remove(sourceIndex);
        int adjustedTarget = targetIndex;
        if (adjustedTarget > items.size()) {
            adjustedTarget = items.size();
        }
        items.add(adjustedTarget, moved);
        focusedItem = moved;
        rememberFocusedIndex(moved);
        selectOnly(moved);
        anchorItem = moved;
        if (itemsReorderedHandler != null) {
            itemsReorderedHandler.accept(List.copyOf(items));
        }
        return true;
    }

    private void handleNavigationKeyPressed(KeyEvent event) {
        if (items.isEmpty()) {
            return;
        }
        if (event.getCode() == KeyCode.A && isAdditiveSelectionModifierDown(event)) {
            selectItems(items);
            event.consume();
            return;
        }
        int currentIndex = currentKeyboardIndex();
        int targetIndex = switch (event.getCode()) {
            case LEFT -> Math.max(0, currentIndex - 1);
            case RIGHT -> Math.min(items.size() - 1, currentIndex + 1);
            case UP -> Math.max(0, currentIndex - columnCount);
            case DOWN -> Math.min(items.size() - 1, currentIndex + columnCount);
            case HOME -> 0;
            case END -> items.size() - 1;
            default -> currentIndex;
        };
        if (targetIndex != currentIndex) {
            T target = items.get(targetIndex);
            if (event.isShiftDown() && anchorItem != null && items.contains(anchorItem)) {
                selectRange(anchorItem, target);
                focusedItem = target;
                rememberFocusedIndex(target);
                updateSelectionStyles();
                requestFocusForItemPreservingSelection(target);
            } else if (isAdditiveSelectionModifierDown(event)) {
                focusedItem = target;
                rememberFocusedIndex(target);
                requestFocusForItemPreservingSelection(target);
            } else {
                focusItem(target);
            }
            event.consume();
        }
    }

    private int currentKeyboardIndex() {
        T current = getFocusedItem();
        int currentIndex = current == null ? -1 : items.indexOf(current);
        if (currentIndex >= 0) {
            return currentIndex;
        }
        return selectedItems.isEmpty() ? 0 : Math.max(0, items.indexOf(selectedItems.getFirst()));
    }

    private void ensureInitialSelection() {
        if (items.isEmpty()) {
            focusedItem = null;
            anchorItem = null;
            focusedItemIndex = -1;
            updateSelectionStyles();
            return;
        }
        if (focusedItem != null && items.contains(focusedItem)) {
            rememberFocusedIndex(focusedItem);
            return;
        }
        if (!selectedItems.isEmpty()) {
            focusedItem = selectedItems.getFirst();
            rememberFocusedIndex(focusedItem);
            anchorItem = focusedItem;
            updateSelectionStyles();
            return;
        }
        focusedItem = itemAtRememberedIndex();
        anchorItem = focusedItem;
        selectOnly(focusedItem);
        updateSelectionStyles();
    }

    private void scheduleInitialItemFocus() {
        if (items.isEmpty()) {
            return;
        }
        T target = getFocusedItem();
        if (target == null) {
            return;
        }
        Platform.runLater(() -> focusSelectedItemIfAppropriate(target));
    }

    private void focusSelectedItemIfAppropriate(T item) {
        if (item == null || !isDisplayable() || !items.contains(item)) {
            return;
        }
        if (!Objects.equals(item, getFocusedItem())) {
            return;
        }
        Node focusOwner = getScene().getFocusOwner();
        if (focusOwner instanceof TextInputControl || isDescendantOf(focusOwner, this)) {
            return;
        }
        Region card = cardsByItem.get(item);
        if (card != null) {
            requestGridFocusPreservingScroll();
        }
    }

    private boolean isDescendantOf(Node node, Node ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isDisplayable() {
        if (getScene() == null) {
            return false;
        }
        Node node = this;
        while (node != null) {
            if (!node.isVisible()) {
                return false;
            }
            node = node.getParent();
        }
        return true;
    }

    private void updateCardWidths() {
        double available = getWidth();
        Insets padding = cardPane.getPadding();
        available = Math.max(0, available - padding.getLeft() - padding.getRight() - 2);
        if (available <= 0) {
            if (virtualizedActive) {
                updateVirtualContentHeight();
                scheduleVirtualWindowUpdate();
            }
            return;
        }

        int previousColumnCount = columnCount;
        double previousCardWidth = computedCardWidth;
        if (singleColumn) {
            columnCount = 1;
            computedCardWidth = available;
            cardPane.setPrefWrapLength(available);
            for (Region card : cardsByItem.values()) {
                applyComputedCardWidth(card);
            }
            updateVirtualLayoutAfterWidthChange(previousColumnCount, previousCardWidth);
            return;
        }

        int columns = Math.max(1, (int) Math.floor((available + horizontalGap) / (minCardWidth + horizontalGap)));
        double cardWidth = calculateCardWidth(available, columns);
        while (cardWidth > maxCardWidth && calculateCardWidth(available, columns + 1) >= minCardWidth) {
            columns++;
            cardWidth = calculateCardWidth(available, columns);
        }
        columnCount = Math.max(1, columns);
        computedCardWidth = cardWidth;
        cardPane.setPrefWrapLength(available);
        for (Region card : cardsByItem.values()) {
            applyComputedCardWidth(card);
        }
        updateVirtualLayoutAfterWidthChange(previousColumnCount, previousCardWidth);
    }

    private void applyComputedCardWidth(Region card) {
        if (card == null) {
            return;
        }
        card.setMinWidth(computedCardWidth);
        card.setPrefWidth(computedCardWidth);
        card.setMaxWidth(computedCardWidth);
    }

    private void updateVirtualLayoutAfterWidthChange(int previousColumnCount, double previousCardWidth) {
        if (!virtualizedActive) {
            return;
        }
        updateVirtualContentHeight();
        if (previousColumnCount != columnCount || Math.abs(previousCardWidth - computedCardWidth) >= 0.5) {
            firstRenderedIndex = -1;
        }
        scheduleVirtualWindowUpdate();
    }

    private void focusItem(T item) {
        focusSelection(item);
        requestFocusForItem(item);
    }

    private void requestFocusForItem(T item) {
        requestFocusForItem(item, false);
    }

    private void requestFocusForItemPreservingSelection(T item) {
        requestFocusForItem(item, true);
    }

    private void requestFocusForItem(T item, boolean preserveSelection) {
        if (virtualizedActive) {
            int itemIndex = items.indexOf(item);
            scrollItemIndexIntoEstimatedView(itemIndex);
            updateVirtualWindow();
        }
        Region card = cardsByItem.get(item);
        if (card != null) {
            boolean previousSuppressFocusSelection = suppressFocusSelection;
            suppressFocusSelection = suppressFocusSelection || preserveSelection;
            try {
                requestGridFocusPreservingScroll(() -> scrollIntoPageView(card));
            } finally {
                suppressFocusSelection = previousSuppressFocusSelection;
            }
        } else {
            requestGridFocus();
        }
    }

    private void requestGridFocus() {
        if (isDisplayable()) {
            requestFocus();
        }
    }

    private void requestGridFocusPreservingScroll() {
        requestGridFocusPreservingScroll(null);
    }

    private void requestGridFocusPreservingScroll(Runnable afterRestore) {
        if (!isDisplayable()) {
            return;
        }
        ScrollPane scrollPane = findAncestorScrollPane();
        if (scrollPane == null) {
            requestGridFocus();
            if (afterRestore != null) {
                Platform.runLater(afterRestore);
            }
            return;
        }
        double originalHValue = scrollPane.getHvalue();
        double originalVValue = scrollPane.getVvalue();
        requestFocus();
        Platform.runLater(() -> {
            restoreScrollPosition(scrollPane, originalHValue, originalVValue);
            if (afterRestore != null) {
                afterRestore.run();
            }
            Platform.runLater(() -> {
                if (afterRestore == null) {
                    restoreScrollPosition(scrollPane, originalHValue, originalVValue);
                } else {
                    afterRestore.run();
                }
            });
        });
    }

    private void restoreScrollPosition(ScrollPane scrollPane, double hValue, double vValue) {
        if (scrollPane == null || scrollPane.getScene() == null) {
            return;
        }
        scrollPane.setHvalue(hValue);
        scrollPane.setVvalue(vValue);
    }

    private void focusSelection(T item) {
        selectOnly(item);
        focusedItem = item;
        rememberFocusedIndex(item);
        anchorItem = item;
        updateSelectionStyles();
    }

    private void focusSelectionFromFocusEvent(T item) {
        if (selectedItems.contains(item)) {
            focusedItem = item;
            rememberFocusedIndex(item);
            if (anchorItem == null || !items.contains(anchorItem)) {
                anchorItem = item;
            }
            updateSelectionStyles();
            return;
        }
        focusSelection(item);
    }

    private void scrollIntoPageView(Region card) {
        ScrollPane pageScrollPane = findAncestorScrollPane();
        if (pageScrollPane == null || pageScrollPane.getContent() == null) {
            return;
        }
        double viewportHeight = pageScrollPane.getViewportBounds().getHeight();
        double contentHeight = pageScrollPane.getContent().getLayoutBounds().getHeight();
        if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
            return;
        }
        Bounds cardBounds = card.localToScene(card.getBoundsInLocal());
        Bounds viewportBounds = pageScrollPane.localToScene(pageScrollPane.getViewportBounds());
        double cardTop = cardBounds.getMinY();
        double cardBottom = cardBounds.getMaxY();
        double viewportTop = viewportBounds.getMinY();
        double viewportBottom = viewportBounds.getMaxY();
        if (cardTop >= viewportTop - SCROLL_VISIBILITY_TOLERANCE
                && cardBottom <= viewportBottom + SCROLL_VISIBILITY_TOLERANCE) {
            return;
        }
        double scrollableHeight = contentHeight - viewportHeight;
        double currentPixels = pageScrollPane.getVvalue() * scrollableHeight;
        double nextPixels = currentPixels;
        if (cardTop < viewportTop) {
            nextPixels -= viewportTop - cardTop + verticalGap;
        } else if (cardBottom > viewportBottom) {
            nextPixels += cardBottom - viewportBottom + verticalGap;
        }
        double nextValue = Math.max(0, Math.min(1, nextPixels / scrollableHeight));
        if (Math.abs(nextValue - pageScrollPane.getVvalue()) >= SCROLL_VALUE_TOLERANCE) {
            pageScrollPane.setVvalue(nextValue);
        }
    }

    private ScrollPane findAncestorScrollPane() {
        Node node = getParent();
        while (node != null) {
            if (node instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            node = node.getParent();
        }
        return null;
    }

    private double calculateCardWidth(double available, int columns) {
        return Math.floor((available - (horizontalGap * (columns - 1))) / columns);
    }

    private void pruneSelection() {
        selectedItems.removeIf(item -> !items.contains(item));
        if (focusedItem != null && !items.contains(focusedItem)) {
            if (selectedItems.isEmpty()) {
                focusedItem = null;
            } else {
                focusedItem = selectedItems.getFirst();
                rememberFocusedIndex(focusedItem);
            }
        }
        if (anchorItem != null && !items.contains(anchorItem)) {
            anchorItem = focusedItem;
        }
    }

    private void updateSelectionStyles() {
        for (Map.Entry<T, Region> entry : cardsByItem.entrySet()) {
            boolean selected = selectedItems.contains(entry.getKey());
            Region card = entry.getValue();
            card.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, selected);
            if (selected && !card.getStyleClass().contains("selected")) {
                card.getStyleClass().add("selected");
            } else if (!selected) {
                card.getStyleClass().remove("selected");
            }
        }
    }

    private void selectOnly(T item) {
        selectedItems.clear();
        selectedItems.add(item);
    }

    private void rememberFocusedIndex(T item) {
        focusedItemIndex = item == null ? -1 : items.indexOf(item);
    }

    private T itemAtRememberedIndex() {
        if (focusedItemIndex >= 0 && focusedItemIndex < items.size()) {
            return items.get(focusedItemIndex);
        }
        return items.getFirst();
    }

    private void updatePlaceholderVisibility() {
        boolean showPlaceholder = items.isEmpty() && !placeholder.getChildren().isEmpty();
        placeholder.setVisible(showPlaceholder);
        placeholder.setManaged(showPlaceholder);
    }
}
