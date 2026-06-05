package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountListUIContextMenuTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void cacheCapableSavedAccountShowsReloadCacheAction() throws Exception {
        Account saved = saveAccount("Xtreme", AccountType.XTREME_API);
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(false, null, null));
        AccountListUI.AccountItem item = accountItem(saved);

        ContextMenu menu = runOnFxThread(() -> createContextMenu(ui, item, List.of(item), new Label("owner")));

        assertTrue(menuItem(menu, "autoReloadCache").isVisible());
        assertTrue(menuItem(menu, "autoVod").isVisible());
        assertTrue(menuItem(menu, "autoSeries").isVisible());
    }

    @Test
    void m3u8AccountShowsReloadCacheButHidesVodAndSeriesActions() throws Exception {
        Account saved = saveAccount("Playlist", AccountType.M3U8_LOCAL);
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(false, null, null));
        AccountListUI.AccountItem item = accountItem(saved);

        ContextMenu menu = runOnFxThread(() -> createContextMenu(ui, item, List.of(item), new Label("owner")));

        assertTrue(menuItem(menu, "autoReloadCache").isVisible());
        assertFalse(menuItem(menu, "autoVod").isVisible());
        assertFalse(menuItem(menu, "autoSeries").isVisible());
    }

    @Test
    void staleAccountIdHidesReloadCacheAction() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(false, null, null));
        AccountListUI.AccountItem missing = new AccountListUI.AccountItem(
                new SimpleStringProperty("Missing"),
                new SimpleStringProperty("999999"),
                new SimpleStringProperty(AccountType.XTREME_API.getDisplay()),
                false,
                0,
                0,
                0
        );

        ContextMenu menu = runOnFxThread(() -> createContextMenu(ui, missing, List.of(missing), new Label("owner")));

        assertFalse(hasMenuItem(menu, "autoReloadCache"));
    }

    @Test
    void multiSelectedCacheCapableAccountsResolveForReloadAction() throws Exception {
        Account first = saveAccount("Cache Multi One", AccountType.XTREME_API);
        Account second = saveAccount("Cache Multi Two", AccountType.M3U8_LOCAL);
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(false, null, null));
        AccountListUI.AccountItem firstItem = accountItem(first);
        AccountListUI.AccountItem secondItem = accountItem(second);
        List<AccountListUI.AccountItem> selectedItems = List.of(firstItem, secondItem);

        ContextMenu menu = runOnFxThread(() -> createContextMenu(ui, firstItem, selectedItems, new Label("owner")));
        List<Account> resolvedAccounts = runOnFxThread(() -> resolveAccountsForReload(ui, firstItem, selectedItems));

        assertTrue(menuItem(menu, "autoReloadCache").isVisible());
        assertEquals(
                List.of(I18n.tr("autoReloadCache"), I18n.tr("autoDeleteAccount")),
                visibleActionTexts(menu)
        );
        assertEquals(
                List.of(first.getDbId(), second.getDbId()),
                resolvedAccounts.stream().map(Account::getDbId).toList()
        );
    }

    @Test
    void accountCardMenuButtonPreservesExistingMultiSelection() throws Exception {
        Account first = saveAccount("Menu Multi One", AccountType.XTREME_API);
        Account second = saveAccount("Menu Multi Two", AccountType.M3U8_LOCAL);
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(false, null, null));
        AccountListUI.AccountItem firstItem = accountItem(first);
        AccountListUI.AccountItem secondItem = accountItem(second);

        List<AccountListUI.AccountItem> selectedItems = runOnFxThread(() -> {
            ResponsiveCardGrid<AccountListUI.AccountItem> grid = accountGrid(ui);
            grid.setItems(FXCollections.observableArrayList(firstItem, secondItem));
            grid.selectItems(List.of(firstItem, secondItem));
            return selectedAccountsForAccountMenuButton(ui, secondItem);
        });

        assertEquals(
                List.of(first.getDbId(), second.getDbId()),
                selectedItems.stream().map(AccountListUI.AccountItem::getAccountId).toList()
        );
    }

    private static Account saveAccount(String name, AccountType type) {
        Account account = new Account(
                name,
                "user",
                "pass",
                "http://example.test",
                "00:11:22:33:44:55",
                null,
                null,
                null,
                null,
                null,
                type,
                null,
                null,
                false
        );
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getAll().get(name);
    }

    private static AccountListUI.AccountItem accountItem(Account account) {
        return new AccountListUI.AccountItem(
                new SimpleStringProperty(account.getAccountName()),
                new SimpleStringProperty(account.getDbId()),
                new SimpleStringProperty(account.getType().getDisplay()),
                account.isPinToTop(),
                0,
                0,
                0
        );
    }

    private static ContextMenu createContextMenu(AccountListUI ui,
                                                 AccountListUI.AccountItem item,
                                                 List<AccountListUI.AccountItem> selectedItems,
                                                 Node owner) throws Exception {
        Method method = AccountListUI.class.getDeclaredMethod(
                "createAccountContextMenu",
                AccountListUI.AccountItem.class,
                List.class,
                Node.class
        );
        method.setAccessible(true);
        return (ContextMenu) method.invoke(ui, item, selectedItems, owner);
    }

    @SuppressWarnings("unchecked")
    private static ResponsiveCardGrid<AccountListUI.AccountItem> accountGrid(AccountListUI ui) throws Exception {
        var field = AccountListUI.class.getDeclaredField("accountGrid");
        field.setAccessible(true);
        return (ResponsiveCardGrid<AccountListUI.AccountItem>) field.get(ui);
    }

    @SuppressWarnings("unchecked")
    private static List<AccountListUI.AccountItem> selectedAccountsForAccountMenuButton(AccountListUI ui,
                                                                                       AccountListUI.AccountItem item) throws Exception {
        Method method = AccountListUI.class.getDeclaredMethod(
                "selectedAccountsForAccountMenuButton",
                AccountListUI.AccountItem.class
        );
        method.setAccessible(true);
        return (List<AccountListUI.AccountItem>) method.invoke(ui, item);
    }

    @SuppressWarnings("unchecked")
    private static List<Account> resolveAccountsForReload(AccountListUI ui,
                                                          AccountListUI.AccountItem item,
                                                          List<AccountListUI.AccountItem> selectedItems) throws Exception {
        Method method = AccountListUI.class.getDeclaredMethod(
                "resolveAccountsForReload",
                AccountListUI.AccountItem.class,
                List.class
        );
        method.setAccessible(true);
        return (List<Account>) method.invoke(ui, item, selectedItems);
    }

    private static MenuItem menuItem(ContextMenu menu, String i18nKey) {
        String expectedText = I18n.tr(i18nKey);
        return menu.getItems().stream()
                .filter(item -> expectedText.equals(item.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasMenuItem(ContextMenu menu, String i18nKey) {
        String expectedText = I18n.tr(i18nKey);
        return menu.getItems().stream()
                .anyMatch(item -> expectedText.equals(item.getText()));
    }

    private static List<String> visibleActionTexts(ContextMenu menu) {
        return menu.getItems().stream()
                .filter(MenuItem::isVisible)
                .map(MenuItem::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }
}
