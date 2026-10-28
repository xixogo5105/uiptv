package com.uiptv.ui;

import com.uiptv.testsupport.*;
import com.uiptv.util.I18n;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardEpgSupportTest extends DbBackedUiTest {
    @BeforeAll static void init() throws Exception { FxTestSupport.initJavaFx(); }
    @Test void plainCardsDoNotLoadGuidesAndExposeAnExplicitContextMenuAction() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (var mode = mockStatic(EpgModeUI.class)) {
                mode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                var owner = new VBox(); var card = new VBox(new Label("Channel"));
                var support = new CardEpgSupport(owner); var requests = new AtomicInteger();
                assertEquals(1, card.getChildren().size()); assertEquals(0, requests.get());
                var menu = new ContextMenu();
                support.addGuideMenu(menu, card, () -> { requests.incrementAndGet(); return null; });
                assertEquals(I18n.tr("epgOpenGuide"),menu.getItems().getFirst().getText());
                assertEquals(0,requests.get()); support.stop();
            }
            return null;
        });
    }
}
