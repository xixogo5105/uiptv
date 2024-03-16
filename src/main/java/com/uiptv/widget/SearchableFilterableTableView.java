package com.uiptv.widget;

import com.uiptv.ui.AccountListUI;
import com.uiptv.util.AccountType;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;

public class SearchableFilterableTableView extends TableView {
    public static final String ALL = "All";
    private final UIptvText searchTextField = new UIptvText("search" + new Date().getTime(), "Search", 10);
    private final ComboBox accountFilterBox = new ComboBox();

    public SearchableFilterableTableView() {
        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        accountFilterBox.setOnMousePressed(event -> searchTextField.clear());
        accountFilterBox.setPrefWidth(175);
        searchTextField.setPrefWidth(275);
        List<String> list = Arrays.stream(AccountType.values()).map(AccountType::getDisplay).toList();
        accountFilterBox.getItems().add(ALL);
        accountFilterBox.getItems().addAll(list);
        accountFilterBox.setValue(ALL);

    }

    public TextField getSearchTextField() {
        return searchTextField;
    }

    public ComboBox getAccountFilterBox() {
        return accountFilterBox;
    }

    public <T> void filterByAccountType() {
        ComboBox acountFilterCheckboxCombo = getAccountFilterBox();

        TextField nameFilterField = getSearchTextField();

        ObjectProperty<Predicate<AccountListUI.AccountItem>> searchNameFilter = new SimpleObjectProperty<>();
        ObjectProperty<Predicate<AccountListUI.AccountItem>> typeFilter = new SimpleObjectProperty<>();

        searchNameFilter.bind(Bindings.createObjectBinding(() -> accountItem -> accountItem.getAccountName().toLowerCase().contains(nameFilterField.getText().toLowerCase()),
                nameFilterField.textProperty()));


        typeFilter.bind(Bindings.createObjectBinding(() -> accountItem -> acountFilterCheckboxCombo.getValue().toString().equalsIgnoreCase(ALL) || accountItem.getAccountType().equalsIgnoreCase(getAccountTypeByDisplay(acountFilterCheckboxCombo.getValue().toString()).name()),
                acountFilterCheckboxCombo.valueProperty()));

        FilteredList<AccountListUI.AccountItem> filteredItems = new FilteredList<>(FXCollections.observableList(getItems()));
        setItems(filteredItems);

        filteredItems.predicateProperty().bind(Bindings.createObjectBinding(
                () -> searchNameFilter.get().and(typeFilter.get()),
                searchNameFilter, typeFilter));
    }
}
