package com.uiptv.shared.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BackgroundJobExceptionTest {
    @Test
    void preservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("network unavailable");

        BackgroundJobException messageOnly = new BackgroundJobException("cache refresh failed");
        BackgroundJobException withCause = new BackgroundJobException("cache refresh failed", cause);

        assertEquals("cache refresh failed", messageOnly.getMessage());
        assertEquals("cache refresh failed", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
