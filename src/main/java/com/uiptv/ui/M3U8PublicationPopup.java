package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.util.AccountType;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class M3U8PublicationPopup extends VBox {

    public M3U8PublicationPopup(Stage stage) {
        setPadding(new Insets(10));
        setSpacing(10);

        Label label = new Label("Select M3U8 accounts to publish:");
        ListView<CheckBox> listView = new ListView<>();

        List<Account> accounts = AccountService.getInstance().getAll().values().stream()
                .filter(a -> a.getType() == AccountType.M3U8_LOCAL || a.getType() == AccountType.M3U8_URL)
                .collect(Collectors.toList());

        Set<String> previouslySelected = M3U8PublicationService.getInstance().getSelectedAccountIds();

        for (Account account : accounts) {
            CheckBox checkBox = new CheckBox(account.getAccountName());
            checkBox.setUserData(account.getDbId());
            if (previouslySelected.contains(account.getDbId())) {
                checkBox.setSelected(true);
            }
            listView.getItems().add(checkBox);
        }

        Button okButton = new Button("OK");
        okButton.setOnAction(e -> {
            Set<String> selectedIds = new HashSet<>();
            for (CheckBox checkBox : listView.getItems()) {
                if (checkBox.isSelected()) {
                    selectedIds.add((String) checkBox.getUserData());
                }
            }
            M3U8PublicationService.getInstance().setSelectedAccountIds(selectedIds);
            stage.close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, okButton, cancelButton);

        getChildren().addAll(label, listView, buttons);
    }
}