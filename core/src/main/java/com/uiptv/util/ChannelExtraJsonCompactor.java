package com.uiptv.util;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Import-time metadata only. Playback commands/DRM/logo remain in their dedicated columns.
 * The allow-list is shared with Android through channel-extra-fields.properties. Retained scalar
 * values keep their JSON types; arbitrary provider objects/arrays (notably cmds/series) are discarded.
 * M3U metadata is deliberately opaque and is copied in full, including custom nested options.
 * This helper must never be applied to serialized Episode models or already-stored user rows.
 */
public final class ChannelExtraJsonCompactor {
    private static final List<String> FIELDS = loadFields();
    private static final AtomicLong malformedInputs = new AtomicLong();

    private ChannelExtraJsonCompactor() { }

    public static String compact(String source) {
        if (source == null || source.isBlank()) return "{}";
        try {
            return compact(new JSONObject(source));
        } catch (RuntimeException _) {
            if (malformedInputs.incrementAndGet() == 1) {
                AppLog.addWarningLog(ChannelExtraJsonCompactor.class, "Invalid channel metadata discarded during import.");
            }
            return "{}";
        }
    }

    public static String compact(JSONObject source) {
        JSONObject result = new JSONObject();
        if (source == null) return result.toString();
        for (String field : FIELDS) {
            Object value = source.opt(field);
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                result.put(field, value);
            }
        }
        JSONObject m3u = source.optJSONObject("m3u");
        if (m3u != null) result.put("m3u", new JSONObject(m3u.toString()));
        return result.toString();
    }

    private static List<String> loadFields() {
        Properties policy = new Properties();
        try (var input = ChannelExtraJsonCompactor.class.getResourceAsStream("/metadata/channel-extra-fields.properties")) {
            if (input == null) throw new IllegalStateException("Channel metadata policy missing");
            policy.load(input);
            return Arrays.asList(policy.getProperty("channel").split(","));
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
