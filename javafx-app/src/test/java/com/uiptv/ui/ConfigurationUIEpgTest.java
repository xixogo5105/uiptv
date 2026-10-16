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
        previous.setEpgEnabled(true); previous.setGlobalEpgSource("/local/guide.xml.gz");
        previous.setEpgMappingsJson("{\"account\":{\"provider-channel\":\"xmltv-channel\"}}");
        ConfigurationService.getInstance().save(previous);
        runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null);
            SwitchToggle enabled = (SwitchToggle) field(ui, "epgEnabledSwitch");
            TextArea source = (TextArea) field(ui, "globalEpgSourceField");
            var interval=(javafx.scene.control.ComboBox<Integer>)field(ui,"epgRefreshIntervalComboBox");
            assertEquals(6,interval.getValue()); assertFalse(interval.isDisabled()); interval.setValue(4);
            assertTrue(ConfigurationUI.themeHelpText().contains(com.uiptv.util.I18n.tr("configEpgRefreshIntervalHelp")));
            assertTrue(ConfigurationUI.cacheFilteringHelpText().contains(com.uiptv.util.I18n.tr("configEpgRefreshIntervalHelp")));
            assertTrue(enabled.isSelected()); assertEquals("/local/guide.xml.gz", source.getText());
            enabled.setSelected(false); source.setText("https://guide.example.test/global.xml.gz");
            assertTrue(interval.isDisabled());
            var build = ConfigurationUI.class.getDeclaredMethod("buildConfigurationToSave", Configuration.class); build.setAccessible(true);
            Configuration saved = (Configuration) build.invoke(ui, previous); ConfigurationService.getInstance().save(saved);
            assertEquals(previous.getEpgMappingsJson(), saved.getEpgMappingsJson());
            assertEquals(4,saved.normalizedEpgRefreshHours());
            ConfigurationUI restarted = new ConfigurationUI(null);
            assertFalse(((SwitchToggle) field(restarted, "epgEnabledSwitch")).isSelected());
            assertEquals(source.getText(), ((TextArea) field(restarted, "globalEpgSourceField")).getText());
            return null;
        });
        assertFalse(ConfigurationService.getInstance().read().isEpgEnabled());
        assertEquals(previous.getEpgMappingsJson(), ConfigurationService.getInstance().read().getEpgMappingsJson());
    }
    @Test void settingsRefreshClearsGuideCacheWithoutFetchingSources() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setEpgEnabled(true); ConfigurationService.getInstance().save(configuration);
        var account = new com.uiptv.model.Account(); account.setType(com.uiptv.util.AccountType.M3U8_LOCAL);
        account.setAction(com.uiptv.model.Account.AccountAction.itv); account.setDbId("settings-refresh");
        var source = tempDir.resolve("refresh-guide.xml"); java.nio.file.Files.writeString(source, "<tv><programme channel='station' start='" + java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now().minusSeconds(60)) + "' stop='" + java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now().plusSeconds(3600)) + "'><title>Current show</title></programme></tv>"); account.setEpg(source.toString());
        var channel = new com.uiptv.model.Channel(); channel.setChannelId("station"); channel.setName("Station");
        new com.uiptv.application.EpgCacheRefreshService().refreshAccount(account, java.util.List.of(channel));
        var cache = new com.uiptv.application.EpgCacheApplicationService();
        assertFalse(cache.schedule(account, channel).isEmpty());
        var done = new java.util.concurrent.CountDownLatch(1);
        var cacheChanged = new java.util.concurrent.CountDownLatch(1);
        var revisionField = EpgModeUI.class.getDeclaredField("cacheRevision"); revisionField.setAccessible(true);
        var revision = (javafx.beans.property.LongProperty) revisionField.get(null);
        javafx.beans.value.ChangeListener<Number> listener = (_, _, _) -> cacheChanged.countDown();
        javafx.scene.control.Button button = runOnFxThread(() -> {
            ConfigurationUI ui = new ConfigurationUI(null);
            javafx.scene.control.Button refresh = (javafx.scene.control.Button) field(ui, "refreshEpgButton");
            assertEquals(com.uiptv.util.I18n.tr("epgRefreshGuide"), refresh.getText());
            assertNotNull(refresh.getParent());
            // Cache invalidation now publishes progress before the refresh worker finishes.
            // The button becoming enabled is the completion signal; a cache event alone is not.
            refresh.disabledProperty().addListener((_, wasDisabled, isDisabled) -> {
                if (wasDisabled && !isDisabled) done.countDown();
            });
            revision.addListener(listener); refresh.fire(); return refresh;
        });
        try {
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(cacheChanged.await(5, java.util.concurrent.TimeUnit.SECONDS));
            runOnFxThread(() -> { assertFalse(button.isDisabled()); return null; });
            assertTrue(cache.schedule(account, channel).isEmpty());
        } finally { runOnFxThread(() -> { revision.removeListener(listener); return null; }); }
    }

    private Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
}
