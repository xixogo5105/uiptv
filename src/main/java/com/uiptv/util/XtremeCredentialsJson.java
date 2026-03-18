package com.uiptv.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public final class XtremeCredentialsJson {
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_DEFAULT = "default";

    private XtremeCredentialsJson() {
    }

    public record Entry(String username, String password, boolean isDefault) {
    }

    public static List<Entry> parse(String rawJson) {
        if (isBlank(rawJson)) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = new JSONArray(rawJson.trim());
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    String username = obj.optString(KEY_USERNAME, "").trim();
                    String password = obj.optString(KEY_PASSWORD, "");
                    boolean isDefault = obj.optBoolean(KEY_DEFAULT, false);
                    if (isNotBlank(username) && isNotBlank(password)) {
                        entries.add(new Entry(username, password, isDefault));
                    }
                }
            }
            return normalize(entries, null);
        } catch (Exception _) {
            return new ArrayList<>();
        }
    }

    public static List<Entry> normalize(List<Entry> entries, String defaultUsername) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<Entry> normalized = dedupe(entries);
        List<Entry> withDefault = applyDefault(normalized, defaultUsername);
        return ensureDefault(withDefault);
    }

    public static Entry resolveDefault(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry != null && entry.isDefault()) {
                return entry;
            }
        }
        return entries.get(0);
    }

    public static String toJson(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        List<Entry> normalized = normalize(entries, null);
        JSONArray array = new JSONArray();
        for (Entry entry : normalized) {
            JSONObject obj = new JSONObject();
            obj.put(KEY_USERNAME, entry.username());
            obj.put(KEY_PASSWORD, entry.password());
            if (entry.isDefault()) {
                obj.put(KEY_DEFAULT, true);
            }
            array.put(obj);
        }
        return array.toString();
    }

    private static List<Entry> dedupe(List<Entry> entries) {
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry != null) {
                String username = entry.username() != null ? entry.username().trim() : "";
                String password = entry.password();
                if (isNotBlank(username) && isNotBlank(password)) {
                    String key = username + "\u0000" + password;
                    unique.computeIfAbsent(key, k -> new Entry(username, password, entry.isDefault()));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static List<Entry> applyDefault(List<Entry> entries, String defaultUsername) {
        boolean defaultFound = false;
        List<Entry> withDefault = new ArrayList<>(entries.size());
        if (isNotBlank(defaultUsername)) {
            String normalizedDefault = defaultUsername.trim();
            for (Entry entry : entries) {
                boolean isDefault = !defaultFound && entry.username().equals(normalizedDefault);
                if (isDefault) {
                    defaultFound = true;
                }
                withDefault.add(new Entry(entry.username(), entry.password(), isDefault));
            }
        } else {
            for (Entry entry : entries) {
                boolean isDefault = entry.isDefault() && !defaultFound;
                if (isDefault) {
                    defaultFound = true;
                }
                withDefault.add(new Entry(entry.username(), entry.password(), isDefault));
            }
        }
        return withDefault;
    }

    private static List<Entry> ensureDefault(List<Entry> entries) {
        if (entries.isEmpty()) {
            return entries;
        }
        boolean hasDefault = entries.stream().anyMatch(entry -> entry != null && entry.isDefault());
        if (hasDefault) {
            return entries;
        }
        List<Entry> updated = new ArrayList<>(entries);
        Entry first = updated.get(0);
        updated.set(0, new Entry(first.username(), first.password(), true));
        return updated;
    }
}
