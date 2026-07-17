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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchingNowMediaCardFactoryTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void accountLabelMovesInlineOnlyWhenTitleRowHasRoom() throws Exception {
        TitleRowSnapshot snapshot = runOnFxThread(() -> {
            WatchingNowMediaCardFactory.CardNodes nodes = WatchingNowMediaCardFactory
                    .builder(WatchingNowMediaCardFactory.CardType.SERIES)
                    .title("EN - You're Killing Me (2026) (CA)")
                    .account("globalgnet.live [ip]")
                    .actionButton(new Button("..."))
                    .build();
            VBox details = nodes.details();
            HBox titleRow = (HBox) details.getChildren().getFirst();
            titleRow.resize(900, 40);
            titleRow.layout();
            boolean accountInlineWhenWide = titleRow.getChildren().contains(nodes.account())
                    && !details.getChildren().contains(nodes.account());

            titleRow.resize(120, 40);
            titleRow.layout();
            return new TitleRowSnapshot(
                    titleRow.getChildren().contains(nodes.title()),
                    accountInlineWhenWide,
                    details.getChildren().contains(nodes.account()),
                    nodes.account().isWrapText(),
                    nodes.account().getTextOverrun()
            );
        });

        assertTrue(snapshot.titleInTitleRow());
        assertTrue(snapshot.accountInlineWhenWide());
        assertTrue(snapshot.accountSeparateWhenNarrow());
        assertTrue(snapshot.accountWrapsWhenSeparate());
        assertEquals(OverrunStyle.ELLIPSIS, snapshot.accountOverrun());
    }

    private record TitleRowSnapshot(
            boolean titleInTitleRow,
            boolean accountInlineWhenWide,
            boolean accountSeparateWhenNarrow,
            boolean accountWrapsWhenSeparate,
            OverrunStyle accountOverrun
    ) {
    }
}
