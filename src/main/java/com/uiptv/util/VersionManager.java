package com.uiptv.util;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class VersionManager {

    public static String getCurrentVersion() {
        try (InputStream input = VersionManager.class.getClassLoader().getResourceAsStream("update.json")) {
            if (input == null) {
                return "N/A";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                JSONObject json = new JSONObject(content.toString());
                return json.getString("version");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return "N/A";
        }
    }
}