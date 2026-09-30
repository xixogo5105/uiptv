package com.uiptv.service.cache;

import java.time.Duration;

/**
 * Wall-clock budget for a single account's cache reload.
 *
 * <p>A Stalker reload is not a single request: it may probe for an API endpoint, handshake, list
 * genres, list all channels, and then fall back to paging every category individually. When an
 * endpoint stalls, each of those calls can burn the full HTTP response timeout, so the total can
 * grow to many minutes with no progress. The budget bounds the wall-clock time one account may
 * consume and lets the reloader stop cleanly, keeping the existing cache instead.
 *
 * <p>Default is 5 minutes, overridable with the {@code uiptv.reload.budget.seconds} system
 * property ({@code 0} disables the limit).
 */
public final class ReloadBudget {

    static final String BUDGET_SECONDS_PROPERTY = "uiptv.reload.budget.seconds";
    private static final int DEFAULT_BUDGET_SECONDS = 300;

    private final long deadlineNanos;
    private final boolean limited;

    private ReloadBudget(long deadlineNanos, boolean limited) {
        this.deadlineNanos = deadlineNanos;
        this.limited = limited;
    }

    public static ReloadBudget start() {
        int seconds = Integer.getInteger(BUDGET_SECONDS_PROPERTY, DEFAULT_BUDGET_SECONDS);
        if (seconds <= 0) {
            return new ReloadBudget(Long.MAX_VALUE, false);
        }
        return new ReloadBudget(System.nanoTime() + Duration.ofSeconds(seconds).toNanos(), true);
    }

    /** Visible for testing. */
    static ReloadBudget start(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return new ReloadBudget(Long.MAX_VALUE, false);
        }
        return new ReloadBudget(System.nanoTime() + duration.toNanos(), true);
    }

    public boolean expired() {
        return limited && System.nanoTime() >= deadlineNanos;
    }

    public long remainingSeconds() {
        if (!limited) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000_000L);
    }
}
