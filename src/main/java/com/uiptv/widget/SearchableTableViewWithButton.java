package com.uiptv.widget;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;

public class SearchableTableViewWithButton<T> extends VBox { // Made generic
    private final TextField searchTextField = new TextField();
    private final Button manageCategoriesButton = new Button("Add");
    private final TableView<T> tableView = new TableView<>(); // Made generic

    public SearchableTableViewWithButton() {
        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchTextField.setPromptText("Search");
        searchTextField.setMaxWidth(Double.MAX_VALUE);
        searchTextField.setOnMousePressed(event -> searchTextField.clear());


        manageCategoriesButton.setMinWidth(Region.USE_PREF_SIZE);
        manageCategoriesButton.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        HBox hBox = new HBox(5, searchTextField, manageCategoriesButton);
        HBox.setHgrow(searchTextField, Priority.ALWAYS);
        hBox.setMaxHeight(25);

        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.getChildren().addAll(hBox, tableView);
        VBox.setVgrow(this, Priority.ALWAYS);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public TextField getSearchTextField() {
        return searchTextField;
    }

    public Button getManageCategoriesButton() {
        return manageCategoriesButton;
    }

    public TableView<T> getTableView() { // Made generic
        return tableView;
    }

    public void addTextFilter() { // Removed <T> from here, now uses class T
        TextField filterField = getSearchTextField();
        final List<TableColumn<T, ?>> columns = tableView.getColumns();
        FilteredList<T> filteredData = new FilteredList<>(tableView.getItems());
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
        sortedData.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedData);
    }
}
