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
        previous.setEpgEnabled(true); previous.setGlobalEpgSource("/local/guide.xml.gz");
        previous.setEpgMappingsJson("{\"account\":{\"provider-channel\":\"xmltv-channel\"}}");
        ConfigurationService.getInstance().save(previous);
        runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null);
            SwitchToggle enabled = (SwitchToggle) field(ui, "epgEnabledSwitch");
            TextArea source = (TextArea) field(ui, "globalEpgSourceField");
            assertTrue(enabled.isSelected()); assertEquals("/local/guide.xml.gz", source.getText());
            enabled.setSelected(false); source.setText("https://guide.example.test/global.xml.gz");
            var build = ConfigurationUI.class.getDeclaredMethod("buildConfigurationToSave", Configuration.class); build.setAccessible(true);
            Configuration saved = (Configuration) build.invoke(ui, previous); ConfigurationService.getInstance().save(saved);
            assertEquals(previous.getEpgMappingsJson(), saved.getEpgMappingsJson());
            ConfigurationUI restarted = new ConfigurationUI(null);
            assertFalse(((SwitchToggle) field(restarted, "epgEnabledSwitch")).isSelected());
            assertEquals(source.getText(), ((TextArea) field(restarted, "globalEpgSourceField")).getText());
            return null;
        });
        assertFalse(ConfigurationService.getInstance().read().isEpgEnabled());
        assertEquals(previous.getEpgMappingsJson(), ConfigurationService.getInstance().read().getEpgMappingsJson());
    }
    private Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
}
