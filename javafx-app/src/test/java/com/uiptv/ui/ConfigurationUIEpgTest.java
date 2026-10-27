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
                assertEquals(6, sections.size());
                assertTrue(sections.stream().noneMatch(section -> "epg".equals(sectionId(section))));
                assertTrue(sections.stream().noneMatch(section -> "cache".equals(sectionId(section))));
                for (Object section : sections) {
                    var helpLinkMethod = section.getClass().getDeclaredMethod("helpLink"); helpLinkMethod.setAccessible(true);
                    var helpLink = (javafx.scene.control.Hyperlink) helpLinkMethod.invoke(section);
                    assertNotNull(helpLink);
                    assertNotNull(helpLink.getOnAction());
                }
                var appearanceSection = sections.stream()
                        .filter(section -> "appearance".equals(sectionId(section)))
                        .findFirst()
                        .orElseThrow();
                var contentMethod = appearanceSection.getClass().getDeclaredMethod("content"); contentMethod.setAccessible(true);
                var searchTextMethod = appearanceSection.getClass().getDeclaredMethod("searchText"); searchTextMethod.setAccessible(true);
                var appearanceContent = (javafx.scene.Node) contentMethod.invoke(appearanceSection);
                assertTrue(((String) searchTextMethod.invoke(appearanceSection)).contains("cache"));
                assertTrue(containsText(appearanceContent, I18n.tr("configCacheExpiresInDays")));
                assertTrue(containsText(appearanceContent, I18n.tr("configClearMenu")));
                assertThrows(NoSuchMethodException.class, () -> ConfigurationUI.class.getDeclaredMethod("buildEpgSettingsGroup"));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            return null;
        });
    }

    private static String sectionId(Object section) {
        try {
            var id = section.getClass().getDeclaredMethod("id"); id.setAccessible(true);
            return (String) id.invoke(section);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static boolean containsText(javafx.scene.Node node, String expected) {
        if (node instanceof javafx.scene.control.Labeled labeled && expected.equals(labeled.getText())) return true;
        return node instanceof javafx.scene.Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsText(child, expected));
    }
}
