package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.model.Category;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryListUITest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void openingDetailViewClearsSearchAndRestoresHiddenCategoryCards() throws Exception {
        int cardCount = runOnFxThread(() -> {
            Account account = new Account();
            account.setAccountName("Account");
            account.setAction(Account.AccountAction.itv);
            CategoryListUI ui = new CategoryListUI(account);
            ui.setItems(List.of(
                    category("uk", "ENGLISH UK"),
                    category("sports", "Sports")
            ));
            ui.setSearchQuery("uk");

            ChannelListUI channelListUI = new ChannelListUI(account, "ENGLISH UK", "uk", Account.AccountAction.itv);
            try {
                showDetailView(ui, channelListUI, "ENGLISH UK");
                return categoryCardPane(ui).getChildren().size();
            } finally {
                channelListUI.dispose();
            }
        });

        assertEquals(3, cardCount);
    }

    @Test
    void mediaListConstructorsDoNotMutateSourceAccountAction() throws Exception {
        runOnFxThread(() -> {
            Account account = new Account();
            account.setAccountName("Account");
            account.setAction(Account.AccountAction.itv);

            CategoryListUI categories = new CategoryListUI(AccountMediaContext.from(account, Account.AccountAction.vod));
            ChannelListUI channels = new ChannelListUI(account, "Series", "series", Account.AccountAction.series);
            try {
                assertEquals(Account.AccountAction.itv, account.getAction());
            } finally {
                categories.dispose();
                channels.dispose();
            }
            return null;
        });
    }

    @Test
    void detailBackReturnsFromNestedEpisodeDetailToCategoryListInOneStep() throws Exception {
        boolean detailPaneVisible = runOnFxThread(() -> {
            Account account = new Account();
            account.setAccountName("Account");
            account.setAction(Account.AccountAction.series);

            CategoryListUI categories = new CategoryListUI(AccountMediaContext.from(account, Account.AccountAction.series));
            ChannelListUI channels = new ChannelListUI(account, "Series", "series", Account.AccountAction.series);
            try {
                showDetailView(categories, channels, "Series");
                showChannelDetailView(channels, new Label("Episodes"), "Series");
                navigateBackFromDetail(categories);
                return categories.getChildren().contains(detailPane(categories));
            } finally {
                categories.dispose();
                channels.dispose();
            }
        });

        assertFalse(detailPaneVisible);
    }

    @Test
    void categoryCardSingleClickOpensCachedChannelPane() throws Exception {
        boolean detailPaneVisible = runOnFxThread(() -> {
            Account account = new Account();
            account.setAccountName("Account");
            account.setAction(Account.AccountAction.itv);

            CategoryListUI categories = new CategoryListUI(account);
            ChannelListUI channels = new ChannelListUI(account, "Sports", "sports", Account.AccountAction.itv);
            try {
                categories.setItems(List.of(
                        category("sports", "Sports"),
                        category("news", "News")
                ));
                CategoryListUI.CategoryItem item = firstCategoryItem(categories);
                installCachedChannelState(categories, item, channels);

                Node card = firstCategoryCard(categories);
                javafx.event.Event.fireEvent(card, mouseClick(card));

                return categories.getChildren().contains(detailPane(categories));
            } finally {
                categories.dispose();
                channels.dispose();
            }
        });

        assertTrue(detailPaneVisible);
    }

    @Test
    void largeCategoryListRendersOnlyVisibleCardWindow() throws Exception {
        int renderedCards = runOnFxThread(() -> {
            Account account = new Account();
            account.setAccountName("Account");
            account.setAction(Account.AccountAction.itv);
            CategoryListUI ui = new CategoryListUI(account);
            List<Category> categories = new java.util.ArrayList<>();
            for (int index = 0; index < 10_000; index++) {
                categories.add(category("category-" + index, "Category " + index));
            }
            ui.setItems(categories);
            return categoryCardPane(ui).getChildren().size();
        });

        assertTrue(renderedCards < 250);
    }

    private static Category category(String id, String title) {
        Category category = new Category();
        category.setDbId(id);
        category.setCategoryId(id);
        category.setTitle(title);
        return category;
    }

    private static void showDetailView(CategoryListUI ui, ChannelListUI channelListUI, String title) throws Exception {
        Method method = CategoryListUI.class.getDeclaredMethod("showDetailView", ChannelListUI.class, String.class);
        method.setAccessible(true);
        method.invoke(ui, channelListUI, title);
    }

    private static void showChannelDetailView(ChannelListUI ui, Node content, String title) throws Exception {
        Method method = ChannelListUI.class.getDeclaredMethod("showDetailView", Node.class, String.class);
        method.setAccessible(true);
        method.invoke(ui, content, title);
    }

    private static void navigateBackFromDetail(CategoryListUI ui) throws Exception {
        Method method = CategoryListUI.class.getDeclaredMethod("navigateBackFromDetail");
        method.setAccessible(true);
        method.invoke(ui);
    }

    private static VBox detailPane(CategoryListUI ui) throws Exception {
        Field field = CategoryListUI.class.getDeclaredField("detailPane");
        field.setAccessible(true);
        return (VBox) field.get(ui);
    }

    private static FlowPane categoryCardPane(CategoryListUI ui) throws Exception {
        Field field = CategoryListUI.class.getDeclaredField("categoryCardGrid");
        field.setAccessible(true);
        ResponsiveCardGrid<?> grid = (ResponsiveCardGrid<?>) field.get(ui);
        return (FlowPane) grid.getChildren().getFirst();
    }

    @SuppressWarnings("unchecked")
    private static CategoryListUI.CategoryItem firstCategoryItem(CategoryListUI ui) throws Exception {
        Field field = CategoryListUI.class.getDeclaredField("categoryItems");
        field.setAccessible(true);
        return ((javafx.collections.ObservableList<CategoryListUI.CategoryItem>) field.get(ui)).getFirst();
    }

    private static Node firstCategoryCard(CategoryListUI ui) throws Exception {
        return categoryCardPane(ui).getChildren().stream()
                .filter(node -> node.getStyleClass().contains("account-category-card"))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void installCachedChannelState(CategoryListUI ui,
                                                  CategoryListUI.CategoryItem selectedCategory,
                                                  ChannelListUI channelListUI) throws Exception {
        Class<?> modeStateClass = Class.forName("com.uiptv.ui.CategoryListUI$ModeState");
        Constructor<?> constructor = modeStateClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object modeState = constructor.newInstance();
        Field categoriesField = modeStateClass.getDeclaredField("categories");
        categoriesField.setAccessible(true);
        categoriesField.set(modeState, List.of(category("sports", "Sports"), category("news", "News")));
        Field selectedCategoryField = modeStateClass.getDeclaredField("selectedCategory");
        selectedCategoryField.setAccessible(true);
        selectedCategoryField.set(modeState, selectedCategory);
        Field channelListField = modeStateClass.getDeclaredField("channelListUI");
        channelListField.setAccessible(true);
        channelListField.set(modeState, channelListUI);

        Field modeStatesField = CategoryListUI.class.getDeclaredField("modeStates");
        modeStatesField.setAccessible(true);
        ((Map<Account.AccountAction, Object>) modeStatesField.get(ui)).put(Account.AccountAction.itv, modeState);
    }

    private static MouseEvent mouseClick(Node target) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        );
    }
}
