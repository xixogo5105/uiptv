package com.uiptv.widget;
import com.uiptv.ui.util.*;

import com.uiptv.ui.AccountListUI;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.AccountType.getAccountTypeByDisplay;

public class SearchableFilterableTableView extends TableView<AccountListUI.AccountItem> {
    private static final String COMMON_ALL = "commonAll";
    private final UIptvText textField = new UIptvText("search" + new Date().getTime(), "commonSearch", 10);
    private final MenuButton menuButton = new MenuButton(I18n.tr(COMMON_ALL));
    private final List<CheckMenuItem> typeCheckMenuItems = new ArrayList<>();
    private final CheckMenuItem allMenuItem;
    private boolean isUpdating = false;

    public SearchableFilterableTableView() {
        this.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        textField.setOnMousePressed(event -> textField.clear());
        menuButton.setPrefWidth(175);
        textField.setPrefWidth(275);

        allMenuItem = new CheckMenuItem(I18n.tr(COMMON_ALL));
        allMenuItem.setSelected(true);
        allMenuItem.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isUpdating) return;
            if (Boolean.TRUE.equals(isSelected)) {
                isUpdating = true;
                typeCheckMenuItems.forEach(item -> item.setSelected(false));
                isUpdating = false;
            }
            updateMenuButtonText();
        });

        menuButton.getItems().add(allMenuItem);
        menuButton.getItems().add(new SeparatorMenuItem());

        for (AccountType type : AccountType.values()) {
            CheckMenuItem item = new CheckMenuItem(type.getDisplay());
            item.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isUpdating) return;
                if (Boolean.TRUE.equals(isSelected)) {
                    isUpdating = true;
                    allMenuItem.setSelected(false);
                    isUpdating = false;
                }
                updateMenuButtonText();
            });
            menuButton.getItems().add(item);
            typeCheckMenuItems.add(item);
        }
    }

    private void updateMenuButtonText() {
        if (allMenuItem.isSelected()) {
            menuButton.setText(I18n.tr(COMMON_ALL));
            return;
        }

        String selectedItemsText = typeCheckMenuItems.stream()
                .filter(CheckMenuItem::isSelected)
                .map(MenuItem::getText)
                .collect(Collectors.joining(", "));

        if (selectedItemsText.isEmpty()) {
            isUpdating = true;
            allMenuItem.setSelected(true);
            isUpdating = false;
            menuButton.setText(I18n.tr(COMMON_ALL));
        } else {
            menuButton.setText(selectedItemsText);
        }
    }

    public TextField getTextField() {
        return textField;
    }

    public MenuButton getMenuButton() {
        return menuButton;
    }

    public void filterByAccountType() {
        FilteredList<AccountListUI.AccountItem> filteredItems = new FilteredList<>(FXCollections.observableList(getItems()));
        setItems(filteredItems);

        List<Observable> dependencies = new ArrayList<>();
        dependencies.add(textField.textProperty());
        dependencies.add(allMenuItem.selectedProperty());
        typeCheckMenuItems.forEach(item -> dependencies.add(item.selectedProperty()));

        filteredItems.predicateProperty().bind(Bindings.createObjectBinding(() -> {
            String searchText = textField.getText() == null ? "" : textField.getText().toLowerCase();
            List<String> selectedTypes = typeCheckMenuItems.stream()
                    .filter(CheckMenuItem::isSelected)
                    .map(item -> getAccountTypeByDisplay(item.getText()).name())
                    .toList();

            return accountItem -> {
                boolean matchesText = accountItem.getAccountName().toLowerCase().contains(searchText);
                boolean matchesType = allMenuItem.isSelected() || selectedTypes.contains(accountItem.getAccountType());
                return matchesText && matchesType;
            };
        }, dependencies.toArray(Observable[]::new)));
    }
}
