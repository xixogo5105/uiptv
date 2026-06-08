package com.uiptv.ui;

import com.uiptv.model.AccountInfo;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManageAccountInfoPaneTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void profileDataUsesModernSummaryCardAndPopupAction() throws Exception {
        AccountInfoSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountInfoPane pane = new ManageAccountInfoPane();
            AccountInfo info = new AccountInfo();
            info.setExpireDate("2030-01-02 00:00:00");
            info.setProfileJson("""
                    {"profile":{"login":"demo","enabled":true},"settings":{"timezone":"Europe/London"}}
                    """);

            pane.apply(info);

            Button profileButton = field(pane, "accountInfoProfileViewButton", Button.class);
            Label expiryLabel = field(pane, "compactAccountInfoExpireDate", Label.class);
            String formattedText = field(pane, "accountInfoProfileFormattedText", String.class);
            return new AccountInfoSnapshot(
                    pane.getStyleClass().contains("manage-account-info-card"),
                    pane.getCenter() instanceof HBox hBox && hBox.getStyleClass().contains("manage-account-info-compact"),
                    pane.hasSummary(),
                    expiryLabel.getText(),
                    profileButton.isVisible(),
                    profileButton.isManaged(),
                    profileButton.getStyleClass().contains("manage-account-info-profile-button"),
                    profileButton.getText(),
                    formattedText
            );
        });

        assertTrue(snapshot.modernCard());
        assertTrue(snapshot.compactInlineContent());
        assertTrue(snapshot.hasSummary());
        assertTrue(snapshot.expiryText().contains("2030"));
        assertTrue(snapshot.profileButtonVisible());
        assertTrue(snapshot.profileButtonManaged());
        assertTrue(snapshot.profileButtonStyled());
        assertEquals("Profile JSON", snapshot.profileButtonText());
        assertTrue(snapshot.formattedText().contains("profile.login: demo"));
        assertTrue(snapshot.formattedText().contains("settings.timezone: Europe/London"));
    }

    @Test
    void compactSummaryLetsExpiryValueUseRemainingInlineSpace() throws Exception {
        InlineLayoutSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountInfoPane pane = new ManageAccountInfoPane();
            AccountInfo info = new AccountInfo();
            info.setExpireDate("2030-12-31 23:59:59");
            info.setProfileJson("{\"profile\":{\"login\":\"demo\"}}");

            pane.apply(info);

            HBox compactContent = (HBox) pane.getCenter();
            HBox compactExpiry = (HBox) compactContent.getChildren().getFirst();
            Label expiryLabel = field(pane, "compactAccountInfoExpireDate", Label.class);
            return new InlineLayoutSnapshot(
                    compactContent.getChildren().size(),
                    HBox.getHgrow(compactExpiry),
                    expiryLabel.getMinWidth(),
                    expiryLabel.getMaxWidth(),
                    HBox.getHgrow(expiryLabel)
            );
        });

        assertEquals(2, snapshot.topLevelChildCount());
        assertEquals(Priority.ALWAYS, snapshot.expiryGroupGrow());
        assertEquals(0.0, snapshot.expiryValueMinWidth());
        assertEquals(Double.MAX_VALUE, snapshot.expiryValueMaxWidth());
        assertEquals(Priority.ALWAYS, snapshot.expiryValueGrow());
    }

    @Test
    void profileDataPopupTextAreaDoesNotUseTerminalStyle() throws Exception {
        ProfileTextAreaSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountInfoPane pane = new ManageAccountInfoPane();
            TextArea textArea = pane.createProfileTextArea("profile.login: demo");
            return new ProfileTextAreaSnapshot(
                    textArea.getStyleClass().contains("account-info-profile-text-area"),
                    textArea.getStyleClass().contains("terminal"),
                    textArea.isEditable(),
                    textArea.isWrapText()
            );
        });

        assertTrue(snapshot.profileStyle());
        assertFalse(snapshot.terminalStyle());
        assertFalse(snapshot.editable());
        assertFalse(snapshot.wrapText());
    }

    private static <T> T field(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private record AccountInfoSnapshot(
            boolean modernCard,
            boolean compactInlineContent,
            boolean hasSummary,
            String expiryText,
            boolean profileButtonVisible,
            boolean profileButtonManaged,
            boolean profileButtonStyled,
            String profileButtonText,
            String formattedText
    ) {
    }

    private record InlineLayoutSnapshot(
            int topLevelChildCount,
            Priority expiryGroupGrow,
            double expiryValueMinWidth,
            double expiryValueMaxWidth,
            Priority expiryValueGrow
    ) {
    }

    private record ProfileTextAreaSnapshot(
            boolean profileStyle,
            boolean terminalStyle,
            boolean editable,
            boolean wrapText
    ) {
    }
}
