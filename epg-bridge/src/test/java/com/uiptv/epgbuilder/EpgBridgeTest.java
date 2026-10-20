package com.uiptv.epgbuilder;

import com.uiptv.application.EpgBuilderApplicationService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpgBridgeTest {
    @Test
    void rejectsMissingAndMalformedArgumentsWithoutTerminatingTheTestJvm() {
        assertEquals(1, EpgBridge.run(new String[0]));
        assertEquals(1, EpgBridge.run(new String[]{"status", "--unexpected"}));
        assertEquals(1, EpgBridge.run(new String[]{"unknown"}));
    }

    @Test
    void dispatchesStatusAndBundleCommandsToTheApplicationService() throws Exception {
        EpgBuilderApplicationService service = mock(EpgBuilderApplicationService.class);
        when(service.showStatus(org.mockito.ArgumentMatchers.anyString())).thenReturn(new JSONObject().put("status", "ok"));
        when(service.validateBundle(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("bundle.json")))
                .thenReturn(new JSONObject().put("valid", true));
        when(service.importBundle(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("bundle.json")))
                .thenReturn(new JSONObject().put("imported", true));
        when(service.mergeSchedules("bundle.json")).thenReturn(new JSONObject().put("merged", true));
        when(service.parseGuide("bundle.json")).thenReturn(new JSONObject().put("parsed", true));

        assertEquals(0, EpgBridge.run(new String[]{"status"}, service));
        assertEquals(0, EpgBridge.run(new String[]{"validate-bundle", "--bundle", "bundle.json"}, service));
        assertEquals(0, EpgBridge.run(new String[]{"import-bundle", "--bundle", "bundle.json"}, service));
        assertEquals(0, EpgBridge.run(new String[]{"merge-schedules", "--bundle", "bundle.json"}, service));
        assertEquals(0, EpgBridge.run(new String[]{"parse-xmltv", "--bundle", "bundle.json"}, service));
    }
}
