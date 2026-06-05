package com.uiptv.ui;

/**
 * Contract for UI panels that receive search text from a parent header.
 */
public interface SearchTarget {
    void setSearchQuery(String query);

    static void apply(SearchTarget target, String query) {
        if (target != null) {
            target.setSearchQuery(query);
        }
    }
}
