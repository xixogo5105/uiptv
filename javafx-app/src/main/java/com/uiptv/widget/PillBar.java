package com.uiptv.widget;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class PillBar<T> extends StackPane {
    private static final double BASE_FONT_SIZE = 13.0;
    private static final double SCROLLBAR_WIDTH_GUTTER = 18;
    private static final double SINGLE_ROW_HEADROOM = 16;
    private static final double NARROW_SIDE_PANE_WIDTH_THRESHOLD = 620;
    private static final double MIN_SINGLE_ROW_HEIGHT = 40;
    private static final double COMPACT_DROPDOWN_HEIGHT = 44;
    public static final double COMPACT_DROPDOWN_MIN_WIDTH = 112;
    public static final double COMPACT_DROPDOWN_PREF_WIDTH = 148;
    private static final double MIN_WRAPPED_ROW_INCREMENT = 48;
    private static final String COMPACT_STYLE_CLASS = "uiptv-pill-bar-compact";

    private final FlowPane content = new FlowPane();
    private final MenuButton compactDropdown = new MenuButton();
    private final ToggleGroup toggleGroup = new ToggleGroup();
    private final ToggleGroup compactToggleGroup = new ToggleGroup();
    private final Function<T, String> labelFactory;
    private final Function<T, String> compactLabelFactory;
    private final Function<T, ?> keyFactory;
    private final Function<T, Node> graphicFactory;
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private boolean rebuilding;
    private boolean syncingCompactSelection;
    private boolean compactMode;
    private boolean compactDropdownEnabled = true;
    private int reservedRowCount;
    private int narrowReservedRowCount;
    private int narrowItemsPerRow;
    private int appliedRowCount;
    private boolean appliedNarrowMode;
    private double appliedFixedHeight = Double.NaN;

    public PillBar(Function<T, String> labelFactory, Function<T, ?> keyFactory) {
        this(labelFactory, keyFactory, null);
    }

    public PillBar(Function<T, String> labelFactory, Function<T, ?> keyFactory, Function<T, Node> graphicFactory) {
        this(labelFactory, keyFactory, graphicFactory, labelFactory);
    }

    public PillBar(
            Function<T, String> labelFactory,
            Function<T, ?> keyFactory,
            Function<T, Node> graphicFactory,
            Function<T, String> compactLabelFactory
    ) {
        this.labelFactory = labelFactory;
        this.compactLabelFactory = compactLabelFactory == null ? labelFactory : compactLabelFactory;
        this.keyFactory = keyFactory;
        this.graphicFactory = graphicFactory;
        getStyleClass().add("uiptv-pill-bar");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(content);
        UiRenderQuality.optimizeLayout(compactDropdown);
        setFocusTraversable(false);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        applyFixedHeight(scaledLength(MIN_SINGLE_ROW_HEIGHT));

        Rectangle clip = new Rectangle();
        clip.setX(-6);
        clip.setY(-6);
        clip.widthProperty().bind(widthProperty().add(12));
        clip.heightProperty().bind(heightProperty().add(12));
        setClip(clip);

        content.getStyleClass().add("uiptv-pill-bar-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setHgap(5);
        content.setVgap(5);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setMaxHeight(Double.MAX_VALUE);
        content.setPrefWrapLength(4096);

        compactDropdown.getStyleClass().add("uiptv-pill-bar-dropdown");
        compactDropdown.setAlignment(Pos.CENTER_LEFT);
        compactDropdown.setContentDisplay(ContentDisplay.LEFT);
        applyCompactDropdownMetrics();
        compactDropdown.setMaxWidth(Double.MAX_VALUE);
        compactDropdown.setTextOverrun(OverrunStyle.ELLIPSIS);
        compactDropdown.setVisible(false);
        compactDropdown.setManaged(false);
        compactDropdown.fontProperty().addListener((_, _, _) -> handleCompactDropdownFontChanged());

        getChildren().addAll(content, compactDropdown);
        StackPane.setAlignment(content, Pos.CENTER_LEFT);
        StackPane.setAlignment(compactDropdown, Pos.CENTER_LEFT);
        widthProperty().addListener((_, _, width) -> {
            updateContentWrapLength(width.doubleValue());
            if (syncReservedHeight(width.doubleValue())) {
                requestAncestorLayout();
            } else {
                requestLayout();
            }
        });

        toggleGroup.selectedToggleProperty().addListener((_, oldValue, newValue) -> {
            if (rebuilding) {
                return;
            }
            if (newValue == null && oldValue != null) {
                oldValue.setSelected(true);
                return;
            }
            selectedItem.set(newValue == null ? null : itemFromToggle(newValue));
            syncCompactSelection(itemFromToggle(newValue));
            if (syncReservedHeight(getWidth())) {
                requestAncestorLayout();
            }
        });

        compactToggleGroup.selectedToggleProperty().addListener((_, oldValue, newValue) -> {
            if (rebuilding || syncingCompactSelection) {
                return;
            }
            if (newValue == null && oldValue != null) {
                oldValue.setSelected(true);
                return;
            }
            setSelectedItem(itemFromToggle(newValue));
        });
    }

    public ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

    public T getSelectedItem() {
        return selectedItem.get();
    }

    public void setSelectedItem(T item) {
        Toggle toggle = findToggleByKey(keyOf(item));
        if (toggle != null) {
            toggleGroup.selectToggle(toggle);
            if (syncReservedHeight(getWidth())) {
                requestAncestorLayout();
            }
        }
    }

    public void setReservedRowCount(int rowCount) {
        if (rowCount < 1) {
            throw new IllegalArgumentException("Reserved row count must be at least 1");
        }
        if (reservedRowCount == rowCount) {
            return;
        }
        reservedRowCount = rowCount;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void clearReservedRowCount() {
        if (reservedRowCount == 0) {
            return;
        }
        reservedRowCount = 0;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void setNarrowReservedRowCount(int rowCount) {
        if (rowCount < 1) {
            throw new IllegalArgumentException("Narrow reserved row count must be at least 1");
        }
        if (narrowReservedRowCount == rowCount) {
            return;
        }
        narrowReservedRowCount = rowCount;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void clearNarrowReservedRowCount() {
        if (narrowReservedRowCount == 0) {
            return;
        }
        narrowReservedRowCount = 0;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void setNarrowItemsPerRow(int itemsPerRow) {
        if (itemsPerRow < 1) {
            throw new IllegalArgumentException("Narrow items per row must be at least 1");
        }
        if (narrowItemsPerRow == itemsPerRow) {
            return;
        }
        narrowItemsPerRow = itemsPerRow;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void clearNarrowItemsPerRow() {
        if (narrowItemsPerRow == 0) {
            return;
        }
        narrowItemsPerRow = 0;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void setItems(List<T> items) {
        T previousSelection = selectedItem.get();
        Object previousKey = keyOf(previousSelection);
        List<T> safeItems = items == null ? List.of() : List.copyOf(items);

        rebuilding = true;
        try {
            content.getChildren().clear();
            toggleGroup.getToggles().clear();
            compactDropdown.getItems().clear();
            compactToggleGroup.getToggles().clear();
            for (T item : safeItems) {
                ToggleButton pill = createPill(item);
                content.getChildren().add(pill);
                compactDropdown.getItems().add(createCompactMenuItem(item));
            }
        } finally {
            rebuilding = false;
        }

        Toggle restored = findToggleByKey(previousKey);
        if (restored == null && !toggleGroup.getToggles().isEmpty()) {
            restored = toggleGroup.getToggles().getFirst();
        }
        toggleGroup.selectToggle(restored);
        selectedItem.set(restored == null ? null : itemFromToggle(restored));
        syncCompactSelection(selectedItem.get());
        updateContentWrapLength(getWidth());
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    public void setCompactDropdownEnabled(boolean compactDropdownEnabled) {
        if (this.compactDropdownEnabled == compactDropdownEnabled) {
            return;
        }
        this.compactDropdownEnabled = compactDropdownEnabled;
        resetAppliedRowCount();
        syncReservedHeight(getWidth());
        requestAncestorLayout();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinHeight(double width) {
        boolean compactDropdownVisible = shouldUseCompactDropdown(width);
        return heightForMode(compactDropdownVisible, rowCountForWidth(width));
    }

    @Override
    protected double computePrefHeight(double width) {
        boolean compactDropdownVisible = shouldUseCompactDropdown(width);
        return heightForMode(compactDropdownVisible, rowCountForWidth(width));
    }

    @Override
    protected void layoutChildren() {
        updateContentWrapLength(getWidth());
        if (compactMode && compactDropdown.isManaged()) {
            compactDropdown.resizeRelocate(
                    snappedLeftInset(),
                    snappedTopInset(),
                    Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset()),
                    Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset())
            );
            return;
        }
        super.layoutChildren();
    }

    private void updateContentWrapLength(double width) {
        content.setPrefWrapLength(effectiveContentWidth(width));
    }

    private boolean syncReservedHeight(double width) {
        boolean useCompactDropdown = shouldUseCompactDropdown(width);
        syncCompactMode(useCompactDropdown);
        boolean narrowMode = isNarrowMode(width);
        int rowCount = useCompactDropdown ? 1 : rowCountForWidth(width);
        if (appliedRowCount == 0 || appliedNarrowMode != narrowMode) {
            appliedRowCount = rowCount;
            appliedNarrowMode = narrowMode;
        } else if (useCompactDropdown) {
            appliedRowCount = 1;
        } else if (narrowMode) {
            appliedRowCount = Math.max(appliedRowCount, rowCount);
        } else {
            appliedRowCount = rowCount;
        }
        return applyFixedHeight(heightForMode(useCompactDropdown, appliedRowCount));
    }

    private boolean shouldUseCompactDropdown(double width) {
        return compactDropdownEnabled && rowCountForWidth(width) > 1;
    }

    private void syncCompactMode(boolean useCompactDropdown) {
        if (compactMode == useCompactDropdown) {
            return;
        }
        compactMode = useCompactDropdown;
        content.setVisible(!useCompactDropdown);
        content.setManaged(!useCompactDropdown);
        compactDropdown.setVisible(useCompactDropdown);
        compactDropdown.setManaged(useCompactDropdown);
        setMinWidth(useCompactDropdown ? scaledLength(COMPACT_DROPDOWN_MIN_WIDTH) : 0);
        setPrefWidth(Region.USE_COMPUTED_SIZE);
        updateStyleClass(this, COMPACT_STYLE_CLASS, useCompactDropdown);
    }

    private boolean applyFixedHeight(double height) {
        if (Math.abs(appliedFixedHeight - height) <= 0.5) {
            return false;
        }
        appliedFixedHeight = height;
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(height);
        return true;
    }

    private int rowCountForWidth(double width) {
        if (reservedRowCount > 0) {
            return reservedRowCount;
        }
        if (content.getChildren().isEmpty()) {
            return 1;
        }
        boolean narrowMode = isNarrowMode(width);
        double contentWidth = effectiveContentWidth(width);
        if (narrowMode && narrowReservedRowCount > 0) {
            return Math.max(narrowReservedRowCount, measuredRowCount(contentWidth));
        }
        if (narrowMode && narrowItemsPerRow > 0) {
            return Math.max(narrowRowCount(), measuredRowCount(contentWidth));
        }
        if (singleLineContentWidth() + SINGLE_ROW_HEADROOM <= contentWidth) {
            return 1;
        }
        return Math.max(1, measuredRowCount(contentWidth));
    }

    private boolean isNarrowMode(double width) {
        if (narrowReservedRowCount <= 0 && narrowItemsPerRow <= 0) {
            return false;
        }
        double measuredWidth = width > 0 ? width : getWidth();
        return measuredWidth <= 0 || measuredWidth < NARROW_SIDE_PANE_WIDTH_THRESHOLD;
    }

    private int measuredRowCount(double contentWidth) {
        if (content.getChildren().isEmpty()) {
            return 1;
        }
        double availableWidth = Math.max(1, contentWidth);
        double lineWidth = 0;
        int rows = 1;
        for (Node child : content.getChildren()) {
            double childWidth = boundedPrefWidth(child);
            double nextWidth = lineWidth <= 0 ? childWidth : lineWidth + content.getHgap() + childWidth;
            if (lineWidth > 0 && nextWidth > availableWidth) {
                rows++;
                lineWidth = childWidth;
            } else {
                lineWidth = nextWidth;
            }
        }
        return rows;
    }

    private double singleLineContentWidth() {
        double width = 0;
        for (Node child : content.getChildren()) {
            if (width > 0) {
                width += content.getHgap();
            }
            width += boundedPrefWidth(child);
        }
        return width;
    }

    private int narrowRowCount() {
        return (int) Math.ceil((double) content.getChildren().size() / narrowItemsPerRow);
    }

    private double heightForRows(int rowCount) {
        int safeRowCount = Math.max(1, rowCount);
        return scaledLength(MIN_SINGLE_ROW_HEIGHT)
                + Math.max(0, safeRowCount - 1) * scaledLength(MIN_WRAPPED_ROW_INCREMENT);
    }

    private double heightForMode(boolean useCompactDropdown, int rowCount) {
        return useCompactDropdown ? scaledLength(COMPACT_DROPDOWN_HEIGHT) : heightForRows(rowCount);
    }

    private void handleCompactDropdownFontChanged() {
        applyCompactDropdownMetrics();
        if (compactMode) {
            setMinWidth(scaledLength(COMPACT_DROPDOWN_MIN_WIDTH));
        }
        if (syncReservedHeight(getWidth())) {
            requestAncestorLayout();
        } else {
            requestLayout();
        }
    }

    private void applyCompactDropdownMetrics() {
        double compactHeight = scaledLength(COMPACT_DROPDOWN_HEIGHT);
        compactDropdown.setMinHeight(compactHeight);
        compactDropdown.setPrefHeight(compactHeight);
        compactDropdown.setMaxHeight(compactHeight);
        compactDropdown.setMinWidth(scaledLength(COMPACT_DROPDOWN_MIN_WIDTH));
        compactDropdown.setPrefWidth(scaledLength(COMPACT_DROPDOWN_PREF_WIDTH));
        compactDropdown.setMaxWidth(Double.MAX_VALUE);
    }

    private double scaledLength(double baseLength) {
        return Math.max(1, baseLength * currentFontScale());
    }

    private double currentFontScale() {
        double fontSize = compactDropdown.getFont() == null ? BASE_FONT_SIZE : compactDropdown.getFont().getSize();
        if (!Double.isFinite(fontSize) || fontSize <= 0) {
            return 1.0;
        }
        return fontSize / BASE_FONT_SIZE;
    }

    private void resetAppliedRowCount() {
        appliedRowCount = 0;
        appliedNarrowMode = false;
    }

    private double boundedPrefWidth(Node node) {
        double width = Math.max(0, node.prefWidth(-1));
        if (node instanceof Region region) {
            double maxWidth = region.getMaxWidth();
            if (maxWidth > 0 && maxWidth < Double.MAX_VALUE) {
                width = Math.min(width, maxWidth);
            }
        }
        return width;
    }

    private double effectiveContentWidth(double width) {
        double measuredWidth = width > 0 ? width : getWidth();
        double contentWidth = measuredWidth > 0
                ? measuredWidth - snappedLeftInset() - snappedRightInset()
                : content.getPrefWrapLength();
        return Math.max(1, contentWidth - SCROLLBAR_WIDTH_GUTTER);
    }

    private void requestAncestorLayout() {
        requestLayout();
        Parent parent = getParent();
        while (parent != null) {
            parent.requestLayout();
            parent = parent.getParent();
        }
    }

    private ToggleButton createPill(T item) {
        ToggleButton pill = new ToggleButton(labelFactory.apply(item));
        pill.getStyleClass().add("uiptv-pill");
        UiRenderQuality.optimizeTextNode(pill);
        if (graphicFactory != null) {
            pill.setGraphic(graphicFactory.apply(item));
        }
        pill.setToggleGroup(toggleGroup);
        pill.setUserData(item);
        pill.setMinWidth(Region.USE_PREF_SIZE);
        pill.setMaxWidth(220);
        pill.setTextOverrun(OverrunStyle.ELLIPSIS);
        pill.addEventHandler(KeyEvent.KEY_PRESSED, event -> handlePillKeyPressed(pill, event));
        return pill;
    }

    private RadioMenuItem createCompactMenuItem(T item) {
        RadioMenuItem menuItem = new RadioMenuItem(labelFactory.apply(item));
        menuItem.setToggleGroup(compactToggleGroup);
        menuItem.setUserData(item);
        if (graphicFactory != null) {
            menuItem.setGraphic(graphicFactory.apply(item));
        }
        return menuItem;
    }

    private void syncCompactSelection(T item) {
        updateCompactDropdownButton(item);
        syncingCompactSelection = true;
        try {
            compactToggleGroup.selectToggle(findToggleByKey(compactToggleGroup, keyOf(item)));
        } finally {
            syncingCompactSelection = false;
        }
    }

    private void updateCompactDropdownButton(T item) {
        if (item == null) {
            compactDropdown.setText("");
            compactDropdown.setGraphic(null);
            return;
        }
        compactDropdown.setText(compactLabelFactory.apply(item));
        compactDropdown.setGraphic(graphicFactory == null ? null : graphicFactory.apply(item));
    }

    private void updateStyleClass(Node node, String styleClass, boolean enabled) {
        boolean hasStyleClass = node.getStyleClass().contains(styleClass);
        if (enabled && !hasStyleClass) {
            node.getStyleClass().add(styleClass);
        } else if (!enabled && hasStyleClass) {
            node.getStyleClass().remove(styleClass);
        }
    }

    private void handlePillKeyPressed(ToggleButton pill, KeyEvent event) {
        if (event.getCode() != KeyCode.LEFT
                && event.getCode() != KeyCode.RIGHT
                && event.getCode() != KeyCode.UP
                && event.getCode() != KeyCode.DOWN) {
            return;
        }
        int currentIndex = content.getChildren().indexOf(pill);
        if (currentIndex < 0) {
            return;
        }
        int direction = event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.UP ? -1 : 1;
        int nextIndex = Math.max(0, Math.min(content.getChildren().size() - 1, currentIndex + direction));
        if (nextIndex == currentIndex || !(content.getChildren().get(nextIndex) instanceof ToggleButton nextPill)) {
            return;
        }
        toggleGroup.selectToggle(nextPill);
        nextPill.requestFocus();
        event.consume();
    }

    private Toggle findToggleByKey(Object key) {
        return findToggleByKey(toggleGroup, key);
    }

    private Toggle findToggleByKey(ToggleGroup group, Object key) {
        return group.getToggles().stream()
                .filter(toggle -> Objects.equals(keyOf(itemFromToggle(toggle)), key))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private T itemFromToggle(Toggle toggle) {
        return toggle == null ? null : (T) toggle.getUserData();
    }

    private Object keyOf(T item) {
        return item == null ? null : keyFactory.apply(item);
    }
}
