package com.uiptv.widget;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;

public class SearchableTableViewWithButton extends VBox {
    private final TextField searchTextField = new TextField();
    private final Button manageCategoriesButton = new Button("Add");
    private final TableView tableView = new TableView();

    public SearchableTableViewWithButton() {
        searchTextField.setPromptText("Search");
        searchTextField.setPrefWidth((double) (GUIDED_MAX_WIDTH_PIXELS / 3) * 0.85);
        searchTextField.setOnMousePressed(event -> searchTextField.clear());


        manageCategoriesButton.setPrefWidth((double) (GUIDED_MAX_WIDTH_PIXELS / 3) * 0.15);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        HBox hBox = new HBox(5, searchTextField, manageCategoriesButton);
        hBox.setMaxHeight(25);

        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.getChildren().addAll(hBox, tableView);
        VBox.setVgrow(this, Priority.ALWAYS);
    }

    public TextField getSearchTextField() {
        return searchTextField;
    }

    public Button getManageCategoriesButton() {
        return manageCategoriesButton;
    }

    public TableView getTableView() {
        return tableView;
    }

    public <T> void addTextFilter() {
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