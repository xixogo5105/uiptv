package com.uiptv.widget;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.OverrunStyle;
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
    private static final double DEFAULT_MIN_HEIGHT = 30;
    private static final double SCROLLBAR_WIDTH_GUTTER = 18;

    private final FlowPane content = new FlowPane();
    private final ToggleGroup toggleGroup = new ToggleGroup();
    private final Function<T, String> labelFactory;
    private final Function<T, ?> keyFactory;
    private final Function<T, Node> graphicFactory;
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private boolean rebuilding;

    public PillBar(Function<T, String> labelFactory, Function<T, ?> keyFactory) {
        this(labelFactory, keyFactory, null);
    }

    public PillBar(Function<T, String> labelFactory, Function<T, ?> keyFactory, Function<T, Node> graphicFactory) {
        this.labelFactory = labelFactory;
        this.keyFactory = keyFactory;
        this.graphicFactory = graphicFactory;
        getStyleClass().add("uiptv-pill-bar");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(content);
        setFocusTraversable(false);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setMinHeight(DEFAULT_MIN_HEIGHT);
        setMaxHeight(Region.USE_PREF_SIZE);

        Rectangle clip = new Rectangle();
        clip.setX(-6);
        clip.setY(-6);
        clip.widthProperty().bind(widthProperty().add(12));
        clip.heightProperty().bind(heightProperty().add(12));
        setClip(clip);

        content.getStyleClass().add("uiptv-pill-bar-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setHgap(4);
        content.setVgap(4);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setPrefWrapLength(4096);
        getChildren().add(content);
        StackPane.setAlignment(content, Pos.CENTER_LEFT);
        widthProperty().addListener((_, _, width) -> {
            syncWrappedHeight(width.doubleValue());
            requestAncestorLayout();
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
        }
    }

    public void setItems(List<T> items) {
        T previousSelection = selectedItem.get();
        Object previousKey = keyOf(previousSelection);
        List<T> safeItems = items == null ? List.of() : List.copyOf(items);

        rebuilding = true;
        try {
            content.getChildren().clear();
            toggleGroup.getToggles().clear();
            for (T item : safeItems) {
                ToggleButton pill = createPill(item);
                content.getChildren().add(pill);
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
        syncWrappedHeight(getWidth());
        Platform.runLater(() -> syncWrappedHeight(getWidth()));
        requestAncestorLayout();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinHeight(double width) {
        return computeWrappedHeight(width);
    }

    @Override
    protected double computePrefHeight(double width) {
        return computeWrappedHeight(width);
    }

    @Override
    protected void layoutChildren() {
        syncWrappedHeight(getWidth());
        super.layoutChildren();
    }

    private double computeWrappedHeight(double width) {
        double insets = snappedTopInset() + snappedBottomInset();
        double contentWidth = effectiveContentWidth(width);
        return Math.max(DEFAULT_MIN_HEIGHT, content.prefHeight(contentWidth) + insets);
    }

    private void updateContentWrapLength(double width) {
        content.setPrefWrapLength(effectiveContentWidth(width));
    }

    private void syncWrappedHeight(double width) {
        updateContentWrapLength(width);
        double height = computeWrappedHeight(width);
        if (Math.abs(getPrefHeight() - height) > 0.5) {
            setPrefHeight(height);
        }
        if (Math.abs(getMinHeight() - height) > 0.5) {
            setMinHeight(height);
        }
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
        return toggleGroup.getToggles().stream()
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
