package com.uiptv.ui;

import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.I18n;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountListUILayoutTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void normalAccountGridUsesWiderResponsiveCardsOnFullWidthScreens() throws Exception {
        double cardWidth = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(false, null, null);
            ResponsiveCardGrid<AccountListUI.AccountItem> grid = accountGrid(ui);
            grid.setSingleColumn(false);
            grid.setItems(FXCollections.observableArrayList(IntStream.range(0, 10)
                    .mapToObj(AccountListUILayoutTest::accountItem)
                    .toList()));
            grid.resize(1810, 500);
            grid.layout();
            return firstCard(grid).getPrefWidth();
        });

        assertTrue(cardWidth >= 340, "Expected full-width account cards to be wide enough for about five columns");
        assertTrue(cardWidth <= 365, "Expected full-width account cards to remain responsive rather than fixed oversized cards");
    }

    @Test
    void embeddedAccountHeaderUsesSectionIconsInsteadOfFooterButton() throws Exception {
        HeaderSnapshot snapshot = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(true, null, null);
            return new HeaderSnapshot(buttonAccessibleTexts(ui), containsStyleClass(ui, "account-footer"));
        });

        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("autoSort") + ": " + I18n.tr("autoSortDefault")));
        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("autoNewAccount")));
        assertFalse(snapshot.hasFooter());
    }

    @Test
    void accountSortModesKeepPinnedDefaultAndSortName() throws Exception {
        List<List<String>> orders = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(false, null, null);
            masterAccountItems(ui).setAll(
                    accountItem("Bravo", "2", false, 0),
                    accountItem("Pinned", "3", true, 1),
                    accountItem("Alpha", "1", false, 2)
            );

            invokeApplyAccountOrdering(ui);
            List<String> defaultOrder = accountGrid(ui).getItems().stream().map(AccountListUI.AccountItem::getAccountName).toList();
            setAccountSortMode(ui, "ASCENDING");
            List<String> ascending = accountGrid(ui).getItems().stream().map(AccountListUI.AccountItem::getAccountName).toList();
            setAccountSortMode(ui, "DESCENDING");
            List<String> descending = accountGrid(ui).getItems().stream().map(AccountListUI.AccountItem::getAccountName).toList();
            setAccountSortMode(ui, "DEFAULT");
            List<String> defaultAgain = accountGrid(ui).getItems().stream().map(AccountListUI.AccountItem::getAccountName).toList();

            return List.of(defaultOrder, ascending, descending, defaultAgain);
        });

        assertEquals(List.of("Pinned", "Bravo", "Alpha"), orders.get(0));
        assertEquals(List.of("Alpha", "Bravo", "Pinned"), orders.get(1));
        assertEquals(List.of("Pinned", "Bravo", "Alpha"), orders.get(2));
        assertEquals(List.of("Pinned", "Bravo", "Alpha"), orders.get(3));
    }

    @Test
    void activeBrowserSearchOnlyFiltersRightSideBrowser() throws Exception {
        BrowserSearchClearSnapshot snapshot = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(true, null, null);
            RecordingCategoryListUI activeBrowser = new RecordingCategoryListUI();
            setActiveCategoryListUI(ui, activeBrowser);
            masterAccountItems(ui).setAll(
                    accountItem("Sports Account", "1", false, 0),
                    accountItem("Movies Account", "2", false, 1),
                    accountItem("News Account", "3", false, 2)
            );
            invokeApplyAccountOrdering(ui);
            switchHeaderSearchMode(ui, "ACTIVE_BROWSER", false);

            ui.table.getTextField().setText("movies");
            invokeApplyAccountOrdering(ui);

            return new BrowserSearchClearSnapshot(
                    accountGrid(ui).getItems().stream()
                            .map(AccountListUI.AccountItem::getAccountName)
                            .toList(),
                    List.copyOf(activeBrowser.queries)
            );
        });

        assertEquals(List.of("Sports Account", "Movies Account", "News Account"), snapshot.visibleAccounts());
        assertEquals("movies", snapshot.browserQueries().getLast());
    }

    @Test
    void clearingBrowserHeaderSearchFromNestedViewRestoresAccountList() throws Exception {
        BrowserSearchClearSnapshot snapshot = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(true, null, null);
            RecordingCategoryListUI activeBrowser = new RecordingCategoryListUI();
            setActiveCategoryListUI(ui, activeBrowser);
            masterAccountItems(ui).setAll(
                    accountItem("Sports Account", "1", false, 0),
                    accountItem("Movies Account", "2", false, 1),
                    accountItem("News Account", "3", false, 2)
            );
            invokeApplyAccountOrdering(ui);
            switchHeaderSearchMode(ui, "ACTIVE_BROWSER", false);

            ui.table.getTextField().setText("movies");
            replaceBrowserHeaderSearchText(ui, "");

            return new BrowserSearchClearSnapshot(
                    accountGrid(ui).getItems().stream()
                            .map(AccountListUI.AccountItem::getAccountName)
                            .toList(),
                    List.copyOf(activeBrowser.queries)
            );
        });

        assertEquals(List.of("Sports Account", "Movies Account", "News Account"), snapshot.visibleAccounts());
        assertEquals("", snapshot.browserQueries().getLast());
    }

    @Test
    void compactAccountBrowserUsesSingleLineRowsEvenWhenThumbnailsEnabled() throws Exception {
        AccountRowSnapshot snapshot = runOnFxThread(() -> {
            setThumbnailsEnabled(true);
            AccountListUI ui = new AccountListUI(true, null, null);
            masterAccountItems(ui).setAll(accountItem("A very long account title", "1", false, 0));
            invokeApplyAccountOrdering(ui);
            invokeSetAccountBrowserCompact(ui, true);
            ResponsiveCardGrid<AccountListUI.AccountItem> grid = accountGrid(ui);
            grid.resize(430, 500);
            grid.layout();

            Region card = firstCard(grid);
            return new AccountRowSnapshot(
                    card.getStyleClass().contains("plain-text-row-card"),
                    card instanceof HBox,
                    ((Parent) card).getChildrenUnmodifiable().size(),
                    containsStyleClass(card, "account-card-type"),
                    containsStyleClass(card, "account-card-metrics"),
                    containsStyleClass(card, "account-card-menu-button"),
                    card.getMinHeight()
            );
        });

        assertTrue(snapshot.plainTextRow());
        assertTrue(snapshot.singleLineContainer());
        assertEquals(2, snapshot.childCount());
        assertFalse(snapshot.hasAccountType());
        assertFalse(snapshot.hasAccountMetrics());
        assertTrue(snapshot.hasAccountMenu());
        assertEquals(42, snapshot.minHeight(), 0.01);
    }

    @SuppressWarnings("unchecked")
    private static ResponsiveCardGrid<AccountListUI.AccountItem> accountGrid(AccountListUI ui) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("accountGrid");
        field.setAccessible(true);
        return (ResponsiveCardGrid<AccountListUI.AccountItem>) field.get(ui);
    }

    private static Region firstCard(ResponsiveCardGrid<?> grid) {
        FlowPane cardPane = (FlowPane) grid.getChildren().getFirst();
        return (Region) cardPane.getChildren().getFirst();
    }

    @SuppressWarnings("unchecked")
    private static ObservableList<AccountListUI.AccountItem> masterAccountItems(AccountListUI ui) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("masterAccountItems");
        field.setAccessible(true);
        return (ObservableList<AccountListUI.AccountItem>) field.get(ui);
    }

    private static void invokeApplyAccountOrdering(AccountListUI ui) throws Exception {
        var method = AccountListUI.class.getDeclaredMethod("applyAccountOrdering");
        method.setAccessible(true);
        method.invoke(ui);
    }

    private static void setAccountSortMode(AccountListUI ui, String sortModeName) throws Exception {
        Object sortMode = accountSortMode(sortModeName);
        var method = AccountListUI.class.getDeclaredMethod("setAccountSortMode", sortMode.getClass());
        method.setAccessible(true);
        method.invoke(ui, sortMode);
    }

    private static void switchHeaderSearchMode(AccountListUI ui, String searchModeName, boolean resetBrowserSearch) throws Exception {
        Object searchMode = headerSearchMode(searchModeName);
        var method = AccountListUI.class.getDeclaredMethod("switchHeaderSearchMode", searchMode.getClass(), boolean.class);
        method.setAccessible(true);
        method.invoke(ui, searchMode, resetBrowserSearch);
    }

    private static void replaceBrowserHeaderSearchText(AccountListUI ui, String text) throws Exception {
        var method = AccountListUI.class.getDeclaredMethod("replaceBrowserHeaderSearchText", String.class);
        method.setAccessible(true);
        method.invoke(ui, text);
    }

    private static void setActiveCategoryListUI(AccountListUI ui, CategoryListUI categoryListUI) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("activeCategoryListUI");
        field.setAccessible(true);
        field.set(ui, categoryListUI);
    }

    private static void invokeSetAccountBrowserCompact(AccountListUI ui, boolean compact) throws Exception {
        var method = AccountListUI.class.getDeclaredMethod("setAccountBrowserCompact", boolean.class);
        method.setAccessible(true);
        method.invoke(ui, compact);
    }

    private static void setThumbnailsEnabled(boolean enabled) {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEnableThumbnails(enabled);
        ConfigurationService.getInstance().save(configuration);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object accountSortMode(String sortModeName) throws Exception {
        Class enumClass = Class.forName("com.uiptv.ui.AccountListUI$AccountSortMode");
        return Enum.valueOf(enumClass, sortModeName);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object headerSearchMode(String searchModeName) throws Exception {
        Class enumClass = Class.forName("com.uiptv.ui.AccountListUI$HeaderSearchMode");
        return Enum.valueOf(enumClass, searchModeName);
    }

    private static List<String> buttonAccessibleTexts(Node root) {
        List<String> values = new ArrayList<>();
        collectButtonAccessibleTexts(root, values);
        return values;
    }

    private static void collectButtonAccessibleTexts(Node node, List<String> values) {
        if (node instanceof Button button && button.getAccessibleText() != null) {
            values.add(button.getAccessibleText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectButtonAccessibleTexts(child, values);
            }
        }
    }

    private static boolean containsStyleClass(Node node, String styleClass) {
        if (node.getStyleClass().contains(styleClass)) {
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsStyleClass(child, styleClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AccountListUI.AccountItem accountItem(int index) {
        return accountItem("Account " + index, String.valueOf(index), false, index);
    }

    private static AccountListUI.AccountItem accountItem(String name, String id, boolean pinned, int originalOrder) {
        return new AccountListUI.AccountItem(
                new SimpleStringProperty(name),
                new SimpleStringProperty(id),
                new SimpleStringProperty("XTREME_API"),
                pinned,
                originalOrder,
                12,
                100
        );
    }

    private record HeaderSnapshot(List<String> accessibleTexts, boolean hasFooter) {
    }

    private record AccountRowSnapshot(
            boolean plainTextRow,
            boolean singleLineContainer,
            int childCount,
            boolean hasAccountType,
            boolean hasAccountMetrics,
            boolean hasAccountMenu,
            double minHeight
    ) {
    }

    private record BrowserSearchClearSnapshot(List<String> visibleAccounts, List<String> browserQueries) {
    }

    private static final class RecordingCategoryListUI extends CategoryListUI {
        private final List<String> queries = new ArrayList<>();

        private RecordingCategoryListUI() {
            super(new Account(), true);
        }

        @Override
        public void setSearchQuery(String searchText) {
            queries.add(searchText == null ? "" : searchText);
        }
    }
}
