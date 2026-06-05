package com.uiptv.ui;

import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.stream.IntStream;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
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

    private static AccountListUI.AccountItem accountItem(int index) {
        return new AccountListUI.AccountItem(
                new SimpleStringProperty("Account " + index),
                new SimpleStringProperty(String.valueOf(index)),
                new SimpleStringProperty("XTREME_API"),
                false,
                index,
                12,
                100
        );
    }
}
