package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout guarantees for account cards in the two-pane account browser.
 *
 * <p>When the account list shares horizontal space with the category/channel browser it must stay
 * single column: a second column renders at roughly half the pane width and spills over the
 * browser pane on the right, which reads as one card sitting on top of another.
 */
class AccountListUICardLayoutTest extends DbBackedUiTest {

    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void accountGridIsSingleColumn_whenAccountBrowserIsShownSideBySide() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(null, null));

        runOnFxThread(() -> {
            setBoolean(ui, "twoPaneAccountBrowser", true);
            setBoolean(ui, "accountBrowserCompact", true);
            invoke(ui, "applyAccountGridDisplayMode", new Class<?>[]{boolean.class}, true);
            return null;
        });

        assertTrue(isSingleColumn(ui),
                "The account grid must use one column when the browser pane sits beside it");
    }

    @Test
    void accountGridStaysMultiColumn_whenNoBrowserPaneIsBesideIt() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(null, null));

        runOnFxThread(() -> {
            setBoolean(ui, "twoPaneAccountBrowser", false);
            setBoolean(ui, "mediaDrawerMode", false);
            setBoolean(ui, "accountBrowserCompact", false);
            invoke(ui, "applyAccountGridDisplayMode", new Class<?>[]{boolean.class}, true);
            return null;
        });

        assertFalse(isSingleColumn(ui),
                "The standalone account browser should keep its responsive multi-column layout");
    }

    @Test
    void accountGridIsSingleColumn_inMediaDrawerMode() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(null, null));

        runOnFxThread(() -> {
            setBoolean(ui, "mediaDrawerMode", true);
            setBoolean(ui, "twoPaneAccountBrowser", false);
            invoke(ui, "applyAccountGridDisplayMode", new Class<?>[]{boolean.class}, true);
            return null;
        });

        assertTrue(isSingleColumn(ui), "Media drawer mode pins one card per row");
    }

    @Test
    void plainTextAccountCardShowsPinIcon_forPinnedAccount() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(null, null));
        AccountListUI.AccountItem pinned = accountItem(saveAccount("Pinned", true));

        Pane card = runOnFxThread(() -> (Pane) invoke(ui, "createPlainTextAccountCard",
                new Class<?>[]{AccountListUI.AccountItem.class}, pinned));

        assertTrue(runOnFxThread(() -> hasStyleClass(card, "account-card-pin-icon")),
                "Pinned accounts must show the pin marker in plain-text mode too");
    }

    @Test
    void plainTextAccountCardHasNoPinIcon_forUnpinnedAccount() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(null, null));
        AccountListUI.AccountItem unpinned = accountItem(saveAccount("Unpinned", false));

        Pane card = runOnFxThread(() -> (Pane) invoke(ui, "createPlainTextAccountCard",
                new Class<?>[]{AccountListUI.AccountItem.class}, unpinned));

        assertFalse(runOnFxThread(() -> hasStyleClass(card, "account-card-pin-icon")),
                "Unpinned accounts must not render a pin marker");
    }

    @Test
    void everyAccountCardStyleDefinesAFloorWidth() throws Exception {
        AccountListUI ui = runOnFxThread(() -> new AccountListUI(null, null));
        AccountListUI.AccountItem item = accountItem(saveAccount("Floored", false));

        runOnFxThread(() -> {
            for (String factory : List.of("createAccountCard", "createCompactAccountCard", "createPlainTextAccountCard")) {
                Region card = (Region) invoke(ui, factory, new Class<?>[]{AccountListUI.AccountItem.class}, item);
                assertTrue(card.getMinWidth() > 0,
                        factory + " must define a minimum width so a card cannot collapse");
                assertTrue(card.getPrefWidth() > 0,
                        factory + " must define a preferred width so a card is not content-sized");
            }
            return null;
        });
    }

    private static Account saveAccount(String name, boolean pinToTop) {
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
                AccountType.M3U8_LOCAL,
                null,
                null,
                false
        );
        account.setPinToTop(pinToTop);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
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

    private boolean hasStyleClass(Pane card, String styleClass) {
        return card.getChildren().stream()
                .filter(Node.class::isInstance)
                .map(Node.class::cast)
                .anyMatch(child -> child.getStyleClass().contains(styleClass));
    }

    private boolean isSingleColumn(AccountListUI ui) {
        try {
            return readBoolean(accountGrid(ui), "singleColumn");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponsiveCardGrid<AccountListUI.AccountItem> accountGrid(AccountListUI ui)
            throws ReflectiveOperationException {
        Field field = AccountListUI.class.getDeclaredField("accountGrid");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ResponsiveCardGrid<AccountListUI.AccountItem> grid =
                (ResponsiveCardGrid<AccountListUI.AccountItem>) field.get(ui);
        return grid;
    }

    private static void setBoolean(AccountListUI ui, String field, boolean value) throws Exception {
        Field target = AccountListUI.class.getDeclaredField(field);
        target.setAccessible(true);
        target.set(ui, value);
    }

    private static boolean readBoolean(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (boolean) field.get(target);
    }

    private static Object invoke(AccountListUI ui, String name, Class<?>[] types, Object... args) {
        try {
            Method method = AccountListUI.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(ui, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
