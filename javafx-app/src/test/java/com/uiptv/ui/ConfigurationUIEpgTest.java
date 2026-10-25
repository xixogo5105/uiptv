package com.uiptv.ui;

import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationUIEpgTest extends DbBackedUiTest {
    @BeforeAll static void init() throws Exception { FxTestSupport.initJavaFx(); }
    @Test void epgSwitchIsLastInThemeSettingsAndSeparatePanelIsRemoved() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            var ui = new ConfigurationUI(null);
            try {
                var method = ConfigurationUI.class.getDeclaredMethod("buildThemeOverrideGroup"); method.setAccessible(true);
                var group = (javafx.scene.layout.VBox) method.invoke(ui);
                assertTrue(group.getChildren().size() >= 4);
                assertTrue(containsText(group.getChildren().get(3), I18n.tr("configEnableEpg")));
                var sectionsField = ConfigurationUI.class.getDeclaredField("settingsSections"); sectionsField.setAccessible(true);
                var sections = (java.util.List<?>) sectionsField.get(ui);
                assertEquals(7, sections.size());
                assertTrue(sections.stream().noneMatch(section -> {
                    try {
                        var id = section.getClass().getDeclaredMethod("id"); id.setAccessible(true);
                        return "epg".equals(id.invoke(section));
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                }));
                assertThrows(NoSuchMethodException.class, () -> ConfigurationUI.class.getDeclaredMethod("buildEpgSettingsGroup"));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            return null;
        });
    }

    private static boolean containsText(javafx.scene.Node node, String expected) {
        if (node instanceof javafx.scene.control.Labeled labeled && expected.equals(labeled.getText())) return true;
        return node instanceof javafx.scene.Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsText(child, expected));
    }
}
