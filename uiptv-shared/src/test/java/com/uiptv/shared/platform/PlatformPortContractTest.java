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
    void backgroundJobRequestRequiresNameAndProtectsParameters() {
        BackgroundJobRequest request = new BackgroundJobRequest(" cache-refresh ", null, false,
                BackgroundJobUniqueness.REPLACE_EXISTING);

        assertEquals(" cache-refresh ", request.jobName());
        assertEquals(Map.of(), request.parameters());
        assertEquals(BackgroundJobUniqueness.REPLACE_EXISTING, request.uniqueness());
        assertThrows(UnsupportedOperationException.class, () -> request.parameters().put("accountId", "7"));
        assertThrows(IllegalArgumentException.class,
                () -> new BackgroundJobRequest(" ", Map.of(), false, null));
    }

    @Test
    void backgroundJobStateDefaultsStatusAndMessage() {
        BackgroundJobState state = new BackgroundJobState("job-1", null, 0, null);

        assertEquals("job-1", state.jobId());
        assertEquals(BackgroundJobStatus.UNKNOWN, state.status());
        assertEquals("", state.message());
        assertThrows(IllegalArgumentException.class,
                () -> new BackgroundJobState("", BackgroundJobStatus.RUNNING, 0, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new BackgroundJobState("job-1", BackgroundJobStatus.RUNNING, -1, ""));
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
