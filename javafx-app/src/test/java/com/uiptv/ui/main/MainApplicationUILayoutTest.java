package com.uiptv.ui.main;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.AccountListUI;
import com.uiptv.ui.ManageAccountUI;
import com.uiptv.util.I18n;
import com.uiptv.widget.AppNavigationController;
import com.uiptv.widget.AppNavigationPane;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainApplicationUILayoutTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void wideModeLeavesSlightlyMoreSpaceForPlayerOnLargeScreens() throws Exception {
        assertEquals(518.4, preferredWideAppAreaWidth(1920), 0.001);
    }

    @Test
    void wideModeAppAreaStillHasStableBounds() throws Exception {
        assertEquals(540.0, preferredWideAppAreaWidth(2560), 0.001);
        assertEquals(360.0, preferredWideAppAreaWidth(1200), 0.001);
    }

    @Test
    void wideEmbeddedLayoutReservesFixedNavigationColumn() throws Exception {
        runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1920, 720, true);
            TabPane tabPane = new TabPane();
            StackPane navigationShell = new StackPane(tabPane);
            HBox embeddedPlayer = new HBox();
            VBox playerAdjacentControls = new VBox();
            GridPane responsiveContent = new GridPane();
            HBox mainContent = new HBox(responsiveContent);
            responsiveContent.getChildren().setAll(navigationShell, embeddedPlayer, playerAdjacentControls);
            new Scene(mainContent, 1920, 600);
            mainContent.resize(1920, 600);
            responsiveContent.resize(1920, 600);
            setField(ui, "activeTabPane", tabPane);
            setField(ui, "navigationShell", navigationShell);
            setField(ui, "embeddedPlayer", embeddedPlayer);
            setField(ui, "playerAdjacentControls", playerAdjacentControls);
            setField(ui, "responsiveContent", responsiveContent);
            setField(ui, "mainContent", mainContent);

            Method method = MainApplicationUI.class.getDeclaredMethod("applyWideEmbeddedLayout");
            method.setAccessible(true);
            method.invoke(ui);

            assertEquals(2, responsiveContent.getColumnConstraints().size());
            ColumnConstraints navigationColumn = responsiveContent.getColumnConstraints().getFirst();
            double expectedWidth = 518.4;
            assertEquals(0, GridPane.getColumnIndex(navigationShell));
            assertEquals(1, GridPane.getColumnIndex(embeddedPlayer));
            assertEquals(expectedWidth, navigationColumn.getMinWidth(), 0.001);
            assertEquals(expectedWidth, navigationColumn.getPrefWidth(), 0.001);
            assertEquals(expectedWidth, navigationColumn.getMaxWidth(), 0.001);
            assertEquals(Priority.NEVER, navigationColumn.getHgrow());
            return null;
        });
    }

    @Test
    void playerAdjacentTopControlsLayoutActivatesOnlyWhenThereIsRoomBesidePlayer() throws Exception {
        assertFalse(shouldUsePlayerAdjacentTopControlsLayout(480));
        assertFalse(shouldUsePlayerAdjacentTopControlsLayout(827));
        assertFalse(shouldUsePlayerAdjacentTopControlsLayout(1199));
        assertTrue(shouldUsePlayerAdjacentTopControlsLayout(1200));
        assertTrue(shouldUsePlayerAdjacentTopControlsLayout(1368));
    }

    @Test
    void accountMediaDrawerModeActivatesOnlyWhenTopPlayerWidthCannotFitSplitBrowser() throws Exception {
        assertTrue(shouldUseAccountMediaDrawerMode(480));
        assertTrue(shouldUseAccountMediaDrawerMode(899));
        assertFalse(shouldUseAccountMediaDrawerMode(900));
        assertFalse(shouldUseAccountMediaDrawerMode(1200));
    }

    @Test
    void playerAdjacentTopControlsLayoutUsesSceneWidthWhenContentIsClipped() throws Exception {
        assertFalse(runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, true);
            HBox mainContent = new HBox();
            mainContent.resize(1200, 600);
            new Scene(mainContent, 700, 600);
            setField(ui, "mainContent", mainContent);
            Method method = MainApplicationUI.class.getDeclaredMethod("shouldUsePlayerAdjacentTopControlsLayout");
            method.setAccessible(true);
            return (boolean) method.invoke(ui);
        }));
    }

    @Test
    void playerAdjacentTopControlsLayoutUsesResponsiveContentWidthWhenSceneIsCurrent() throws Exception {
        assertFalse(runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, true);
            GridPane responsiveContent = new GridPane();
            HBox mainContent = new HBox(responsiveContent);
            new Scene(mainContent, 1280, 600);
            mainContent.resize(1280, 600);
            responsiveContent.resize(1180, 600);
            setField(ui, "mainContent", mainContent);
            setField(ui, "responsiveContent", responsiveContent);
            Method method = MainApplicationUI.class.getDeclaredMethod("shouldUsePlayerAdjacentTopControlsLayout");
            method.setAccessible(true);
            return (boolean) method.invoke(ui);
        }));
    }

    @Test
    void playerAdjacentTopControlsLayoutIgnoresStaleNarrowGridAfterLargeSceneExpansion() throws Exception {
        assertTrue(runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, true);
            GridPane responsiveContent = new GridPane();
            HBox mainContent = new HBox(responsiveContent);
            new Scene(mainContent, 1920, 600);
            mainContent.resize(544, 600);
            responsiveContent.resize(544, 600);
            setField(ui, "mainContent", mainContent);
            setField(ui, "responsiveContent", responsiveContent);
            Method method = MainApplicationUI.class.getDeclaredMethod("shouldUsePlayerAdjacentTopControlsLayout");
            method.setAccessible(true);
            return (boolean) method.invoke(ui);
        }));
    }

    @Test
    void playerAdjacentLayoutReservesFixedPlayerColumnAndFullWidthNavigationRow() throws Exception {
        assertTrue(runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, true);
            TabPane tabPane = new TabPane();
            StackPane navigationShell = new StackPane(tabPane);
            HBox embeddedPlayer = new HBox();
            VBox playerAdjacentControls = new VBox();
            embeddedPlayer.setManaged(true);
            GridPane responsiveContent = new GridPane();
            HBox mainContent = new HBox(responsiveContent);
            new Scene(mainContent, 1368, 600);
            mainContent.resize(1368, 600);
            responsiveContent.resize(1368, 600);
            setField(ui, "activeTabPane", tabPane);
            setField(ui, "navigationShell", navigationShell);
            setField(ui, "embeddedPlayer", embeddedPlayer);
            setField(ui, "playerAdjacentControls", playerAdjacentControls);
            setField(ui, "responsiveContent", responsiveContent);
            setField(ui, "mainContent", mainContent);
            Method method = MainApplicationUI.class.getDeclaredMethod("applyPlayerAdjacentTopControlsEmbeddedArrangement");
            method.setAccessible(true);
            method.invoke(ui);
            if (responsiveContent.getColumnConstraints().size() != 2) {
                return false;
            }
            ColumnConstraints playerColumn = responsiveContent.getColumnConstraints().getFirst();
            ColumnConstraints controlsColumn = responsiveContent.getColumnConstraints().get(1);
            return GridPane.getColumnSpan(navigationShell) == 2
                    && playerColumn.getMinWidth() == 480.0
                    && playerColumn.getPrefWidth() == 480.0
                    && playerColumn.getMaxWidth() == 480.0
                    && playerColumn.getHgrow() == Priority.NEVER
                    && controlsColumn.getHgrow() == Priority.ALWAYS;
        }));
    }

    @Test
    void topMenuContainsHelpActionsOnly() throws Exception {
        runOnFxThread(() -> {
            Stage stage = Mockito.mock(Stage.class);
            ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
            Mockito.when(configurationService.read()).thenReturn(new Configuration());
            MainApplicationUI ui = new MainApplicationUI(stage, null, configurationService, _ -> {
            }, 1368, 720, false);

            MenuBar menuBar = createMenuBar(ui);
            List<String> labels = menuBar.getMenus().getFirst().getItems().stream()
                    .map(MenuItem::getText)
                    .toList();
            assertEquals(List.of(
                    I18n.tr("autoAbout"),
                    I18n.tr("autoCheckForUpdates2")
            ), labels);
            return null;
        });
    }

    @Test
    void manageAccountDockStaysOutOfTopLevelAppContent() throws Exception {
        runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, false);
            Object manageAccountColumn = createManageAccountColumn();
            Node dockNode = manageAccountColumnNode(manageAccountColumn);
            AccountListUI accountListUI = new AccountListUI(null, null);
            accountListUI.setLeadingBodyContent(dockNode);
            HBox mainContent = new HBox(new Label("Accounts"));

            HBox appContent = buildAppContent(ui, manageAccountColumn, mainContent, accountListUI);

            assertEquals(mainContent, appContent.getChildren().getFirst());
            assertFalse(appContent.getChildren().contains(dockNode));
            assertFalse(dockNode.isVisible());
            assertFalse(dockNode.isManaged());

            openManageAccountColumn(manageAccountColumn);

            assertTrue(dockNode.isVisible());
            assertTrue(dockNode.isManaged());
            return null;
        });
    }

    @Test
    void manageAccountDockHeaderPlacesSaveBesideClose() throws Exception {
        HeaderButtonsSnapshot snapshot = runOnFxThread(() -> {
            Object manageAccountColumn = createManageAccountColumn();
            VBox dock = (VBox) manageAccountColumnNode(manageAccountColumn);
            HBox header = (HBox) dock.getChildrenUnmodifiable().getFirst();
            List<Button> buttons = header.getChildrenUnmodifiable().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .toList();
            return new HeaderButtonsSnapshot(
                    buttons.stream().map(Button::getText).toList(),
                    buttons.stream().map(button -> List.copyOf(button.getStyleClass())).toList()
            );
        });

        assertEquals(List.of(I18n.tr("commonSave"), I18n.tr("commonClose")), snapshot.texts());
        assertTrue(snapshot.styleClasses().getFirst().contains("manage-account-dock-save"));
        assertTrue(snapshot.styleClasses().get(1).contains("manage-account-dock-close"));
    }

    @Test
    void manageAccountDockReplacesAccountBodyWhenWidthIsLimited() throws Exception {
        runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, false);
            Object manageAccountColumn = createManageAccountColumn();
            Node dockNode = manageAccountColumnNode(manageAccountColumn);
            AccountListUI accountListUI = new AccountListUI(null, null);
            accountListUI.setLeadingBodyContent(dockNode);
            HBox mainContent = new HBox(accountListUI);
            HBox appContent = buildAppContent(ui, manageAccountColumn, mainContent, accountListUI);
            appContent.resize(700, 600);

            openManageAccountColumn(manageAccountColumn);
            updateManageAccountResponsiveColumns(ui, appContent, manageAccountColumn, accountListUI);

            assertTrue(dockNode.isVisible());
            assertTrue(dockNode.isManaged());
            assertTrue(mainContent.isVisible());
            assertTrue(mainContent.isManaged());
            assertEquals(List.of(dockNode), showingBodyColumns(accountListUI));

            closeManageAccountColumn(manageAccountColumn);
            updateManageAccountResponsiveColumns(ui, appContent, manageAccountColumn, accountListUI);

            assertTrue(mainContent.isVisible());
            assertTrue(mainContent.isManaged());
            assertFalse(dockNode.isVisible());
            assertFalse(dockNode.isManaged());
            assertEquals(1, showingBodyColumns(accountListUI).size());
            assertFalse(showingBodyColumns(accountListUI).contains(dockNode));
            return null;
        });
    }

    @Test
    void manageAccountDockReplacesAccountBodyInWideModeAtLargeWidth() throws Exception {
        runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1920, 720, true);
            setField(ui, "wideEmbeddedLayoutActive", true);
            Object manageAccountColumn = createManageAccountColumn();
            Node dockNode = manageAccountColumnNode(manageAccountColumn);
            AccountListUI accountListUI = new AccountListUI(null, null);
            accountListUI.setLeadingBodyContent(dockNode);
            HBox mainContent = new HBox(accountListUI);
            HBox appContent = buildAppContent(ui, manageAccountColumn, mainContent, accountListUI);
            appContent.resize(1920, 600);

            openManageAccountColumn(manageAccountColumn);
            updateManageAccountResponsiveColumns(ui, appContent, manageAccountColumn, accountListUI);

            assertEquals(List.of(dockNode), showingBodyColumns(accountListUI));

            closeManageAccountColumn(manageAccountColumn);
            updateManageAccountResponsiveColumns(ui, appContent, manageAccountColumn, accountListUI);

            assertFalse(dockNode.isVisible());
            assertFalse(dockNode.isManaged());
            assertEquals(1, showingBodyColumns(accountListUI).size());
            assertFalse(showingBodyColumns(accountListUI).contains(dockNode));
            return null;
        });
    }

    @Test
    void accountsNavigationDoesNotOpenManageAccountDock() throws Exception {
        runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, false);
            AppNavigationPane tabPane = new AppNavigationPane();
            Tab settingsTab = new Tab("Settings");
            Tab accountsTab = new Tab("Accounts");
            Tab importTab = new Tab("Import");
            Tab logsTab = new Tab("Logs");
            Tab watchingNowTab = new Tab("Watching");
            Tab bookmarksTab = new Tab("Bookmarks");
            tabPane.getTabs().addAll(settingsTab, accountsTab, importTab, logsTab, watchingNowTab, bookmarksTab);

            Object manageAccountColumn = createManageAccountColumn();
            Node dockNode = manageAccountColumnNode(manageAccountColumn);

            configureAppNavigation(
                    ui,
                    tabPane,
                    settingsTab,
                    accountsTab,
                    importTab,
                    logsTab,
                    watchingNowTab,
                    bookmarksTab,
                    manageAccountColumn
            );

            AppNavigationController.navigate(AppNavigationController.Target.ACCOUNTS);

            assertEquals(accountsTab, tabPane.getSelectionModel().getSelectedItem());
            assertFalse(dockNode.isVisible());
            assertFalse(dockNode.isManaged());
            AppNavigationController.reset();
            return null;
        });
    }

    @Test
    void stackedEmbeddedLayoutKeepsPositivePlayerRowHeight() throws Exception {
        assertTrue(runOnFxThread(() -> {
            MainApplicationUI ui = new MainApplicationUI(null, null, null, null, 1368, 720, true);
            HBox mainContent = new HBox();
            GridPane responsiveContent = new GridPane();
            mainContent.getChildren().add(responsiveContent);
            new Scene(mainContent, 700, 600);
            setField(ui, "mainContent", mainContent);
            setField(ui, "responsiveContent", responsiveContent);
            Method method = MainApplicationUI.class.getDeclaredMethod("configureStackedResponsiveGrid");
            method.setAccessible(true);
            method.invoke(ui);
            if (responsiveContent.getRowConstraints().size() != 2) {
                return false;
            }
            RowConstraints playerRow = responsiveContent.getRowConstraints().getFirst();
            Method widthMethod = MainApplicationUI.class.getDeclaredMethod("stackedEmbeddedPlayerWidth");
            widthMethod.setAccessible(true);
            return playerRow.getPrefHeight() >= 210
                    && playerRow.getPrefHeight() <= 305
                    && playerRow.getMinHeight() == playerRow.getPrefHeight()
                    && playerRow.getMaxHeight() == playerRow.getPrefHeight()
                    && (double) widthMethod.invoke(ui) == 480;
        }));
    }

    private static double preferredWideAppAreaWidth(int guidedWidth) throws Exception {
        MainApplicationUI ui = new MainApplicationUI(null, null, null, null, guidedWidth, 720, true);
        Method method = MainApplicationUI.class.getDeclaredMethod("preferredWideAppAreaWidth");
        method.setAccessible(true);
        return (double) method.invoke(ui);
    }

    private static boolean shouldUsePlayerAdjacentTopControlsLayout(int guidedWidth) throws Exception {
        MainApplicationUI ui = new MainApplicationUI(null, null, null, null, guidedWidth, 720, true);
        Method method = MainApplicationUI.class.getDeclaredMethod("shouldUsePlayerAdjacentTopControlsLayout");
        method.setAccessible(true);
        return (boolean) method.invoke(ui);
    }

    private static boolean shouldUseAccountMediaDrawerMode(int guidedWidth) throws Exception {
        MainApplicationUI ui = new MainApplicationUI(null, null, null, null, guidedWidth, 720, true);
        Method method = MainApplicationUI.class.getDeclaredMethod("shouldUseAccountMediaDrawerMode");
        method.setAccessible(true);
        return (boolean) method.invoke(ui);
    }

    private static MenuBar createMenuBar(MainApplicationUI ui) throws Exception {
        Method method = BaseMainApplicationUI.class.getDeclaredMethod("createMenuBar");
        method.setAccessible(true);
        return (MenuBar) method.invoke(ui);
    }

    private static HBox buildAppContent(MainApplicationUI ui,
                                        Object manageAccountColumn,
                                        HBox mainContent,
                                        AccountListUI accountListUI) throws Exception {
        Method method = BaseMainApplicationUI.class.getDeclaredMethod(
                "buildAppContent",
                manageAccountColumnClass(),
                HBox.class,
                AccountListUI.class
        );
        method.setAccessible(true);
        return (HBox) method.invoke(ui, manageAccountColumn, mainContent, accountListUI);
    }

    private static void updateManageAccountResponsiveColumns(MainApplicationUI ui,
                                                             HBox appContent,
                                                             Object manageAccountColumn,
                                                             AccountListUI accountListUI) throws Exception {
        Method method = BaseMainApplicationUI.class.getDeclaredMethod(
                "updateManageAccountResponsiveColumns",
                HBox.class,
                manageAccountColumnClass(),
                AccountListUI.class
        );
        method.setAccessible(true);
        method.invoke(ui, appContent, manageAccountColumn, accountListUI);
    }

    private static void configureAppNavigation(MainApplicationUI ui,
                                               TabPane tabPane,
                                               Tab configurationTab,
                                               Tab manageAccountTab,
                                               Tab parseMultipleAccountTab,
                                               Tab logDisplayTab,
                                               Tab watchingNowTab,
                                               Tab bookmarkChannelListTab,
                                               Object manageAccountColumn) throws Exception {
        Method method = BaseMainApplicationUI.class.getDeclaredMethod(
                "configureAppNavigation",
                TabPane.class,
                Tab.class,
                Tab.class,
                Tab.class,
                Tab.class,
                Tab.class,
                Tab.class,
                manageAccountColumnClass()
        );
        method.setAccessible(true);
        method.invoke(
                ui,
                tabPane,
                configurationTab,
                manageAccountTab,
                parseMultipleAccountTab,
                logDisplayTab,
                watchingNowTab,
                bookmarkChannelListTab,
                manageAccountColumn
        );
    }

    private static Class<?> manageAccountColumnClass() throws ClassNotFoundException {
        return Class.forName("com.uiptv.ui.main.BaseMainApplicationUI$ManageAccountColumn");
    }

    private static Object createManageAccountColumn() throws Exception {
        var constructor = manageAccountColumnClass().getDeclaredConstructor(ManageAccountUI.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new ManageAccountUI());
    }

    private static Node manageAccountColumnNode(Object manageAccountColumn) throws Exception {
        Method method = manageAccountColumnClass().getDeclaredMethod("node");
        method.setAccessible(true);
        return (Node) method.invoke(manageAccountColumn);
    }

    private static void openManageAccountColumn(Object manageAccountColumn) throws Exception {
        Method method = manageAccountColumnClass().getDeclaredMethod("open");
        method.setAccessible(true);
        method.invoke(manageAccountColumn);
    }

    private static void closeManageAccountColumn(Object manageAccountColumn) throws Exception {
        Method method = manageAccountColumnClass().getDeclaredMethod("close");
        method.setAccessible(true);
        method.invoke(manageAccountColumn);
    }

    private static void setField(MainApplicationUI ui, String fieldName, Object value) throws Exception {
        Field field = MainApplicationUI.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(ui, value);
    }

    private static HBox accountBodyLayout(AccountListUI accountListUI) throws Exception {
        Field field = AccountListUI.class.getDeclaredField("bodyLayout");
        field.setAccessible(true);
        return (HBox) field.get(accountListUI);
    }

    private static List<Node> showingBodyColumns(AccountListUI accountListUI) throws Exception {
        return accountBodyLayout(accountListUI).getChildren().stream()
                .filter(node -> node.isVisible() && node.isManaged())
                .toList();
    }

    private record HeaderButtonsSnapshot(List<String> texts, List<List<String>> styleClasses) {
    }
}
