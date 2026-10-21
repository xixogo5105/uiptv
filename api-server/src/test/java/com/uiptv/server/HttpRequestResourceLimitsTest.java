package com.uiptv.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestResourceLimitsTest {
    @Test
    void eventStreamPermitsAreBoundedAndReleasedOnce() {
        List<HttpRequestResourceLimits.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 4; i++) permits.add(HttpRequestResourceLimits.tryAcquireEventStream());
        assertNull(HttpRequestResourceLimits.tryAcquireEventStream());

        permits.getFirst().close();
        permits.getFirst().close();
        HttpRequestResourceLimits.Permit replacement = HttpRequestResourceLimits.tryAcquireEventStream();
        assertNotNull(replacement);
        permits.forEach(HttpRequestResourceLimits.Permit::close);
        replacement.close();
        HttpRequestResourceLimits.Permit finalPermit = HttpRequestResourceLimits.tryAcquireEventStream();
        assertNotNull(finalPermit);
        finalPermit.close();
    }

    @Test
    void proxyStreamPermitsAreBounded() {
        List<HttpRequestResourceLimits.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 12; i++) permits.add(HttpRequestResourceLimits.tryAcquireProxyStream());
        assertNull(HttpRequestResourceLimits.tryAcquireProxyStream());
        permits.forEach(HttpRequestResourceLimits.Permit::close);
        HttpRequestResourceLimits.Permit finalPermit = HttpRequestResourceLimits.tryAcquireProxyStream();
        assertNotNull(finalPermit);
        finalPermit.close();
    }
}
