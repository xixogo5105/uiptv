package com.uiptv.widget;

import com.uiptv.ui.util.UiI18n;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
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

    private final Function<T, Region> cardFactory;
    private final FlowPane cardPane = new FlowPane();
    private final Label placeholder = new Label();
    private final Map<T, Region> cardsByItem = new LinkedHashMap<>();
    private final ObservableList<T> selectedItems = FXCollections.observableArrayList();
    private final ObservableList<T> readonlySelectedItems = FXCollections.unmodifiableObservableList(selectedItems);
    private final ListChangeListener<T> itemChangeListener = _ -> rebuildCards();
    private ObservableList<T> items = FXCollections.observableArrayList();
    private ContextMenuFactory<T> contextMenuFactory;
    private Consumer<T> itemActivatedHandler;
    private Consumer<List<T>> itemsReorderedHandler;
    private T focusedItem;
    private T anchorItem;
    private boolean reorderEnabled;
    private boolean mouseSelectionInProgress;
    private boolean activateOnSingleClick;
    private int columnCount = 1;
    private double minCardWidth = DEFAULT_MIN_CARD_WIDTH;
    private double maxCardWidth = DEFAULT_MAX_CARD_WIDTH;
    private double horizontalGap = DEFAULT_HORIZONTAL_GAP;
    private double verticalGap = DEFAULT_VERTICAL_GAP;

    public ResponsiveCardGrid(Function<T, Region> cardFactory) {
        this.cardFactory = Objects.requireNonNull(cardFactory, "cardFactory");
        getStyleClass().add("uiptv-responsive-card-grid");
        UiRenderQuality.optimizeLayout(this);
        setFocusTraversable(false);
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
        UiRenderQuality.optimizeTextNode(placeholder);
        placeholder.setVisible(false);
        placeholder.setManaged(false);

        getChildren().addAll(cardPane, placeholder);
        items.addListener(itemChangeListener);
        widthProperty().addListener((_, _, _) -> updateCardWidths());
        addEventHandler(KeyEvent.KEY_PRESSED, this::handleNavigationKeyPressed);
        rebuildCards();
    }

    public void setItems(ObservableList<T> items) {
        this.items.removeListener(itemChangeListener);
        this.items = items == null ? FXCollections.observableArrayList() : items;
        this.items.addListener(itemChangeListener);
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
        placeholder.setText(text == null ? "" : text);
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

    public void refresh() {
        rebuildCards();
    }

    public void clearSelection() {
        selectedItems.clear();
        anchorItem = null;
        focusedItem = null;
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
        anchorItem = focusedItem;
        updateSelectionStyles();
    }

    private void rebuildCards() {
        pruneSelection();
        cardsByItem.clear();
        cardPane.getChildren().clear();
        for (T item : items) {
            Region card = cardFactory.apply(item);
            configureCard(item, card);
            cardsByItem.put(item, card);
            cardPane.getChildren().add(card);
        }
        updatePlaceholderVisibility();
        updateCardWidths();
        updateSelectionStyles();
    }

    private void configureCard(T item, Region card) {
        if (!card.getStyleClass().contains("uiptv-responsive-card")) {
            card.getStyleClass().add("uiptv-responsive-card");
        }
        card.setCursor(Cursor.HAND);
        UiRenderQuality.optimizeLayout(card);
        card.setFocusTraversable(true);
        card.setMinHeight(76);
        card.setMaxWidth(maxCardWidth);

        card.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            mouseSelectionInProgress = true;
            card.requestFocus();
            if (event.getButton() == MouseButton.SECONDARY) {
                normalizeContextSelection(item);
            }
            Platform.runLater(() -> mouseSelectionInProgress = false);
        });
        card.focusedProperty().addListener((_, _, focused) -> {
            if (Boolean.TRUE.equals(focused) && !mouseSelectionInProgress) {
                focusSelection(item);
            }
        });
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> handleCardClicked(item, card, event));
        card.setOnContextMenuRequested(event -> showContextMenu(item, card, event));
        if (reorderEnabled) {
            configureDragHandlers(item, card);
        }
    }

    private void handleCardClicked(T item, Region card, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (isInteractiveChildEvent(card, event)) {
            return;
        }
        updateSelectionForClick(item, event);
        boolean shouldActivate = itemActivatedHandler != null
                && (event.getClickCount() == 2 || (activateOnSingleClick && event.getClickCount() == 1
                && !event.isShiftDown() && !event.isShortcutDown() && !event.isControlDown()));
        if (shouldActivate) {
            itemActivatedHandler.accept(item);
        }
        event.consume();
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
        } else if (event.isShortcutDown() || event.isControlDown()) {
            toggleSelection(item);
            anchorItem = item;
        } else {
            selectOnly(item);
            anchorItem = item;
        }
        focusedItem = item;
        updateSelectionStyles();
    }

    private void normalizeContextSelection(T item) {
        if (!selectedItems.contains(item)) {
            selectOnly(item);
        }
        focusedItem = item;
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
            focusItem(target);
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

    private void updateCardWidths() {
        double available = getWidth();
        Insets padding = cardPane.getPadding();
        available = Math.max(0, available - padding.getLeft() - padding.getRight() - 2);
        if (available <= 0) {
            return;
        }

        int columns = Math.max(1, (int) Math.floor((available + horizontalGap) / (minCardWidth + horizontalGap)));
        double cardWidth = calculateCardWidth(available, columns);
        while (cardWidth > maxCardWidth && calculateCardWidth(available, columns + 1) >= minCardWidth) {
            columns++;
            cardWidth = calculateCardWidth(available, columns);
        }
        columnCount = Math.max(1, columns);
        cardPane.setPrefWrapLength(available);
        for (Region card : cardsByItem.values()) {
            card.setMinWidth(cardWidth);
            card.setPrefWidth(cardWidth);
            card.setMaxWidth(cardWidth);
        }
    }

    private void focusItem(T item) {
        focusSelection(item);
        Region card = cardsByItem.get(item);
        if (card != null) {
            card.requestFocus();
            scrollIntoPageView(card);
        }
    }

    private void focusSelection(T item) {
        selectOnly(item);
        focusedItem = item;
        anchorItem = item;
        updateSelectionStyles();
    }

    private void scrollIntoPageView(Region card) {
        ScrollPane pageScrollPane = findAncestorScrollPane();
        if (pageScrollPane == null || pageScrollPane.getContent() == null) {
            return;
        }
        double viewportHeight = pageScrollPane.getViewportBounds().getHeight();
        double contentHeight = pageScrollPane.getContent().getBoundsInLocal().getHeight();
        if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
            return;
        }
        double cardTop = card.localToScene(card.getBoundsInLocal()).getMinY();
        double cardBottom = card.localToScene(card.getBoundsInLocal()).getMaxY();
        double viewportTop = pageScrollPane.localToScene(pageScrollPane.getBoundsInLocal()).getMinY();
        double viewportBottom = viewportTop + viewportHeight;
        if (cardTop >= viewportTop && cardBottom <= viewportBottom) {
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
        pageScrollPane.setVvalue(Math.max(0, Math.min(1, nextPixels / scrollableHeight)));
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
            focusedItem = selectedItems.isEmpty() ? null : selectedItems.getFirst();
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

    private void updatePlaceholderVisibility() {
        boolean showPlaceholder = items.isEmpty() && placeholder.getText() != null && !placeholder.getText().isBlank();
        placeholder.setVisible(showPlaceholder);
        placeholder.setManaged(showPlaceholder);
    }
}
