package com.uiptv.model;

import java.util.Locale;

public enum AccountStatus {
    ACTIVE,
    SUSPENDED;

    public static AccountStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return AccountStatus.valueOf(normalized);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    public String toDisplay() {
        return name().toLowerCase(Locale.ROOT);
    }
}
