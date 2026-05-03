package com.uiptv.ui;

import com.uiptv.ui.dialog.UpdateAvailableDialog;
import com.uiptv.util.I18n;
import com.uiptv.util.VersionManager;
import com.uiptv.widget.UIptvAlert;
import javafx.application.HostServices;
import javafx.application.Platform;
import com.uiptv.util.HttpUtil;
import org.json.JSONObject;

public class UpdateChecker {
    static final String LATEST_RELEASE_API_URL = "https://api.github.com/repos/xixogo5105/uiptv/releases/latest";
    private static final String LATEST_RELEASE_PAGE_URL = "https://github.com/xixogo5105/uiptv/releases/latest";

    private UpdateChecker() {
    }

    public static void checkForUpdates(HostServices hostServices) {
        new Thread(() -> {
            try {
                HttpUtil.HttpResult response = HttpUtil.sendRequest(LATEST_RELEASE_API_URL, githubHeaders(), "GET");
                String content = response.body();

                UpdateInfo updateInfo = parseUpdateInfo(new JSONObject(content));

                if (isUpdateAvailable(updateInfo.getVersion())) {
                    Platform.runLater(() -> {
                        if (showUpdateAvailableDialog(updateInfo)) {
                            openWebpage(hostServices, updateInfo.getUrl());
                        }
                    });
                } else {
                    Platform.runLater(() -> UIptvAlert.showMessageAlert(I18n.tr("autoYouAreCurrentlyOnTheLatestVersion")));
                }
            } catch (Exception e) {
                UIptvAlert.showError(I18n.tr("autoUpdateCheckFailed", e.getMessage()), e);
                Platform.runLater(() -> UIptvAlert.showErrorAlert(I18n.tr("autoUpdateCheckFailed", e.getMessage())));
            }
        }).start();
    }

    static UpdateInfo parseUpdateInfo(JSONObject json) {
        String latestVersion = normalizeVersionValue(json.optString("tag_name", json.optString("name", "")));
        String url = json.optString("html_url", LATEST_RELEASE_PAGE_URL);
        String description = json.optString("body", "").trim();
        if (description.isBlank()) {
            description = json.optString("name", latestVersion);
        }
        if (latestVersion.isBlank()) {
            latestVersion = "0";
        }
        return new UpdateInfo(latestVersion, url, description);
    }

    static boolean isUpdateAvailable(String latestVersion) {
        String currentVersion = VersionManager.getCurrentVersion();
        if (latestVersion == null || currentVersion == null) {
            return false;
        }

        try {
            String[] current = normalizeVersionValue(currentVersion).split("\\.");
            String[] latest = normalizeVersionValue(latestVersion).split("\\.");
            int length = Math.max(current.length, latest.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < current.length ? Integer.parseInt(current[i]) : 0;
                int latestPart = i < latest.length ? Integer.parseInt(latest[i]) : 0;
                if (latestPart > currentPart) {
                    return true;
                }
                if (latestPart < currentPart) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            UIptvAlert.showError("Failed to compare versions. currentVersion='" + currentVersion + "', latestVersion='" + latestVersion + "'", e);
            return false;
        }

        return false;
    }

    private static void openWebpage(HostServices hostServices, String url) {
        hostServices.showDocument(url);
    }

    private static boolean showUpdateAvailableDialog(UpdateInfo updateInfo) {
        return UpdateAvailableDialog.show(updateInfo);
    }

    private static java.util.Map<String, String> githubHeaders() {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        headers.put("User-Agent", "UIPTV/" + VersionManager.getCurrentVersion());
        return headers;
    }

    static String normalizeVersionValue(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }
        String normalized = value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace("-SNAPSHOT", "");
        return normalized;
    }
}
