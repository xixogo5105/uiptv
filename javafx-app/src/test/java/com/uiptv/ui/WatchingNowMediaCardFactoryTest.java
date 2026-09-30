package com.uiptv.ui;

import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchingNowMediaCardFactoryTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void accountLabelStaysUnderTheTitleAtEveryWidth() throws Exception {
        AccountPlacementSnapshot snapshot = runOnFxThread(() -> {
            WatchingNowMediaCardFactory.CardNodes nodes = WatchingNowMediaCardFactory
                    .builder(WatchingNowMediaCardFactory.CardType.SERIES)
                    .title("EN - You're Killing Me (2026) (CA)")
                    .account("globalgnet.live [ip]")
                    .actionButton(new Button("..."))
                    .build();
            VBox details = nodes.details();
            HBox titleRow = (HBox) details.getChildren().getFirst();
            titleRow.resize(120, 40);
            titleRow.layout();
            AccountPlacement narrow = new AccountPlacement(
                    titleRow.getChildren().contains(nodes.account()),
                    details.getChildren().contains(nodes.account()),
                    details.getChildren().indexOf(nodes.account())
            );

            titleRow.resize(900, 40);
            titleRow.layout();
            return new AccountPlacementSnapshot(
                    narrow,
                    new AccountPlacement(
                            titleRow.getChildren().contains(nodes.account()),
                            details.getChildren().contains(nodes.account()),
                            details.getChildren().indexOf(nodes.account())),
                    nodes.account().isWrapText(),
                    nodes.account().getTextOverrun()
            );
        });

        // The account name must never join the title row: in plain mode there is no poster column
        // and the grid is single-column, so the row is always wide enough to "fit" it, and with the
        // action button in place HBox splits the leftover width between the two growable children -
        // which is what rendered the account name in the middle of the line.
        assertEquals(false, snapshot.narrow().accountInline(), "account must not be in the title row when narrow");
        assertEquals(true, snapshot.narrow().accountInDetails());
        assertEquals(1, snapshot.narrow().accountIndexInDetails());

        assertEquals(false, snapshot.wide().accountInline(), "account must not be in the title row when wide");
        assertEquals(true, snapshot.wide().accountInDetails());
        assertEquals(1, snapshot.wide().accountIndexInDetails());

        assertTrue(snapshot.accountWraps());
        assertEquals(OverrunStyle.ELLIPSIS, snapshot.accountOverrun());
    }

    @Test
    void blankAccountNameIsNotManaged() throws Exception {
        boolean managed = runOnFxThread(() -> WatchingNowMediaCardFactory
                .builder(WatchingNowMediaCardFactory.CardType.SERIES)
                .title("Some Series")
                .account("")
                .actionButton(new Button("..."))
                .build()
                .account()
                .isManaged());

        assertEquals(false, managed, "an empty account name must not take up a line");
    }

    private record AccountPlacement(boolean accountInline, boolean accountInDetails, int accountIndexInDetails) {
    }

    private record AccountPlacementSnapshot(
            AccountPlacement narrow,
            AccountPlacement wide,
            boolean accountWraps,
            OverrunStyle accountOverrun
    ) {
    }
}
