package com.uiptv.model;

/**
 * Special category types used throughout the application.
 * Using an enum ensures consistency and prevents spelling variations (e.g., uncategorized vs uncategorised).
 */
public enum CategoryType {
    /** Special category containing all content regardless of actual categories */
    ALL("All", "all"),
    
    /** Special category for content without explicit category assignment */
    UNCATEGORIZED("Uncategorized", "uncategorized");

    private final String displayName;
    private final String identifier;

    CategoryType(String displayName, String identifier) {
        this.displayName = displayName;
        this.identifier = identifier;
    }

    /**
     * Get the display name for this category type (for UI and database storage).
     * 
     * @return display name (e.g., "All", "Uncategorized")
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Get the lowercase identifier for this category type.
     * 
     * @return identifier (e.g., "all", "uncategorized")
     */
    public String identifier() {
        return identifier;
    }

    /**
     * Check if a given string matches this category type (case-insensitive).
     * 
     * @param value string to check
     * @return true if value matches this category type
     */
    public boolean matches(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return displayName.equalsIgnoreCase(trimmed) || identifier.equalsIgnoreCase(trimmed);
    }

    /**
     * Find a CategoryType that matches the given string.
     * 
     * @param value string to match (case-insensitive)
     * @return CategoryType if found, null otherwise
     */
    public static CategoryType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (CategoryType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if a string represents the ALL category type.
     * 
     * @param value string to check
     * @return true if value is "All" (case-insensitive)
     */
    public static boolean isAll(String value) {
        return ALL.matches(value);
    }

    /**
     * Check if a string represents the UNCATEGORIZED category type.
     * 
     * @param value string to check
     * @return true if value is "Uncategorized" (case-insensitive)
     */
    public static boolean isUncategorized(String value) {
        return UNCATEGORIZED.matches(value);
    }
}
