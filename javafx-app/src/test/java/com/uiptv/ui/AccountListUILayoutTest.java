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
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
            AccountListUI ui = new AccountListUI(null, null);
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
    void accountCardShowsCompactExpiryAfterAccountTypeWithExpiryColor() throws Exception {
        AccountExpiryCardSnapshot snapshot = runOnFxThread(() -> {
            setThumbnailsEnabled(true);
            AccountListUI ui = new AccountListUI(null, null);
            masterAccountItems(ui).setAll(new AccountListUI.AccountItem(
                    new SimpleStringProperty("Account With Expiry"),
                    new SimpleStringProperty("1"),
                    new SimpleStringProperty("STALKER_PORTAL"),
                    false,
                    0,
                    12,
                    100,
                    "12 Sep 26",
                    AccountInfoUiUtil.ExpiryState.WARNING
            ));
            invokeApplyAccountOrdering(ui);
            ResponsiveCardGrid<AccountListUI.AccountItem> grid = accountGrid(ui);
            grid.resize(430, 500);
            grid.layout();

            Region card = firstCard(grid);
            Node typeLine = findByStyle(card, "account-card-type");
            Text expiryDate = (Text) findByStyle(card, "account-card-expiry-date");
            return new AccountExpiryCardSnapshot(
                    joinedText(typeLine),
                    expiryDate.getText(),
                    expiryDate.getStyle()
            );
        });

        assertEquals("Stalker Portal (12 Sep 26)", snapshot.typeLineText());
        assertEquals("12 Sep 26", snapshot.expiryDateText());
        assertTrue(snapshot.expiryDateStyle().contains(AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.WARNING)));
    }

    @Test
    void embeddedAccountToolbarUsesPillBarAndQuietAddButton() throws Exception {
        ToolbarSnapshot snapshot = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(null, null);
            return new ToolbarSnapshot(
                    controlAccessibleTexts(ui),
                    containsStyleClass(ui, "account-footer"),
                    containsStyleClass(ui, "uiptv-pill-bar"),
                    directChildHasStyle(ui, "account-toolbar", "uiptv-pill-bar"),
                    toolbarHasLeadingSpacer(ui),
                    containsStyleClass(ui, "list-toolbar-actions"),
                    containsStyleClass(ui, "list-toolbar-sort-menu"),
                    containsStyleClass(ui, "list-toolbar-action-button"),
                    labeledTextByStyle(ui, "list-toolbar-sort-menu"),
                    labeledHasGraphicByStyle(ui, "list-toolbar-sort-menu")
            );
        });

        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("autoSort") + ": " + I18n.tr("autoSortDefault")));
        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("autoNewAccount")));
        assertTrue(snapshot.hasFilterPillBar());
        assertTrue(snapshot.filterPillBarIsInToolbar());
        assertTrue(snapshot.toolbarHasLeadingSpacer());
        assertTrue(snapshot.hasToolbarActions());
        assertTrue(snapshot.hasSortDropdown());
        assertTrue(snapshot.hasQuietActionButton());
        assertEquals("Default", snapshot.sortDropdownText());
        assertTrue(snapshot.sortDropdownHasGraphic());
        assertFalse(snapshot.hasFooter());
    }

    @Test
    void accountToolbarKeepsSpacingBetweenFilterPillBarAndActionsAfterCss() throws Exception {
        double spacing = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(null, null);
            Scene scene = new Scene(ui, 520, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    AccountListUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();

            Node toolbar = findByStyle(ui, "account-toolbar");
            return toolbar instanceof VBox vBox ? vBox.getSpacing() : -1.0;
        });

        assertEquals(8.0, spacing, 0.01);
    }

    @Test
    void accountToolbarStacksFilterAndActionsWhenInlineWidthIsConstrained() throws Exception {
        List<Boolean> layout = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(null, null);
            Scene scene = new Scene(ui, 360, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    AccountListUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();
            ui.resize(360, 720);
            ui.layout();
            ui.layout();

            Node toolbar = findByStyle(ui, "account-toolbar");
            Parent parent = (Parent) toolbar;
            return List.of(
                    parent.getChildrenUnmodifiable().size() == 2,
                    parent.getChildrenUnmodifiable().get(0).getStyleClass().contains("uiptv-pill-bar"),
                    parent.getChildrenUnmodifiable().get(1).getStyleClass().contains("list-toolbar-actions")
            );
        });

        assertTrue(layout.get(0));
        assertTrue(layout.get(1));
        assertTrue(layout.get(2));
    }

    @Test
    void accountPillBarKeepsWrappedDrawerTabsInsideBackground() throws Exception {
        List<Double> heights = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(null, null);
            Scene scene = new Scene(ui, 520, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    AccountListUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();

            Region pillBar = (Region) findByStyle(ui, "uiptv-pill-bar");
            ui.resize(1800, 720);
            ui.layout();
            ui.layout();
            double wideHeight = pillBar.getHeight();

            ui.resize(520, 720);
            ui.layout();
            ui.layout();
            double normalLayoutHeight = pillBar.getHeight();

            invokeSetAccountBrowserCompact(ui, true);
            ui.applyCss();
            ui.resize(520, 720);
            ui.layout();
            ui.layout();
            double compactHeight = pillBar.getHeight();

            ui.setMediaDrawerMode(true);
            ui.applyCss();
            ui.resize(520, 720);
            ui.layout();
            ui.layout();
            double drawerLayoutHeight = pillBar.getHeight();

            firePillByText(pillBar, "M3U8 URL");
            ui.applyCss();
            ui.layout();
            ui.layout();
            double afterSelectionHeight = pillBar.getHeight();
            Region content = (Region) findByStyle(pillBar, "uiptv-pill-bar-content");

            return List.of(
                    wideHeight,
                    normalLayoutHeight,
                    compactHeight,
                    drawerLayoutHeight,
                    afterSelectionHeight,
                    content.getBoundsInParent().getMaxY()
            );
        });

        assertEquals(40, heights.get(0), 0.01);
        assertEquals(44, heights.get(1), 0.01);
        assertEquals(44, heights.get(2), 0.01);
        assertEquals(44, heights.get(3), 0.01);
        assertEquals(44, heights.get(4), 0.01);
    }

    @Test
    void accountSortModesKeepPinnedDefaultAndSortName() throws Exception {
        List<List<String>> orders = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(null, null);
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
            AccountListUI ui = new AccountListUI(null, null);
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
            AccountListUI ui = new AccountListUI(null, null);
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
            AccountListUI ui = new AccountListUI(null, null);
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

    @Test
    void activeAccountBrowserUsesSinglePaneAtMinimumWidthAndRestoresSplitWhenWide() throws Exception {
        AccountBrowserResponsiveSnapshot snapshot = runOnFxThread(() -> {
            AccountListUI ui = new AccountListUI(null, null);
            RecordingCategoryListUI activeBrowser = new RecordingCategoryListUI();
            StackPane root = new StackPane(ui);
            new Scene(root, 900, 720);
            root.resize(900, 720);
            ui.resize(900, 720);
            root.layout();
            ui.layout();

            invokeShowAccountBrowser(ui, activeBrowser);
            HBox splitLayout = browserLayout(ui);

            root.resize(480, 720);
            ui.resize(480, 720);
            root.layout();
            ui.layout();
            boolean minimumUsesSinglePane = currentContent(ui) == activeBrowser
                    && embeddedContainer(ui).getChildren().contains(activeBrowser);

            root.resize(900, 720);
            ui.resize(900, 720);
            root.layout();
            ui.layout();

            return new AccountBrowserResponsiveSnapshot(
                    minimumUsesSinglePane,
                    currentContent(ui) == splitLayout,
                    splitLayout.getChildren().contains(listView(ui)),
                    splitLayout.getChildren().contains(activeBrowser)
            );
        });

        assertTrue(snapshot.minimumUsesSinglePane());
        assertTrue(snapshot.wideUsesSplitLayout());
        assertTrue(snapshot.wideSplitContainsAccountList());
        assertTrue(snapshot.wideSplitContainsBrowser());
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

    private static void invokeShowAccountBrowser(AccountListUI ui, CategoryListUI categoryListUI) throws Exception {
        Method method = AccountListUI.class.getDeclaredMethod("showAccountBrowser", CategoryListUI.class);
        method.setAccessible(true);
        method.invoke(ui, categoryListUI);
    }

    private static Node currentContent(AccountListUI ui) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("currentContent");
        field.setAccessible(true);
        return (Node) field.get(ui);
    }

    private static HBox browserLayout(AccountListUI ui) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("browserLayout");
        field.setAccessible(true);
        return (HBox) field.get(ui);
    }

    private static VBox embeddedContainer(AccountListUI ui) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("embeddedContainer");
        field.setAccessible(true);
        return (VBox) field.get(ui);
    }

    private static VBox listView(AccountListUI ui) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("listView");
        field.setAccessible(true);
        return (VBox) field.get(ui);
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

    private static List<String> controlAccessibleTexts(Node root) {
        List<String> values = new ArrayList<>();
        collectControlAccessibleTexts(root, values);
        return values;
    }

    private static void collectControlAccessibleTexts(Node node, List<String> values) {
        if (node instanceof Labeled labeled && labeled.getAccessibleText() != null) {
            values.add(labeled.getAccessibleText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectControlAccessibleTexts(child, values);
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

    private static String joinedText(Node node) {
        List<String> values = new ArrayList<>();
        collectText(node, values);
        return String.join("", values);
    }

    private static void collectText(Node node, List<String> values) {
        if (node instanceof Text text) {
            values.add(text.getText());
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectText(child, values);
            }
        }
    }

    private static String labeledTextByStyle(Node root, String styleClass) {
        Node node = findByStyle(root, styleClass);
        return node instanceof Labeled labeled ? labeled.getText() : "";
    }

    private static boolean labeledHasGraphicByStyle(Node root, String styleClass) {
        Node node = findByStyle(root, styleClass);
        return node instanceof Labeled labeled && labeled.getGraphic() != null;
    }

    private static boolean directChildHasStyle(Node root, String parentStyleClass, String childStyleClass) {
        Node node = findByStyle(root, parentStyleClass);
        if (!(node instanceof Parent parent)) {
            return false;
        }
        return parent.getChildrenUnmodifiable().stream()
                .anyMatch(child -> child.getStyleClass().contains(childStyleClass));
    }

    private static boolean toolbarHasLeadingSpacer(Node root) {
        Node node = findByStyle(root, "list-toolbar-actions");
        if (!(node instanceof Parent parent) || parent.getChildrenUnmodifiable().isEmpty()) {
            return false;
        }
        Node first = parent.getChildrenUnmodifiable().getFirst();
        return first instanceof Region && first.getStyleClass().isEmpty();
    }

    private static Node findByStyle(Node node, String styleClass) {
        if (node.getStyleClass().contains(styleClass)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = findByStyle(child, styleClass);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static boolean firePillByText(Node node, String text) {
        if (node instanceof ToggleButton toggleButton && Objects.equals(toggleButton.getText(), text)) {
            toggleButton.fire();
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (firePillByText(child, text)) {
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

    private record ToolbarSnapshot(List<String> accessibleTexts,
                                   boolean hasFooter,
                                   boolean hasFilterPillBar,
                                   boolean filterPillBarIsInToolbar,
                                   boolean toolbarHasLeadingSpacer,
                                   boolean hasToolbarActions,
                                   boolean hasSortDropdown,
                                   boolean hasQuietActionButton,
                                   String sortDropdownText,
                                   boolean sortDropdownHasGraphic) {
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

    private record AccountExpiryCardSnapshot(String typeLineText, String expiryDateText, String expiryDateStyle) {
    }

    private record BrowserSearchClearSnapshot(List<String> visibleAccounts, List<String> browserQueries) {
    }

    private record AccountBrowserResponsiveSnapshot(
            boolean minimumUsesSinglePane,
            boolean wideUsesSplitLayout,
            boolean wideSplitContainsAccountList,
            boolean wideSplitContainsBrowser
    ) {
    }

    private static final class RecordingCategoryListUI extends CategoryListUI {
        private final List<String> queries = new ArrayList<>();

        private RecordingCategoryListUI() {
            super(new Account());
        }

        @Override
        public void setSearchQuery(String searchText) {
            queries.add(searchText == null ? "" : searchText);
        }
    }
}
