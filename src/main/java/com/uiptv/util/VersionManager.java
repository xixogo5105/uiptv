package com.uiptv.util;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.uiptv.widget.UIptvAlert.showError;

public class VersionManager {
    private VersionManager() {
    }

    public static String getCurrentVersion() {
        return getUpdateMetadataValue("version");
    }

    public static String getReleaseUrl() {
        return getUpdateMetadataValue("url");
    }

    public static String getReleaseDescription() {
        return getUpdateMetadataValue("description");
    }

    private static String getUpdateMetadataValue(String key) {
        JSONObject metadata = readUpdateMetadata();
        if (metadata == null) {
            return "N/A";
        }
        return metadata.optString(key, "N/A");
    }

    private static JSONObject readUpdateMetadata() {
        try (InputStream input = VersionManager.class.getClassLoader().getResourceAsStream("update.json")) {
            if (input == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                return new JSONObject(content.toString());
            }
        } catch (IOException ex) {
            showError("Failed to read update metadata from update.json", ex);
            return null;
        }
    }
}
