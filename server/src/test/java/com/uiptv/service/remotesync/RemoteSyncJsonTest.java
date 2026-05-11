package com.uiptv.service.remotesync;

import com.uiptv.service.DatabaseSyncService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncJsonTest {
    @Test
    void requestRoundTrip_preservesDirectionRequesterAndOptions() {
        RemoteSyncRequest request = new RemoteSyncRequest(
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                "4321",
                "living-room",
                new RemoteSyncOptions(true, false)
        );

        String json = RemoteSyncJson.toJson(request);
        RemoteSyncRequest parsed = RemoteSyncJson.toRequest(json);

        assertEquals(request, parsed);
    }

    @Test
    void sessionStateRoundTrip_preservesFields_andHandlesUnknownKeys() {
        RemoteSyncSessionState state = new RemoteSyncSessionState(
                "session-7",
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                RemoteSyncStatus.APPROVED,
                "1234",
                "office",
                "192.168.1.9",
                new RemoteSyncOptions(false, true),
                "Approved."
        );

        String json = RemoteSyncJson.toJson(state);
        JSONObject payload = new JSONObject(json);
        payload.put("extraField", "ignored");

        RemoteSyncSessionState parsed = RemoteSyncJson.toSessionState(payload.toString());

        assertEquals(state, parsed);
    }

    @Test
    void executionResultRoundTrip_preservesReportAndMessage() {
        DatabaseSyncService.DatabaseSyncReport report = new DatabaseSyncService.DatabaseSyncReport(
                List.of(
                        new DatabaseSyncService.TableSyncResult("account", 3),
                        new DatabaseSyncService.TableSyncResult("channel", 11)
                ),
                true,
                true,
                false
        );
        RemoteSyncExecutionResult result = new RemoteSyncExecutionResult(report, "completed");

        String json = RemoteSyncJson.toJson(result);
        RemoteSyncExecutionResult parsed = RemoteSyncJson.toExecutionResult(json);

        assertNotNull(parsed.report());
        assertEquals("completed", parsed.message());
        assertEquals(2, parsed.report().tableResults().size());
        assertEquals("account", parsed.report().tableResults().get(0).tableName());
        assertEquals(3, parsed.report().tableResults().get(0).rowCount());
        assertTrue(parsed.report().isConfigurationRequested());
        assertTrue(parsed.report().isConfigurationCopied());
        assertFalse(parsed.report().isExternalPlayerPathsIncluded());
    }

    @Test
    void executionResultRoundTrip_withoutReport_keepsMessageOnly() {
        RemoteSyncExecutionResult parsed = RemoteSyncJson.toExecutionResult(
                RemoteSyncJson.toJson(new RemoteSyncExecutionResult(null, "nothing to sync"))
        );

        assertNull(parsed.report());
        assertEquals("nothing to sync", parsed.message());
    }

    @Test
    void completeSessionJson_containsExpectedFields() {
        JSONObject json = new JSONObject(RemoteSyncJson.toJson("session-22", true, "done"));

        assertEquals("session-22", json.getString("sessionId"));
        assertTrue(json.getBoolean("success"));
        assertEquals("done", json.getString("message"));
    }
}
