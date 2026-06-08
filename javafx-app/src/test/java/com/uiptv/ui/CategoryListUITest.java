package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.model.Category;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                return categoryCardList(ui).getChildren().size();
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

    private static VBox categoryCardList(CategoryListUI ui) throws Exception {
        Field field = CategoryListUI.class.getDeclaredField("categoryCardList");
        field.setAccessible(true);
        return (VBox) field.get(ui);
    }
}
