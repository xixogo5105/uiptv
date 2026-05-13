package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeCredentialsJson;
import com.uiptv.widget.UIptvCombo;
import com.uiptv.widget.UIptvText;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class ManageAccountXtremeCredentialsHelper {
    private final UIptvCombo xtremeUsername;
    private final UIptvText username;
    private final UIptvText password;
    private final Hyperlink manageXtremeCredentialsLink;
    private final HBox xtremeUsernameContainer;

    private List<XtremeCredentialsJson.Entry> xtremeCredentials = new ArrayList<>();
    private String xtremeDefaultUsername;

    public ManageAccountXtremeCredentialsHelper(UIptvCombo xtremeUsername,
                                                UIptvText username,
                                                UIptvText password,
                                                Hyperlink manageXtremeCredentialsLink,
                                                HBox xtremeUsernameContainer) {
        this.xtremeUsername = xtremeUsername;
        this.username = username;
        this.password = password;
        this.manageXtremeCredentialsLink = manageXtremeCredentialsLink;
        this.xtremeUsernameContainer = xtremeUsernameContainer;
        this.xtremeUsername.valueProperty().addListener((obs, oldVal, newVal) -> handleSelection(newVal));
    }

    public void configureForType(AccountType type) {
        boolean isXtreme = type == AccountType.XTREME_API;
        password.setEditable(!isXtreme);
        xtremeUsernameContainer.setVisible(isXtreme);
        xtremeUsernameContainer.setManaged(isXtreme);
        manageXtremeCredentialsLink.setVisible(isXtreme);
        manageXtremeCredentialsLink.setManaged(isXtreme);
        if (isXtreme) {
            seedFromFieldsIfNeeded();
            refreshUsernameItems();
        }
    }

    public void openManagementPopup(Stage owner, Runnable onChangePersist) {
        XtremeCredentialsManagementPopup popup = new XtremeCredentialsManagementPopup(
                owner,
                xtremeCredentials,
                xtremeDefaultUsername,
                (newEntries, newDefault) -> {
                    xtremeCredentials = newEntries != null ? newEntries : new ArrayList<>();
                    xtremeDefaultUsername = newDefault;
                    refreshUsernameItems();
                    if (onChangePersist != null) {
                        onChangePersist.run();
                    }
                }
        );
        popup.show();
    }

    public void loadFromAccount(Account account) {
        if (account == null) {
            return;
        }
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(account.getXtremeCredentialsJson());
        if (entries.isEmpty() && isNotBlank(account.getUsername()) && isNotBlank(account.getPassword())) {
            entries = new ArrayList<>();
            entries.add(new XtremeCredentialsJson.Entry(account.getUsername(), account.getPassword(), true));
        } else {
            entries = XtremeCredentialsJson.normalize(entries, account.getUsername());
        }
        xtremeCredentials = entries;
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        xtremeDefaultUsername = defaultEntry != null ? defaultEntry.username() : account.getUsername();
        refreshUsernameItems();
    }

    public void applyToAccount(Account account) {
        if (account == null || account.getType() != AccountType.XTREME_API) {
            return;
        }
        String selectedUsername = xtremeUsername.getValue();
        String selectedPassword = password.getText();
        List<XtremeCredentialsJson.Entry> entries = new ArrayList<>(xtremeCredentials);
        if (entries.isEmpty() && isNotBlank(selectedUsername) && isNotBlank(selectedPassword)) {
            entries.add(new XtremeCredentialsJson.Entry(selectedUsername, selectedPassword, true));
        } else if (isNotBlank(selectedUsername) && isNotBlank(selectedPassword)) {
            boolean exists = entries.stream().anyMatch(entry ->
                    entry.username().equals(selectedUsername) && entry.password().equals(selectedPassword));
            if (!exists) {
                entries.add(new XtremeCredentialsJson.Entry(selectedUsername, selectedPassword, entries.isEmpty()));
            }
        }

        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(entries, isNotBlank(selectedUsername) ? selectedUsername : xtremeDefaultUsername);
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(normalized);
        if (defaultEntry != null) {
            account.setUsername(defaultEntry.username());
            account.setPassword(defaultEntry.password());
            xtremeDefaultUsername = defaultEntry.username();
        }
        account.setXtremeCredentialsJson(XtremeCredentialsJson.toJson(normalized));
        xtremeCredentials = normalized;
    }

    public void reset() {
        xtremeUsername.getItems().clear();
        xtremeUsername.setValue(null);
        xtremeCredentials = new ArrayList<>();
        xtremeDefaultUsername = null;
    }

    private void seedFromFieldsIfNeeded() {
        if (!xtremeCredentials.isEmpty()) {
            return;
        }
        String currentUsername = isNotBlank(xtremeUsername.getValue()) ? xtremeUsername.getValue() : username.getText();
        String currentPassword = password.getText();
        if (isBlank(currentUsername) || isBlank(currentPassword)) {
            return;
        }
        xtremeCredentials = new ArrayList<>();
        xtremeCredentials.add(new XtremeCredentialsJson.Entry(currentUsername, currentPassword, true));
        xtremeDefaultUsername = currentUsername;
    }

    private void refreshUsernameItems() {
        if (xtremeCredentials.isEmpty()) {
            xtremeUsername.getItems().clear();
            xtremeUsername.setValue(null);
            return;
        }
        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(xtremeCredentials, xtremeDefaultUsername);
        xtremeCredentials = normalized;
        xtremeUsername.getItems().setAll(normalized.stream().map(XtremeCredentialsJson.Entry::username).toList());

        String selection = xtremeDefaultUsername;
        if (isBlank(selection)) {
            selection = xtremeUsername.getValue();
        }
        if (isBlank(selection) && !normalized.isEmpty()) {
            selection = normalized.getFirst().username();
        }
        if (selection != null) {
            xtremeUsername.setValue(selection);
            handleSelection(selection);
        }
    }

    private void handleSelection(String usernameValue) {
        if (isBlank(usernameValue)) {
            return;
        }
        XtremeCredentialsJson.Entry entry = resolveCredentialByUsername(usernameValue);
        if (entry == null) {
            return;
        }
        xtremeDefaultUsername = entry.username();
        username.setText(entry.username());
        password.setText(entry.password());
    }

    private XtremeCredentialsJson.Entry resolveCredentialByUsername(String usernameValue) {
        if (isBlank(usernameValue)) {
            return null;
        }
        for (XtremeCredentialsJson.Entry entry : xtremeCredentials) {
            if (entry != null && usernameValue.equals(entry.username())) {
                return entry;
            }
        }
        return null;
    }
}
