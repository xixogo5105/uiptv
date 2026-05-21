package com.uiptv.db;

import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowLevelCoverageTest extends DbBackedTest {

    @Test
    void databaseAccessException_preservesMessageAndCause() {
        DatabaseAccessException withoutCause = new DatabaseAccessException("plain");
        IllegalStateException cause = new IllegalStateException("boom");
        DatabaseAccessException withCause = new DatabaseAccessException("wrapped", cause);

        assertEquals("plain", withoutCause.getMessage());
        assertEquals("wrapped", withCause.getMessage());
        assertEquals(cause, withCause.getCause());
    }
}
