package com.uiptv.widget;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.Date;
import java.util.List;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;

public class SearchableTableView extends TableView {
    private final UIptvText searchTextField = new UIptvText("search" + new Date().getTime(), "Search", 10);

    public SearchableTableView() {
        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchTextField.setOnMousePressed(event -> searchTextField.clear());
    }

    public TextField getSearchTextField() {
        return searchTextField;
    }

    public <T> void addTextFilter() {
        TextField filterField = getSearchTextField();
        final List<TableColumn<T, ?>> columns = getColumns();
        FilteredList<T> filteredData = new FilteredList<>(getItems());
        filteredData.predicateProperty().bind(Bindings.createObjectBinding(() -> {
            String text = filterField.getText();
            if (isBlank(text)) {
                return null;
            }
            final String filterText = text.toLowerCase();
            return o -> {
                for (TableColumn<T, ?> col : columns) {
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

        SortedList<T> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(comparatorProperty());
        setItems(sortedData);
    }
}
