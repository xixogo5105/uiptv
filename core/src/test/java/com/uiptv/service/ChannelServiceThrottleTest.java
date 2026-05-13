package com.uiptv.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelServiceThrottleTest {

    @Test
    void onSuccessReturnsBaseDelayWhenNoJitter() {
        ChannelService.RequestThrottle throttle = new ChannelService.RequestThrottle(800L, 8000L, 0L);
        long delay = throttle.onSuccess();
        assertEquals(800L, delay);
    }

    @Test
    void onFailureBacksOffAndCapsAtMaxDelay() {
        ChannelService.RequestThrottle throttle = new ChannelService.RequestThrottle(800L, 3000L, 0L);

        long first = throttle.onFailure();
        long second = throttle.onFailure();
        long third = throttle.onFailure();

        assertEquals(1600L, first);
        assertEquals(3000L, second);
        assertEquals(3000L, third);
    }

    @Test
    void onSuccessResetsBackoff() {
        ChannelService.RequestThrottle throttle = new ChannelService.RequestThrottle(800L, 8000L, 0L);

        long firstFailure = throttle.onFailure();
        long secondFailure = throttle.onFailure();
        long success = throttle.onSuccess();

        assertEquals(1600L, firstFailure);
        assertEquals(3200L, secondFailure);
        assertEquals(800L, success);
    }

    @Test
    void jitterStaysWithinBounds() {
        long baseDelay = 800L;
        long jitter = 200L;
        ChannelService.RequestThrottle throttle = new ChannelService.RequestThrottle(baseDelay, 8000L, jitter);

        long delay = throttle.onSuccess();
        assertTrue(delay >= baseDelay - jitter);
        assertTrue(delay <= baseDelay + jitter);
    }
}
