package com.uiptv.widget;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class PillBar<T> extends StackPane {
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
        setMinHeight(30);
        setMaxHeight(Region.USE_PREF_SIZE);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
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
        widthProperty().addListener((_, _, width) ->
                content.setPrefWrapLength(Math.max(1, width.doubleValue() - 4)));

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
