package com.uiptv.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublishedM3uSelectionTest {

    @Test
    void constructorsAndLombokAccessorsWork() {
        PublishedM3uSelection empty = new PublishedM3uSelection();
        assertNull(empty.getAccountId());
        assertNull(empty.getDbId());

        PublishedM3uSelection selection = new PublishedM3uSelection("account-1");
        selection.setDbId("db-1");

        assertEquals("account-1", selection.getAccountId());
        assertEquals("db-1", selection.getDbId());
        assertEquals(selection, selection);
        assertNotEquals(selection, empty);
    }
}
