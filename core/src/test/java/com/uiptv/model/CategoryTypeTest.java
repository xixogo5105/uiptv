package com.uiptv.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTypeTest {

    @Test
    void matchesDisplayNamesIdentifiersAndNulls() {
        assertEquals("All", CategoryType.ALL.displayName());
        assertEquals("all", CategoryType.ALL.identifier());
        assertEquals("Uncategorized", CategoryType.UNCATEGORIZED.displayName());
        assertEquals("uncategorized", CategoryType.UNCATEGORIZED.identifier());

        assertTrue(CategoryType.ALL.matches(" all "));
        assertTrue(CategoryType.ALL.matches("ALL"));
        assertTrue(CategoryType.UNCATEGORIZED.matches("uncategorized"));
        assertFalse(CategoryType.ALL.matches(null));
        assertFalse(CategoryType.ALL.matches("movies"));

        assertEquals(CategoryType.ALL, CategoryType.fromString("All"));
        assertEquals(CategoryType.UNCATEGORIZED, CategoryType.fromString(" uncategorized "));
        assertNull(CategoryType.fromString(null));
        assertNull(CategoryType.fromString("movies"));
        assertTrue(CategoryType.isAll("all"));
        assertFalse(CategoryType.isAll("uncategorized"));
        assertTrue(CategoryType.isUncategorized("Uncategorized"));
        assertFalse(CategoryType.isUncategorized("all"));
    }
}
