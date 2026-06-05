package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkChannelListUITest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void favoriteHeaderUsesSectionIconsInsteadOfFooterButton() throws Exception {
        HeaderSnapshot snapshot = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            return new HeaderSnapshot(buttonAccessibleTexts(ui), containsStyleClass(ui, "bookmark-footer"));
        });

        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("autoSort") + ": " + I18n.tr("autoSortDefault")));
        assertTrue(snapshot.accessibleTexts().contains(I18n.tr("searchableTableManageTabs")));
        assertFalse(snapshot.hasFooter());
    }

    @Test
    void favoriteSortModesKeepDefaultOrderAndSortName() throws Exception {
        List<List<String>> orders = runOnFxThread(() -> {
            BookmarkChannelListUI ui = new BookmarkChannelListUI(null, null);
            allBookmarkItems(ui).addAll(List.of(
                    bookmarkItem("3", "Zulu", "Account C"),
                    bookmarkItem("1", "Alpha", "Account A"),
                    bookmarkItem("2", "Bravo", "Account B")
            ));

            invokeFilterView(ui);
            List<String> defaultOrder = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "ASCENDING");
            List<String> ascending = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "DESCENDING");
            List<String> descending = filteredChannelNames(ui);
            setBookmarkSortMode(ui, "DEFAULT");
            List<String> defaultAgain = filteredChannelNames(ui);

            return List.of(defaultOrder, ascending, descending, defaultAgain);
        });

        assertEquals(List.of("Zulu", "Alpha", "Bravo"), orders.get(0));
        assertEquals(List.of("Alpha", "Bravo", "Zulu"), orders.get(1));
        assertEquals(List.of("Zulu", "Bravo", "Alpha"), orders.get(2));
        assertEquals(List.of("Zulu", "Alpha", "Bravo"), orders.get(3));
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

    private static void setBookmarkSortMode(BookmarkChannelListUI ui, String sortModeName) throws Exception {
        Object sortMode = bookmarkSortMode(sortModeName);
        Method method = BookmarkChannelListUI.class.getDeclaredMethod("setBookmarkSortMode", sortMode.getClass());
        method.setAccessible(true);
        method.invoke(ui, sortMode);
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

    private record HeaderSnapshot(List<String> accessibleTexts, boolean hasFooter) {
    }
}
