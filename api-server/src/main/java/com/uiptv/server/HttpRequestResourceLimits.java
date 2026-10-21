package com.uiptv.server;

import java.util.concurrent.Semaphore;

/** Bounds requests that keep one Jetty worker occupied for their whole lifetime. */
public final class HttpRequestResourceLimits {
    private static final int MAX_PROXY_STREAMS = 12;
    private static final int MAX_EVENT_STREAMS = 4;
    private static final Semaphore PROXY_STREAMS = new Semaphore(MAX_PROXY_STREAMS, true);
    private static final Semaphore EVENT_STREAMS = new Semaphore(MAX_EVENT_STREAMS, true);

    private HttpRequestResourceLimits() {
    }

    public static Permit tryAcquireProxyStream() {
        return tryAcquire(PROXY_STREAMS);
    }

    public static Permit tryAcquireEventStream() {
        return tryAcquire(EVENT_STREAMS);
    }

    private static Permit tryAcquire(Semaphore semaphore) {
        return semaphore.tryAcquire() ? new Permit(semaphore) : null;
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            semaphore.release();
        }
    }
}
