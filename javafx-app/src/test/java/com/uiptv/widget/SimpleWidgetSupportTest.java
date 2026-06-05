package com.uiptv.widget;

import javafx.scene.text.Font;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleWidgetSupportTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void appFontsLoadEmbeddedRobotoFontsOnlyOnce() throws Exception {
        Field loadedField = AppFonts.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        loadedField.set(null, false);

        runOnFxThread(() -> {
            AppFonts.load();
            AppFonts.load();
            return null;
        });

        assertTrue((Boolean) loadedField.get(null));
        assertTrue(Font.getFamilies().stream().anyMatch(AppFonts.UI_FONT_FAMILY::equalsIgnoreCase));
    }

    @Test
    void appFontsLogsMissingEmbeddedFontWithoutFailing() throws Exception {
        Method loadFont = AppFonts.class.getDeclaredMethod("loadFont", String.class);
        loadFont.setAccessible(true);

        runOnFxThread(() -> {
            loadFont.invoke(null, "/fonts/roboto/not-present.ttf");
            return null;
        });
    }

    @Test
    void dialogAlertReturnsNoInHeadlessMode() {
        String previous = System.getProperty("uiptv.headless");
        try {
            System.setProperty("uiptv.headless", "true");
            assertEquals(javafx.scene.control.ButtonType.NO, DialogAlert.showDialog("commonClose"));
            assertEquals(javafx.scene.control.ButtonType.NO, DialogAlert.showDialog("commonConfirm", "commonClose"));
        } finally {
            if (previous == null) {
                System.clearProperty("uiptv.headless");
            } else {
                System.setProperty("uiptv.headless", previous);
            }
        }
    }

    @Test
    void textAreaAppliesPromptWrapAndColumnCount() throws Exception {
        UIptvTextArea textArea = runOnFxThread(() -> new UIptvTextArea("notes", "commonClose", 28));

        assertEquals("notes", runOnFxThread(textArea::getId));
        assertTrue(runOnFxThread(textArea::isWrapText));
        assertEquals(28, runOnFxThread(textArea::getPrefColumnCount));
        assertEquals(200.0, runOnFxThread(textArea::getHeight));
    }

    @Test
    void searchableTableWithButtonFiltersRowsAndCanDetachToolbar() throws Exception {
        SearchableTableViewWithButton<String> table = runOnFxThread(SearchableTableViewWithButton::new);

        runOnFxThread(() -> {
            TableColumn<String, String> name = new TableColumn<>("Name");
            name.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
            table.getTableView().getColumns().add(name);
            table.getTableView().setItems(FXCollections.observableArrayList("BBC One", "CNN", "BBC Two"));
            table.addTextFilter();
            table.getSearchTextField().setText("bbc");
            return null;
        });

        assertEquals(2, runOnFxThread(() -> table.getTableView().getItems().size()));
        assertEquals("BBC One", runOnFxThread(() -> table.getTableView().getItems().get(0)));

        runOnFxThread(() -> {
            table.detachToolbarControls();
            return null;
        });

        assertNull(runOnFxThread(() -> table.getSearchTextField().getParent()));
        assertNull(runOnFxThread(() -> table.getManageCategoriesButton().getParent()));
        assertFalse(runOnFxThread(() -> table.getChildren().get(0).isManaged()));
        assertFalse(runOnFxThread(() -> table.getChildren().get(0).isVisible()));
    }

    @Test
    void uiptvAlertHeadlessMethodsDoNotCreateDialogs() {
        String previous = System.getProperty("uiptv.headless");
        try {
            System.setProperty("uiptv.headless", "true");
            assertEquals(ButtonBar.ButtonData.OK_DONE, UIptvAlert.okButtonType().getButtonData());
            assertEquals(ButtonBar.ButtonData.CANCEL_CLOSE, UIptvAlert.closeButtonType().getButtonData());
            UIptvAlert.showMessageAlert("commonClose");
            UIptvAlert.showErrorAlert("commonClose");
            UIptvAlert.showErrorAlert("commonClose", new IllegalStateException("boom"));
            UIptvAlert.showMessage("plain message");
            UIptvAlert.showMessageKey("commonClose");
            UIptvAlert.showError("plain error");
            UIptvAlert.showError("plain error", new IllegalStateException("boom"));
            UIptvAlert.showErrorKey("commonClose");
            UIptvAlert.showErrorKey("commonClose", new IllegalStateException("boom"));
            assertFalse(UIptvAlert.showConfirmationAlert("commonClose"));
        } finally {
            if (previous == null) {
                System.clearProperty("uiptv.headless");
            } else {
                System.setProperty("uiptv.headless", previous);
            }
        }
    }

    @Test
    void messageAndErrorAlertsUseNotificationCenterWhenAvailable() throws Exception {
        String previous = System.getProperty("uiptv.headless");
        try {
            System.setProperty("uiptv.headless", "false");
            VBox host = runOnFxThread(AppNotificationCenter::createHost);
            runOnFxThread(() -> {
                AppNotificationCenter.install(host);
                UIptvAlert.showMessageAlert("commonClose");
                UIptvAlert.showMessageAlert("commonClose");
                UIptvAlert.showErrorAlert("commonClose");
                UIptvAlert.showErrorAlert("commonClose");
                return null;
            });
            waitForFxEvents();

            assertEquals(2, runOnFxThread(() -> host.getChildren().size()));
        } finally {
            if (previous == null) {
                System.clearProperty("uiptv.headless");
            } else {
                System.setProperty("uiptv.headless", previous);
            }
        }
    }

    @Test
    void asyncImageViewKeepsDefaultIconWhenUrlIsBlank() throws Exception {
        AsyncImageView imageView = runOnFxThread(AsyncImageView::new);

        runOnFxThread(() -> {
            imageView.loadImage(null, "logo");
            imageView.loadImage("", "logo");
            imageView.clearImage();
            return null;
        });

        assertTrue(runOnFxThread(() -> imageView.getStyleClass().contains(AsyncImageView.IMAGE_VIEW_STYLE_CSS)));
        assertFalse(runOnFxThread(() -> imageView.getStyleClass().contains(AsyncImageView.HAS_IMAGE_STYLE_CSS)));
        assertEquals(2, runOnFxThread(() -> imageView.getChildren().size()));
    }

}
