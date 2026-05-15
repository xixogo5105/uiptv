package com.uiptv.shared.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DatabasePortExceptionTest {
    @Test
    void preservesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("disk locked");

        DatabasePortException messageOnly = new DatabasePortException("sync failed");
        DatabasePortException withCause = new DatabasePortException("sync failed", cause);

        assertEquals("sync failed", messageOnly.getMessage());
        assertEquals("sync failed", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
