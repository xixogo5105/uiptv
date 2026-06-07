package com.uiptv.ui.main;

import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    private static void setField(MainApplicationUI ui, String fieldName, Object value) throws Exception {
        Field field = MainApplicationUI.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(ui, value);
    }
}
