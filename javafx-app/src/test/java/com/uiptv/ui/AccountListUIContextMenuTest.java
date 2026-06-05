package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
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

        assertFalse(menuItem(menu, "autoReloadCache").isVisible());
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

    private static MenuItem menuItem(ContextMenu menu, String i18nKey) {
        String expectedText = I18n.tr(i18nKey);
        return menu.getItems().stream()
                .filter(item -> expectedText.equals(item.getText()))
                .findFirst()
                .orElseThrow();
    }
}
