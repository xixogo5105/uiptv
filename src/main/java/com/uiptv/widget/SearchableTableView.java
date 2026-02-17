package com.uiptv.widget;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Callback;

import java.util.Date;
import java.util.List;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;

public class SearchableTableView<S> extends TableView<S> {
    private final UIptvText searchTextField = new UIptvText("search" + new Date().getTime(), "Search", 10);
    private final ObjectProperty<S> playingItem = new SimpleObjectProperty<>();
    private boolean isInternalUpdate = false;

    public SearchableTableView() {
        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchTextField.setOnMousePressed(event -> searchTextField.clear());
        
        // Listen to rowFactory changes to wrap any user-provided factory
        this.rowFactoryProperty().addListener((obs, oldFactory, newFactory) -> {
            if (isInternalUpdate) return;
            
            isInternalUpdate = true;
            try {
                this.setRowFactory(tv -> {
                    TableRow<S> row;
                    if (newFactory != null) {
                        row = newFactory.call(tv);
                    } else {
                        row = new TableRow<>();
                    }
                    
                    // Add listener to update style when item changes
                    row.itemProperty().addListener((o, oldItem, newItem) -> updateRowStyle(row));
                    
                    // Add listener to update style when playingItem changes
                    playingItem.addListener((o, oldVal, newVal) -> updateRowStyle(row));
                    
                    // Initial update
                    updateRowStyle(row);
                    
                    return row;
                });
            } finally {
                isInternalUpdate = false;
            }
        });
        
        // Set initial factory to trigger the listener logic
        this.setRowFactory(tv -> new TableRow<>());
    }
    
    private void updateRowStyle(TableRow<S> row) {
        if (row.getItem() != null && row.getItem().equals(playingItem.get())) {
            if (!row.getStyleClass().contains("playing")) {
                row.getStyleClass().add("playing");
            }
        } else {
            row.getStyleClass().remove("playing");
        }
    }

    public TextField getSearchTextField() {
        return searchTextField;
    }
    
    public void setPlayingItem(S item) {
        this.playingItem.set(item);
    }
    
    public S getPlayingItem() {
        return playingItem.get();
    }
    
    public ObjectProperty<S> playingItemProperty() {
        return playingItem;
    }

    public void addTextFilter() {
        TextField filterField = getSearchTextField();
        final List<TableColumn<S, ?>> columns = getColumns();
        FilteredList<S> filteredData = new FilteredList<>(getItems());
        filteredData.predicateProperty().bind(Bindings.createObjectBinding(() -> {
            String text = filterField.getText();
            if (isBlank(text)) {
                return null;
            }
            final String filterText = text.toLowerCase();
            return o -> {
                for (TableColumn<S, ?> col : columns) {
                    ObservableValue<?> observable = col.getCellObservableValue(o);
                    if (observable != null) {
                        Object value = observable.getValue();
                        if (value != null && value.toString().toLowerCase().contains(filterText)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }, filterField.textProperty()));

        SortedList<S> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(comparatorProperty());
        setItems(sortedData);
    }
}
