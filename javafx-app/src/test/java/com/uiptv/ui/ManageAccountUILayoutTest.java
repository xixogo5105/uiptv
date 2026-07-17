package com.uiptv.ui;

import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import com.uiptv.widget.DangerousButton;
import com.uiptv.widget.PillBar;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.paint.Color;
import javafx.css.PseudoClass;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManageAccountUILayoutTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void macAddressActionsUseCompactWrappingLayout() throws Exception {
        MacActionRowSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountUI ui = new ManageAccountUI();
            FlowPane row = field(ui, "macAddressContainer", FlowPane.class);
            ComboBox<?> macAddress = field(ui, "macAddress", ComboBox.class);
            Hyperlink verifyLink = field(ui, "verifyMacsLink", Hyperlink.class);

            verifyLink.setVisible(true);
            Scene scene = new Scene(new StackPane(ui), 220, 640);
            scene.getStylesheets().add(Objects.requireNonNull(
                    ManageAccountUILayoutTest.class.getResource("/application.css")
            ).toExternalForm());
            ui.applyCss();
            ui.resize(220, 640);
            ui.layout();

            row.resize(204, 90);
            row.layout();
            Node actionGroup = row.getChildrenUnmodifiable().get(1);

            return new MacActionRowSnapshot(
                    macAddress.getMinWidth(),
                    macAddress.getPrefWidth(),
                    verifyLink.getFont().getSize(),
                    actionGroup.getBoundsInParent().getMinY() > macAddress.getBoundsInParent().getMinY()
            );
        });

        assertTrue(snapshot.comboMinWidth() <= 155);
        assertTrue(snapshot.comboPrefWidth() <= 188);
        assertTrue(snapshot.linkFontSize() < 12.5);
        assertTrue(snapshot.actionsWrappedBelowCombo());
    }

    @Test
    void accountTypeUsesWrappingPillBarAndMacListIsSmaller() throws Exception {
        AccountTypeSelectorSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountUI ui = new ManageAccountUI();
            @SuppressWarnings("unchecked")
            PillBar<AccountType> pillBar = (PillBar<AccountType>) field(ui, "accountTypePillBar", PillBar.class);
            TextArea macAddressList = field(ui, "macAddressList", TextArea.class);
            ManageAccountInfoPane accountInfoPane = field(ui, "accountInfoPane", ManageAccountInfoPane.class);

            return new AccountTypeSelectorSnapshot(
                    pillBar.getStyleClass().contains("manage-account-type-pill-bar"),
                    pillBar.getSelectedItem(),
                    macAddressList.getPrefHeight(),
                    accountInfoPane.isVisible(),
                    accountInfoPane.isManaged()
            );
        });

        assertTrue(snapshot.hasManageAccountPillStyle());
        assertEquals(AccountType.STALKER_PORTAL, snapshot.selectedType());
        assertTrue(snapshot.macListPrefHeight() <= 136);
        assertTrue(!snapshot.accountInfoVisible());
        assertTrue(!snapshot.accountInfoManaged());
    }

    @Test
    void bottomActionsAreCompactAndExcludeHeaderSave() throws Exception {
        BottomActionsSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountUI ui = new ManageAccountUI();
            VBox actionSection = field(ui, "actionSection", VBox.class);
            ComboBox<?> xtremeUsername = field(ui, "xtremeUsername", ComboBox.class);
            Button deleteButton = field(ui, "deleteButton", Button.class);
            Pane actionRow = (Pane) actionSection.getChildrenUnmodifiable().getFirst();
            List<Button> buttons = actionRow.getChildrenUnmodifiable().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .toList();

            return new BottomActionsSnapshot(
                    buttons.stream().map(Button::getText).toList(),
                    buttons.stream().map(Button::getPrefWidth).toList(),
                    xtremeUsername.getPrefWidth(),
                    xtremeUsername.getMaxWidth(),
                    deleteButton.getPrefWidth(),
                    buttons.stream().allMatch(button -> button.getStyleClass().contains("manage-account-compact-action")),
                    ui.getHeaderSaveButton().getStyleClass().contains("manage-account-header-save")
            );
        });

        assertEquals(List.of(I18n.tr("autoReloadCache"), I18n.tr("autoClearData"), I18n.tr("autoDeleteAccount")), snapshot.bottomButtonTexts());
        assertTrue(snapshot.buttonPrefWidths().stream().allMatch(width -> width <= 124));
        assertTrue(snapshot.xtremeUsernamePrefWidth() <= 220);
        assertTrue(snapshot.xtremeUsernameMaxWidth() <= 240);
        assertTrue(snapshot.deleteButtonPrefWidth() >= 120);
        assertTrue(snapshot.bottomActionsCompact());
        assertTrue(snapshot.headerSaveStyled());
    }

    @Test
    void dangerousButtonsKeepDangerColorWhenFocused() throws Exception {
        assertEquals(Color.web("#b91c1c"), focusedDangerousButtonFill("/application.css"));
        assertEquals(Color.web("#dc2626"), focusedDangerousButtonFill("/dark-application.css"));
    }

    @Test
    void localM3uPathAndBrowseShareOneResponsiveRow() throws Exception {
        LocalPathRowSnapshot snapshot = runOnFxThread(() -> {
            ManageAccountUI ui = new ManageAccountUI();
            @SuppressWarnings("unchecked")
            PillBar<AccountType> pillBar = (PillBar<AccountType>) field(ui, "accountTypePillBar", PillBar.class);
            VBox formContainer = field(ui, "formContainer", VBox.class);
            HBox pathBrowserRow = field(ui, "m3u8PathBrowserRow", HBox.class);
            Node pathField = field(ui, "m3u8Path", Node.class);
            Button browseButton = field(ui, "browserButtonM3u8Path", Button.class);

            pillBar.setSelectedItem(AccountType.M3U8_LOCAL);

            return new LocalPathRowSnapshot(
                    formContainer.getChildren().contains(pathBrowserRow),
                    pathBrowserRow.getChildren().contains(pathField),
                    pathBrowserRow.getChildren().contains(browseButton),
                    !formContainer.getChildren().contains(browseButton),
                    formContainer.getSpacing()
            );
        });

        assertTrue(snapshot.rowInForm());
        assertTrue(snapshot.pathFieldInRow());
        assertTrue(snapshot.browseButtonInRow());
        assertTrue(snapshot.browseButtonNotSeparateFormChild());
        assertTrue(snapshot.formSpacing() >= 6);
    }

    private static <T> T field(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static Color focusedDangerousButtonFill(String stylesheetResource) throws Exception {
        return runOnFxThread(() -> {
            Button button = new DangerousButton("Delete");
            StackPane root = new StackPane(button);
            Scene scene = new Scene(root, 160, 48);
            scene.getStylesheets().add(Objects.requireNonNull(
                    ManageAccountUILayoutTest.class.getResource(stylesheetResource)
            ).toExternalForm());
            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            root.applyCss();
            return (Color) button.getBackground().getFills().getFirst().getFill();
        });
    }

    private record MacActionRowSnapshot(
            double comboMinWidth,
            double comboPrefWidth,
            double linkFontSize,
            boolean actionsWrappedBelowCombo
    ) {
    }

    private record AccountTypeSelectorSnapshot(
            boolean hasManageAccountPillStyle,
            AccountType selectedType,
            double macListPrefHeight,
            boolean accountInfoVisible,
            boolean accountInfoManaged
    ) {
    }

    private record BottomActionsSnapshot(
            List<String> bottomButtonTexts,
            List<Double> buttonPrefWidths,
            double xtremeUsernamePrefWidth,
            double xtremeUsernameMaxWidth,
            double deleteButtonPrefWidth,
            boolean bottomActionsCompact,
            boolean headerSaveStyled
    ) {
    }

    private record LocalPathRowSnapshot(
            boolean rowInForm,
            boolean pathFieldInRow,
            boolean browseButtonInRow,
            boolean browseButtonNotSeparateFormChild,
            double formSpacing
    ) {
    }
}
