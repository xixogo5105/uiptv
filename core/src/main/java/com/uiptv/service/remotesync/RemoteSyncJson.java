package com.uiptv.service.remotesync;

import com.uiptv.service.DatabaseSyncService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RemoteSyncJson {
    private static final String DIRECTION = "direction";
    private static final String VERIFICATION_CODE = "verificationCode";
    private static final String REQUESTER_NAME = "requesterName";
    private static final String SYNC_CONFIGURATION = "syncConfiguration";
    private static final String SYNC_EXTERNAL_PLAYER_PATHS = "syncExternalPlayerPaths";
    private static final String MESSAGE = "message";

    private RemoteSyncJson() {
    }

    public static JSONObject toJson(RemoteSyncRequest request) {
        return new JSONObject()
                .put(DIRECTION, request.direction().name())
                .put(VERIFICATION_CODE, request.verificationCode())
                .put(REQUESTER_NAME, request.requesterName())
                .put(SYNC_CONFIGURATION, request.options().syncConfiguration())
                .put(SYNC_EXTERNAL_PLAYER_PATHS, request.options().syncExternalPlayerPaths());
    }

    public static RemoteSyncRequest toRequest(JSONObject json) {
        return new RemoteSyncRequest(
                RemoteSyncDirection.valueOf(json.optString(DIRECTION, RemoteSyncDirection.EXPORT_TO_REMOTE.name())),
                json.optString(VERIFICATION_CODE, ""),
                json.optString(REQUESTER_NAME, ""),
                new RemoteSyncOptions(
                        json.optBoolean(SYNC_CONFIGURATION, false),
                        json.optBoolean(SYNC_EXTERNAL_PLAYER_PATHS, false)
                )
        );
    }

    public static JSONObject toJson(RemoteSyncSessionState state) {
        return new JSONObject()
                .put("sessionId", state.sessionId())
                .put(DIRECTION, state.direction().name())
                .put("status", state.status().name())
                .put(VERIFICATION_CODE, state.verificationCode())
                .put(REQUESTER_NAME, state.requesterName())
                .put("requesterAddress", state.requesterAddress())
                .put(SYNC_CONFIGURATION, state.options().syncConfiguration())
                .put(SYNC_EXTERNAL_PLAYER_PATHS, state.options().syncExternalPlayerPaths())
                .put(MESSAGE, state.message());
    }

    public static RemoteSyncSessionState toSessionState(JSONObject json) {
        return new RemoteSyncSessionState(
                json.optString("sessionId", ""),
                RemoteSyncDirection.valueOf(json.optString(DIRECTION, RemoteSyncDirection.EXPORT_TO_REMOTE.name())),
                RemoteSyncStatus.valueOf(json.optString("status", RemoteSyncStatus.FAILED.name())),
                json.optString(VERIFICATION_CODE, ""),
                json.optString(REQUESTER_NAME, ""),
                json.optString("requesterAddress", ""),
                new RemoteSyncOptions(
                        json.optBoolean(SYNC_CONFIGURATION, false),
                        json.optBoolean(SYNC_EXTERNAL_PLAYER_PATHS, false)
                ),
                json.optString(MESSAGE, "")
        );
    }

    public static JSONObject toJson(RemoteSyncExecutionResult result) {
        JSONObject json = new JSONObject().put(MESSAGE, result.message());
        DatabaseSyncService.DatabaseSyncReport report = result.report();
        if (report == null) {
            return json;
        }
        JSONArray tables = new JSONArray();
        for (DatabaseSyncService.TableSyncResult table : report.getTableResults()) {
            tables.put(new JSONObject()
                    .put("tableName", table.getTableName())
                    .put("rowCount", table.getRowCount()));
        }
        json.put("report", new JSONObject()
                .put("configurationRequested", report.isConfigurationRequested())
                .put("configurationCopied", report.isConfigurationCopied())
                .put("externalPlayerPathsIncluded", report.isExternalPlayerPathsIncluded())
                .put("tableResults", tables));
        return json;
    }

    public static RemoteSyncExecutionResult toExecutionResult(JSONObject json) {
        JSONObject reportJson = json.optJSONObject("report");
        if (reportJson == null) {
            return new RemoteSyncExecutionResult(null, json.optString(MESSAGE, ""));
        }
        List<DatabaseSyncService.TableSyncResult> tableResults = new ArrayList<>();
        JSONArray tables = reportJson.optJSONArray("tableResults");
        if (tables != null) {
            for (int i = 0; i < tables.length(); i++) {
                JSONObject tableJson = tables.optJSONObject(i);
                if (tableJson == null) {
                    continue;
                }
                tableResults.add(new DatabaseSyncService.TableSyncResult(
                        tableJson.optString("tableName", ""),
                        tableJson.optInt("rowCount", 0)
                ));
            }
        }
        DatabaseSyncService.DatabaseSyncReport report = new DatabaseSyncService.DatabaseSyncReport(
                tableResults,
                reportJson.optBoolean("configurationRequested", false),
                reportJson.optBoolean("configurationCopied", false),
                reportJson.optBoolean("externalPlayerPathsIncluded", false)
        );
        return new RemoteSyncExecutionResult(report, json.optString(MESSAGE, ""));
    }
}
