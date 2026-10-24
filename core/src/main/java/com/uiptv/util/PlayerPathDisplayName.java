package com.uiptv.util;

import java.util.Locale;
import java.util.Set;

/** Creates readable labels for configured external-player paths on any host OS. */
public final class PlayerPathDisplayName {
    private static final Set<String> EXECUTABLE_SUFFIXES = Set.of(
            ".app", ".appimage", ".bat", ".bin", ".cmd", ".com", ".exe", ".run", ".sh"
    );

    private PlayerPathDisplayName() {
    }

    /**
     * Returns the final path component without a common executable or app-bundle suffix.
     * Both slash styles are treated as separators so Windows paths work on Unix and vice versa.
     */
    public static String playerName(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return "";

        String path = configuredPath.trim();
        if (hasMatchingOuterQuotes(path)) {
            path = path.substring(1, path.length() - 1).trim();
        }
        path = path.replace('\\', '/');
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty() || path.matches("(?i)^[a-z]:$")) return "";

        int separator = path.lastIndexOf('/');
        String name = path.substring(separator + 1).trim();
        int extensionSeparator = name.lastIndexOf('.');
        if (extensionSeparator > 0) {
            String suffix = name.substring(extensionSeparator).toLowerCase(Locale.ROOT);
            if (EXECUTABLE_SUFFIXES.contains(suffix)) {
                name = name.substring(0, extensionSeparator);
            }
        }
        return name;
    }

    /** Adds the configured executable name to a translated player-slot label when available. */
    public static String withPlayerName(String playerLabel, String configuredPath) {
        String label = playerLabel == null ? "" : playerLabel;
        String name = playerName(configuredPath);
        return name.isEmpty() ? label : label + " (" + name + ")";
    }

    private static boolean hasMatchingOuterQuotes(String value) {
        return value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")));
    }
}
