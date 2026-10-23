package com.uiptv.ui;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.widget.SwitchToggle;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.*;
import java.lang.reflect.Field;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationUIEpgTest extends DbBackedUiTest {
    @BeforeAll static void initializeFx() throws Exception { FxTestSupport.initJavaFx(); }
    @Test void settingsLoadSaveAndReloadWithoutErasingMappings() throws Exception {
        Configuration previous = ConfigurationService.getInstance().read();
        previous.setEpgRefreshIntervalHours("6");
        previous.setEpgSchedulerIntervalMinutes("5");
        previous.setEpgEnabled(true); previous.setGlobalEpgSource("/local/guide.xml.gz");
        previous.setEpgMappingsJson("{\"account\":{\"provider-channel\":\"xmltv-channel\"}}");
        ConfigurationService.getInstance().save(previous);
        runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null);
            SwitchToggle enabled = (SwitchToggle) field(ui, "epgEnabledSwitch");
            TextArea source = (TextArea) field(ui, "globalEpgSourceField");
            assertEquals(6, source.getPrefRowCount());
            try {
                var sourceRow = ConfigurationUI.class.getDeclaredMethod("buildEpgSourceRow");
                sourceRow.setAccessible(true);
                var row = (javafx.scene.layout.VBox) sourceRow.invoke(ui);
                assertSame(source, row.getChildren().get(1));
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
            var interval=(javafx.scene.control.ComboBox<Integer>)field(ui,"epgSchedulerIntervalComboBox");
            assertEquals(5,interval.getValue()); assertFalse(interval.isDisabled()); interval.setValue(10);
            assertTrue(ConfigurationUI.themeHelpText().contains(com.uiptv.util.I18n.tr("configEpgSchedulerIntervalHelp")));
            assertTrue(ConfigurationUI.cacheFilteringHelpText().contains(com.uiptv.util.I18n.tr("configEpgSchedulerIntervalHelp")));
            assertTrue(enabled.isSelected()); assertEquals("/local/guide.xml.gz", source.getText());
            enabled.setSelected(false); source.setText("https://guide.example.test/global.xml.gz");
            assertTrue(interval.isDisabled());
            var build = ConfigurationUI.class.getDeclaredMethod("buildConfigurationToSave", Configuration.class); build.setAccessible(true);
            Configuration saved = (Configuration) build.invoke(ui, previous); ConfigurationService.getInstance().save(saved);
            assertEquals(previous.getEpgMappingsJson(), saved.getEpgMappingsJson());
            assertEquals(10,saved.normalizedEpgSchedulerIntervalMinutes());
            ConfigurationUI restarted = new ConfigurationUI(null);
            assertFalse(((SwitchToggle) field(restarted, "epgEnabledSwitch")).isSelected());
            assertEquals(source.getText(), ((TextArea) field(restarted, "globalEpgSourceField")).getText());
            return null;
        });
        assertFalse(ConfigurationService.getInstance().read().isEpgEnabled());
        assertEquals(previous.getEpgMappingsJson(), ConfigurationService.getInstance().read().getEpgMappingsJson());
    }
    @Test void settingsExposeNonDestructiveRefreshAndSeparateCacheMaintenance() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEpgEnabled(true); ConfigurationService.getInstance().save(configuration);
        long refreshedAt = System.currentTimeMillis();
        new com.uiptv.db.EpgCacheDb().replaceSource("settings-retained-source",
                java.util.Map.of("station", java.util.List.of(new com.uiptv.application.EpgProgramme(
                        "Saved show", "", refreshedAt - 60_000, refreshedAt + 3_600_000))),
                java.util.Map.of(), java.util.Map.of(), refreshedAt, refreshedAt + 3_600_000);
        var controls = runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null);
            javafx.scene.control.Button refresh = (javafx.scene.control.Button) field(ui, "refreshEpgButton");
            assertEquals(com.uiptv.util.I18n.tr("epgRefreshGuide"), refresh.getText());
            assertNotNull(refresh.getParent()); assertFalse(refresh.isDisabled());
            javafx.scene.control.Button clear = (javafx.scene.control.Button) field(ui, "clearEpgCacheButton");
            assertNotNull(clear.getParent()); assertFalse(clear.isDisabled());
            return new javafx.scene.control.Button[]{refresh, clear};
        });
        assertEquals(refreshedAt, new com.uiptv.db.EpgCacheDb().sourceState("settings-retained-source").refreshedAt());
        assertEquals(com.uiptv.util.I18n.tr("epgRefreshGuide"), controls[0].getText());
        assertEquals(com.uiptv.util.I18n.tr("configClearCache"), controls[1].getText());
    }

    private Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
}
