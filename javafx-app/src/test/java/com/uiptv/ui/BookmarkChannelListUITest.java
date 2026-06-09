package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import com.uiptv.widget.PillBar;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkChannelListUITest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void favoriteToolbarUsesPillBarAndQuietManageTabsButton() throws Exception {
        ToolbarSnapshot snapshot = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            return new ToolbarSnapshot(
                    controlAccessibleTexts(ui),
                    containsStyleClass(ui, "bookmark-footer"),
                    containsStyleClass(ui, "bookmark-list-panel"),
                    containsStyleClass(ui, "uiptv-pill-bar"),
                    directChildHasStyle(ui, "bookmark-category-row", "uiptv-pill-bar"),
                    toolbarHasLeadingSpacer(ui),
                    containsStyleClass(ui, "list-toolbar-actions"),
                    containsStyleClass(ui, "list-toolbar-sort-menu"),
                    containsStyleClass(ui, "list-toolbar-action-button"),
                    labeledTextByStyle(ui, "list-toolbar-sort-menu"),
                    labeledHasGraphicByStyle(ui, "list-toolbar-sort-menu")
            );
        });

        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("autoSort") + ": " + I18n.tr("autoSortDefault")));
        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("searchableTableManageTabs")));
        assertTrue(snapshot.hasListPanel());
        assertTrue(snapshot.hasFilterPillBar());
        assertTrue(snapshot.filterPillBarIsInCategoryRow());
        assertTrue(snapshot.toolbarHasLeadingSpacer());
        assertTrue(snapshot.hasToolbarActions());
        assertTrue(snapshot.hasSortDropdown());
        assertTrue(snapshot.hasQuietActionButton());
        assertEquals("Default", snapshot.sortDropdownText());
        assertTrue(snapshot.sortDropdownHasGraphic());
        assertFalse(snapshot.hasFooter());
    }

    @Test
    void favoritePillBarReservesDynamicRowsFromFiveItemNarrowAllocation() throws Exception {
        List<Double> heights = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            populateCategoryPills(ui, List.of(
                    new BookmarkCategory(null, "All"),
                    new BookmarkCategory("cricket", "Cricket"),
                    new BookmarkCategory("movies", "Movies"),
                    new BookmarkCategory("drama", "Drama"),
                    new BookmarkCategory("4k", "4K"),
                    new BookmarkCategory("news", "News"),
                    new BookmarkCategory("samad", "A Samad"),
                    new BookmarkCategory("kids", "Kids"),
                    new BookmarkCategory("sports", "Sports"),
                    new BookmarkCategory("music", "Music"),
                    new BookmarkCategory("documentary", "Documentary")
            ));
            Scene scene = new Scene(ui, 1920, 1080);
            scene.getStylesheets().add(Objects.requireNonNull(
                    BookmarkChannelListUITest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();

            Region pillBar = (Region) findByStyle(ui, "uiptv-pill-bar");
            ui.resize(1800, 720);
            ui.layout();
            ui.layout();
            double wideLayoutHeight = pillBar.getHeight();

            ui.resize(520, 720);
            ui.layout();
            ui.layout();
            double narrowLayoutHeight = pillBar.getHeight();

            return List.of(wideLayoutHeight, narrowLayoutHeight);
        });

        assertEquals(40, heights.get(0), 0.01);
        assertEquals(44, heights.get(1), 0.01);
    }

    @Test
    void favoriteToolbarStacksFilterAndActionsWhenInlineWidthIsConstrained() throws Exception {
        StackedToolbarSnapshot layout = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            populateCategoryPills(ui, List.of(
                    new BookmarkCategory(null, "All"),
                    new BookmarkCategory("cricket", "Cricket"),
                    new BookmarkCategory("movies", "Movies"),
                    new BookmarkCategory("drama", "Drama"),
                    new BookmarkCategory("4k", "4K"),
                    new BookmarkCategory("news", "News"),
                    new BookmarkCategory("samad", "A Samad")
            ));
            Scene scene = new Scene(ui, 360, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    BookmarkChannelListUITest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();
            ui.resize(360, 720);
            ui.layout();
            ui.layout();

            Node toolbar = findByStyle(ui, "bookmark-category-row");
            Parent parent = (Parent) toolbar;
            Region pillBar = (Region) findByStyle(ui, "uiptv-pill-bar");
            return new StackedToolbarSnapshot(
                    parent.getChildrenUnmodifiable().size() == 2,
                    parent.getChildrenUnmodifiable().get(0).getStyleClass().contains("uiptv-pill-bar"),
                    parent.getChildrenUnmodifiable().get(1).getStyleClass().contains("list-toolbar-actions"),
                    pillBar.getWidth()
            );
        });

        assertTrue(layout.stacked());
        assertTrue(layout.pillBarFirst());
        assertTrue(layout.actionsSecond());
        assertTrue(layout.pillBarWidth() > PillBar.COMPACT_DROPDOWN_PREF_WIDTH);
    }

    @Test
    void favoritePillBarKeepsSevenCategoryWrapInsideBackground() throws Exception {
        List<Double> heights = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            populateCategoryPills(ui, List.of(
                    new BookmarkCategory(null, "All"),
                    new BookmarkCategory("cricket", "Cricket"),
                    new BookmarkCategory("movies", "Movies"),
                    new BookmarkCategory("drama", "Drama"),
                    new BookmarkCategory("4k", "4K"),
                    new BookmarkCategory("news", "News"),
                    new BookmarkCategory("samad", "A Samad")
            ));
            Scene scene = new Scene(ui, 520, 720);
            scene.getStylesheets().add(Objects.requireNonNull(
                    BookmarkChannelListUITest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();

            Region pillBar = (Region) findByStyle(ui, "uiptv-pill-bar");
            ui.resize(520, 720);
            ui.layout();
            ui.layout();
            double initialHeight = pillBar.getHeight();

            firePillByText(pillBar, "News");
            ui.applyCss();
            ui.layout();
            ui.layout();
            Region content = (Region) findByStyle(pillBar, "uiptv-pill-bar-content");
            return List.of(initialHeight, pillBar.getHeight(), pillBar.getPrefHeight(), content.getBoundsInParent().getMaxY());
        });

        assertEquals(44, heights.get(0), 0.01);
        assertEquals(44, heights.get(1), 0.01);
        assertEquals(44, heights.get(2), 0.01);
    }

    @Test
    void favoriteSortModesKeepDefaultOrderAndSortNameOrAccountName() throws Exception {
        List<List<String>> orders = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            allBookmarkItems(ui).addAll(List.of(
                    bookmarkItem("3", "Zulu", "Account B"),
                    bookmarkItem("1", "Alpha", "Account C"),
                    bookmarkItem("2", "Bravo", "Account A")
            ));

            invokeFilterView(ui);
            List<String> defaultOrder = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "ASCENDING");
            List<String> ascending = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "DESCENDING");
            List<String> descending = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "ACCOUNT_ASCENDING");
            List<String> accountAscending = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "ACCOUNT_DESCENDING");
            List<String> accountDescending = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "DEFAULT");
            List<String> defaultAgain = filteredChannelNames(ui);

            return List.of(defaultOrder, ascending, descending, accountAscending, accountDescending, defaultAgain);
        });

        assertEquals(List.of("Zulu", "Alpha", "Bravo"), orders.get(0));
        assertEquals(List.of("Alpha", "Bravo", "Zulu"), orders.get(1));
        assertEquals(List.of("Zulu", "Bravo", "Alpha"), orders.get(2));
        assertEquals(List.of("Bravo", "Zulu", "Alpha"), orders.get(3));
        assertEquals(List.of("Alpha", "Zulu", "Bravo"), orders.get(4));
        assertEquals(List.of("Zulu", "Alpha", "Bravo"), orders.get(5));
    }

    @Test
    void plainTextFavoriteCardKeepsDrmBadgeVisible() throws Exception {
        BadgeSnapshot badge = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            Region card = createPlainTextBookmarkCard(ui, drmBookmarkItem());
            Node node = findByStyle(card, "drm-badge");
            if (node instanceof Labeled labeled) {
                return new BadgeSnapshot(labeled.getText(), labeled.isVisible(), labeled.isManaged());
            }
            return null;
        });

        assertNotNull(badge);
        assertEquals(I18n.tr("autoDrm"), badge.text());
        assertTrue(badge.visible());
        assertTrue(badge.managed());
    }

    @Test
    void favoriteRegistersAccountChangeListenerForImmediateAccountUpdates() throws Exception {
        ListenerSnapshot snapshot = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            boolean registered = booleanField(ui, "accountChangeListenerRegistered");
            invokeNoArg(ui, "unregisterBookmarkChangeListener");
            boolean unregistered = !booleanField(ui, "accountChangeListenerRegistered");
            return new ListenerSnapshot(registered, unregistered);
        });

        assertTrue(snapshot.registered());
        assertTrue(snapshot.unregistered());
    }

    @SuppressWarnings("unchecked")
    private static List<BookmarkChannelListUI.BookmarkItem> allBookmarkItems(BookmarkChannelListUI ui) throws Exception {
        Field field = BookmarkChannelListUI.class.getDeclaredField("allBookmarkItems");
        field.setAccessible(true);
        return (List<BookmarkChannelListUI.BookmarkItem>) field.get(ui);
    }

    @SuppressWarnings("unchecked")
    private static ObservableList<BookmarkChannelListUI.BookmarkItem> filteredItems(BookmarkChannelListUI ui) throws Exception {
        Field field = BookmarkChannelListUI.class.getDeclaredField("filteredItems");
        field.setAccessible(true);
        return (ObservableList<BookmarkChannelListUI.BookmarkItem>) field.get(ui);
    }

    private static void invokeFilterView(BookmarkChannelListUI ui) throws Exception {
        Method method = BookmarkChannelListUI.class.getDeclaredMethod("filterView");
        method.setAccessible(true);
        method.invoke(ui);
    }

    private static void populateCategoryPills(BookmarkChannelListUI ui, List<BookmarkCategory> categories) throws Exception {
        Method method = BookmarkChannelListUI.class.getDeclaredMethod("populateCategoryPills", List.class);
        method.setAccessible(true);
        method.invoke(ui, categories);
    }

    private static void setBookmarkSortMode(BookmarkChannelListUI ui, String sortModeName) throws Exception {
        Object sortMode = bookmarkSortMode(sortModeName);
        Method method = BookmarkChannelListUI.class.getDeclaredMethod("setBookmarkSortMode", sortMode.getClass());
        method.setAccessible(true);
        method.invoke(ui, sortMode);
    }

    private static Region createPlainTextBookmarkCard(BookmarkChannelListUI ui, BookmarkChannelListUI.BookmarkItem item) throws Exception {
        Method method = BookmarkChannelListUI.class.getDeclaredMethod("createPlainTextBookmarkCard", BookmarkChannelListUI.BookmarkItem.class);
        method.setAccessible(true);
        return (Region) method.invoke(ui, item);
    }

    private static void invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static boolean booleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object bookmarkSortMode(String sortModeName) throws Exception {
        Class enumClass = Class.forName("com.uiptv.ui.BookmarkChannelListUI$BookmarkSortMode");
        return Enum.valueOf(enumClass, sortModeName);
    }

    private static List<String> filteredChannelNames(BookmarkChannelListUI ui) throws Exception {
        return filteredItems(ui).stream()
                .map(BookmarkChannelListUI.BookmarkItem::getChannelName)
                .toList();
    }

    private static BookmarkChannelListUI.BookmarkItem bookmarkItem(String id, String channelName, String accountName) {
        return new BookmarkChannelListUI.BookmarkItem(
                new SimpleStringProperty(id),
                new SimpleStringProperty(channelName),
                new SimpleStringProperty("channel-" + id),
                new SimpleStringProperty("cmd-" + id),
                new SimpleStringProperty(accountName),
                new SimpleStringProperty("All"),
                new SimpleStringProperty("http://example.test"),
                new SimpleStringProperty(channelName + " (" + accountName + ")"),
                new SimpleStringProperty(null),
                new SimpleStringProperty(""),
                Account.AccountAction.itv,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static BookmarkChannelListUI.BookmarkItem drmBookmarkItem() {
        return new BookmarkChannelListUI.BookmarkItem(
                new SimpleStringProperty("drm-1"),
                new SimpleStringProperty("DRM Channel"),
                new SimpleStringProperty("channel-drm-1"),
                new SimpleStringProperty("cmd-drm-1"),
                new SimpleStringProperty("Account A"),
                new SimpleStringProperty("All"),
                new SimpleStringProperty("http://example.test"),
                new SimpleStringProperty("DRM Channel (Account A)"),
                new SimpleStringProperty(null),
                new SimpleStringProperty(""),
                Account.AccountAction.itv,
                "clearkey",
                null,
                null,
                null,
                null
        );
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

    private record ToolbarSnapshot(List<String> accessibleTexts,
                                   boolean hasFooter,
                                   boolean hasListPanel,
                                   boolean hasFilterPillBar,
                                   boolean filterPillBarIsInCategoryRow,
                                   boolean toolbarHasLeadingSpacer,
                                   boolean hasToolbarActions,
                                   boolean hasSortDropdown,
                                   boolean hasQuietActionButton,
                                   String sortDropdownText,
                                   boolean sortDropdownHasGraphic) {
    }

    private record BadgeSnapshot(String text, boolean visible, boolean managed) {
    }

    private record ListenerSnapshot(boolean registered, boolean unregistered) {
    }

    private record StackedToolbarSnapshot(boolean stacked,
                                          boolean pillBarFirst,
                                          boolean actionsSecond,
                                          double pillBarWidth) {
    }
}
