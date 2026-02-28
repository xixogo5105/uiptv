package com.uiptv.ui;

import com.uiptv.util.VersionManager;
import com.uiptv.widget.UIptvAlert;
import javafx.application.HostServices;
import javafx.application.Platform;
import org.json.JSONObject;
import com.uiptv.util.HttpUtil;

public class UpdateChecker {

    private static final String UPDATE_URL = "https://raw.githubusercontent.com/xixogo5105/uiptv/refs/heads/main/src/main/resources/update.json";

    public static void checkForUpdates(HostServices hostServices) {
        new Thread(() -> {
            try {
                HttpUtil.HttpResult response = HttpUtil.sendRequest(UPDATE_URL, null, "GET");
                String content = response.body();

                JSONObject json = new JSONObject(content);
                UpdateInfo updateInfo = new UpdateInfo(json.getString("version"), json.getString("url"), json.getString("description"));

                if (isUpdateAvailable(updateInfo.getVersion())) {
                    Platform.runLater(() -> {
                        if (UIptvAlert.showConfirmationAlert("Update Available: " + updateInfo.getVersion() + "\n\nRelease notes: " + updateInfo.getDescription() + "\n\nClick 'OK' to download.")) {
                            openWebpage(hostServices, updateInfo.getUrl());
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        UIptvAlert.showMessageAlert("You are currently on the latest version.");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    UIptvAlert.showErrorAlert("Update Check Failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static boolean isUpdateAvailable(String latestVersion) {
        String currentVersion = VersionManager.getCurrentVersion();
        if (latestVersion == null || currentVersion == null) {
            return false;
        }

        try {
            String[] current = currentVersion.replace("-SNAPSHOT", "").split("\\.");
            String[] latest = latestVersion.replace("-SNAPSHOT", "").split("\\.");
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
            e.printStackTrace();
            return false;
        }

        return false;
    }

    private static void openWebpage(HostServices hostServices, String url) {
        hostServices.showDocument(url);
    }
}
