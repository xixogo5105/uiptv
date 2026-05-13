package com.uiptv.shared.platform;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformPortContractTest {
    @Test
    void backgroundJobRequestDefaultsToKeepExisting() {
        BackgroundJobRequest request = new BackgroundJobRequest("cache-refresh", Map.of("accountId", "7"), true, null);

        assertEquals("cache-refresh", request.jobName());
        assertEquals("7", request.parameters().get("accountId"));
        assertEquals(BackgroundJobUniqueness.KEEP_EXISTING, request.uniqueness());
    }

    @Test
    void backgroundJobStateRequiresBoundedProgress() {
        assertEquals(BackgroundJobStatus.RUNNING,
                new BackgroundJobState("job-1", BackgroundJobStatus.RUNNING, 50, null).status());
        assertThrows(IllegalArgumentException.class,
                () -> new BackgroundJobState("job-1", BackgroundJobStatus.RUNNING, 101, null));
    }

    @Test
    void noopLoggerAcceptsAllLevels() {
        LoggerPort logger = LoggerPort.noop();

        logger.debug("debug");
        logger.info("info");
        logger.warn("warn");
        logger.error("error", new IllegalStateException("ignored"));
    }
}
