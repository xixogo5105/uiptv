package com.uiptv.model;

/**
 * Theme selection persisted in the {@code themeMode} column of the {@code Configuration} table.
 * <p>
 * The column replaced the {@code darkTheme} flag in migration 0206, which is dropped again by 0207, so
 * this enum is the only description of the setting. Values the column does not recognise, and an absent
 * value, mean the light theme.
 * <p>
 * This enum is deliberately free of JavaFX types so it can live in {@code core}; resolving
 * {@link #SYSTEM} against the operating system colour scheme is the job of the JavaFX layer.
 */
public enum ThemeMode {
    /** Always use the light palette, regardless of the operating system setting. */
    LIGHT("light"),
    /** Always use the dark palette, regardless of the operating system setting. */
    DARK("dark"),
    /** Follow the operating system colour scheme and re-apply when the user changes it. */
    // Literal instead of SYSTEM_VALUE: enum constants must precede field declarations, so referencing the
    // constant declared below would be an illegal forward reference. ThemeModeTest pins the two together.
    SYSTEM("system");

    /** Persisted sentinel for {@link #SYSTEM} inside the legacy {@code darkTheme} column. */
    public static final String SYSTEM_VALUE = "system";

    private final String persistedValue;

    ThemeMode(String persistedValue) {
        this.persistedValue = persistedValue;
    }

    /** Value written to the {@code darkTheme} column. */
    public String persistedValue() {
        return persistedValue;
    }

    /** {@code true} when the operating system colour scheme has to be consulted to resolve this mode. */
    public boolean isSystem() {
        return this == SYSTEM;
    }

    /**
     * Next mode for the header toggle cycle: Light to Dark to System to Light.
     */
    public ThemeMode next() {
        return switch (this) {
            case LIGHT -> DARK;
            case DARK -> SYSTEM;
            case SYSTEM -> LIGHT;
        };
    }

    /**
     * Resolves a stored column value. Never throws; an absent or unrecognised value means light.
     */
    public static ThemeMode fromPersisted(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LIGHT;
        }
        String value = rawValue.trim();
        if (SYSTEM_VALUE.equalsIgnoreCase(value)) {
            return SYSTEM;
        }
        if (DARK.persistedValue.equalsIgnoreCase(value)) {
            return DARK;
        }
        return LIGHT;
    }
}
