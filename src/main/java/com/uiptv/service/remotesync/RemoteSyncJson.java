package com.uiptv.service.remotesync;

import com.uiptv.service.DatabaseSyncService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RemoteSyncJson {
    private RemoteSyncJson() {
    }

    public static JSONObject toJson(RemoteSyncRequest request) {
        return new JSONObject()
                .put("direction", request.direction().name())
                .put("verificationCode", request.verificationCode())
                .put("requesterName", request.requesterName())
                .put("syncConfiguration", request.options().syncConfiguration())
                .put("syncExternalPlayerPaths", request.options().syncExternalPlayerPaths());
    }

    public static RemoteSyncRequest toRequest(JSONObject json) {
        return new RemoteSyncRequest(
                RemoteSyncDirection.valueOf(json.optString("direction", RemoteSyncDirection.EXPORT_TO_REMOTE.name())),
                json.optString("verificationCode", ""),
                json.optString("requesterName", ""),
                new RemoteSyncOptions(
                        json.optBoolean("syncConfiguration", false),
                        json.optBoolean("syncExternalPlayerPaths", false)
                )
        );
    }

    public static JSONObject toJson(RemoteSyncSessionState state) {
        return new JSONObject()
                .put("sessionId", state.sessionId())
                .put("direction", state.direction().name())
                .put("status", state.status().name())
                .put("verificationCode", state.verificationCode())
                .put("requesterName", state.requesterName())
                .put("requesterAddress", state.requesterAddress())
                .put("syncConfiguration", state.options().syncConfiguration())
                .put("syncExternalPlayerPaths", state.options().syncExternalPlayerPaths())
                .put("message", state.message());
    }

    public static RemoteSyncSessionState toSessionState(JSONObject json) {
        return new RemoteSyncSessionState(
                json.optString("sessionId", ""),
                RemoteSyncDirection.valueOf(json.optString("direction", RemoteSyncDirection.EXPORT_TO_REMOTE.name())),
                RemoteSyncStatus.valueOf(json.optString("status", RemoteSyncStatus.FAILED.name())),
                json.optString("verificationCode", ""),
                json.optString("requesterName", ""),
                json.optString("requesterAddress", ""),
                new RemoteSyncOptions(
                        json.optBoolean("syncConfiguration", false),
                        json.optBoolean("syncExternalPlayerPaths", false)
                ),
                json.optString("message", "")
        );
    }

    public static JSONObject toJson(RemoteSyncExecutionResult result) {
        JSONObject json = new JSONObject().put("message", result.message());
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
            return new RemoteSyncExecutionResult(null, json.optString("message", ""));
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
        return new RemoteSyncExecutionResult(report, json.optString("message", ""));
    }
}
