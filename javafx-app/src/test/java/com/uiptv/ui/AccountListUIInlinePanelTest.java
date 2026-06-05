package com.uiptv.ui;

import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.widget.InlinePanelService;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountListUIInlinePanelTest extends DbBackedUiTest {
    private StackPane host;

    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @AfterEach
    void detachAccountUiFromScene() throws Exception {
        if (host == null) {
            return;
        }
        runOnFxThread(() -> {
            host.getChildren().clear();
            return null;
        });
        FxTestSupport.waitForFxEvents();
    }

    @Test
    void temporaryInlinePanelDetachDoesNotClearEmbeddedDetailView() throws Exception {
        AccountListUI accountListUI = runOnFxThread(() -> new AccountListUI(true, null, null));
        Label manageAccountContent = runOnFxThread(() -> new Label("Manage account form"));

        host = runOnFxThread(() -> {
            showDetailView(accountListUI, manageAccountContent);
            StackPane inlineHost = InlinePanelService.createHost(accountListUI);
            InlinePanelService.install(inlineHost);
            new Scene(inlineHost, 900, 600);
            return inlineHost;
        });

        assertTrue(runOnFxThread(() -> detailContent(accountListUI).getChildren().contains(manageAccountContent)));

        InlinePanelService.InlinePanelHandle handle = runOnFxThread(() ->
                InlinePanelService.open("Manage MAC addresses", new Label("MAC inline")).orElseThrow());

        assertTrue(runOnFxThread(() -> InlinePanelService.hasOpenPanel()));
        assertTrue(runOnFxThread(() -> detailContent(accountListUI).getChildren().contains(manageAccountContent)));

        runOnFxThread(() -> {
            handle.close();
            return null;
        });

        assertSame(accountListUI, runOnFxThread(() -> host.getChildren().getFirst()));
        assertTrue(runOnFxThread(() -> detailContent(accountListUI).getChildren().contains(manageAccountContent)));
    }

    private static void showDetailView(AccountListUI accountListUI, Node content) throws Exception {
        Method method = AccountListUI.class.getDeclaredMethod("showDetailView", Node.class);
        method.setAccessible(true);
        method.invoke(accountListUI, content);
    }

    private static VBox detailContent(AccountListUI accountListUI) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("detailContent");
        field.setAccessible(true);
        return (VBox) field.get(accountListUI);
    }
}
