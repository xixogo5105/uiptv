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
    private final UIptvText textField = new UIptvText("search" + new Date().getTime(), "Search", 10);
    private final ComboBox comboBox = new ComboBox();

    public SearchableFilterableTableView() {
        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        textField.setOnMousePressed(event -> textField.clear());
        comboBox.setPrefWidth(175);
        textField.setPrefWidth(275);
        List<String> list = Arrays.stream(AccountType.values()).map(AccountType::getDisplay).toList();
        comboBox.getItems().add(ALL);
        comboBox.getItems().addAll(list);
        comboBox.setValue(ALL);

    }

    public TextField getTextField() {
        return textField;
    }

    public ComboBox getComboBox() {
        return comboBox;
    }

    public <T> void filterByAccountType() {


        ObjectProperty<Predicate<AccountListUI.AccountItem>> textFilter = new SimpleObjectProperty<>();
        ObjectProperty<Predicate<AccountListUI.AccountItem>> comboFilter = new SimpleObjectProperty<>();

        textFilter.bind(Bindings.createObjectBinding(() -> accountItem -> accountItem.getAccountName().toLowerCase().contains(textField.getText().toLowerCase()),
                textField.textProperty()));


        comboFilter.bind(Bindings.createObjectBinding(() -> accountItem -> comboBox.getValue().toString().equalsIgnoreCase(ALL) || accountItem.getAccountType().equalsIgnoreCase(getAccountTypeByDisplay(comboBox.getValue().toString()).name()),
                comboBox.valueProperty()));

        FilteredList<AccountListUI.AccountItem> filteredItems = new FilteredList<>(FXCollections.observableList(getItems()));
        setItems(filteredItems);

        filteredItems.predicateProperty().bind(Bindings.createObjectBinding(
                () -> textFilter.get().and(comboFilter.get()),
                textFilter, comboFilter));
    }
}
