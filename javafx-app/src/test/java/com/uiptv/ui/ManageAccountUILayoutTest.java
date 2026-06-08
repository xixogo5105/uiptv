package com.uiptv.ui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
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

    private static <T> T field(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private record MacActionRowSnapshot(
            double comboMinWidth,
            double comboPrefWidth,
            double linkFontSize,
            boolean actionsWrappedBelowCombo
    ) {
    }
}
