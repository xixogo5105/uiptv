package com.uiptv.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.uiptv.util.StringUtils.EMPTY;
import static com.uiptv.util.StringUtils.isBlank;

public final class M3uPlaylistUtils {
    private M3uPlaylistUtils() {
    }

    public static String parseAttribute(String line, String key) {
        if (isBlank(line) || isBlank(key)) {
            return EMPTY;
        }
        int keyIndex = line.indexOf(key);
        if (keyIndex < 0) {
            return EMPTY;
        }
        int equalsIndex = line.indexOf('=', keyIndex + key.length());
        if (equalsIndex < 0) {
            return EMPTY;
        }
        int valueStart = equalsIndex + 1;
        while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= line.length()) {
            return EMPTY;
        }

        char quote = line.charAt(valueStart);
        if (quote == '"' || quote == '\'') {
            int valueEnd = line.indexOf(quote, valueStart + 1);
            if (valueEnd < 0) {
                return line.substring(valueStart + 1).trim();
            }
            return line.substring(valueStart + 1, valueEnd).trim();
        }

        int valueEnd = valueStart;
        while (valueEnd < line.length()) {
            char current = line.charAt(valueEnd);
            if (Character.isWhitespace(current) || current == ',') {
                break;
            }
            valueEnd++;
        }
        return line.substring(valueStart, valueEnd).trim();
    }

    public static List<String> splitGroupTitles(String rawGroupTitle) {
        if (isBlank(rawGroupTitle)) {
            return List.of();
        }
        Set<String> titles = new LinkedHashSet<>();
        for (String candidate : rawGroupTitle.split(";")) {
            String trimmed = candidate == null ? EMPTY : candidate.trim();
            if (!trimmed.isEmpty()) {
                titles.add(trimmed);
            }
        }
        return new ArrayList<>(titles);
    }

    public static String escapeAttributeValue(String value) {
        if (value == null) {
            return EMPTY;
        }
        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\"", "'");
    }

    public static String sanitizeTitle(String value) {
        if (value == null) {
            return EMPTY;
        }
        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }
}
