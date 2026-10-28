package com.uiptv.util;

/** Quotes SQLite metadata names; callers must still allowlist table selection. */
public final class SqlIdentifiers {
    private SqlIdentifiers() { }

    public static String quote(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
