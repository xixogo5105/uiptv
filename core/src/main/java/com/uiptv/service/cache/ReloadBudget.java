package com.uiptv.service.cache;

import java.time.Duration;

/**
 * Wall-clock budget for a single account's cache reload.
 *
 * <p>Catalogue refresh uses bulk endpoints only. The network request scope enforces deadlines
 * during I/O; this budget also stops later phases from starting once the account time expires.
 *
 * <p>Default is 2 minutes, overridable with the {@code uiptv.reload.budget.seconds} system
 * property ({@code 0} disables the limit).
 */
public final class ReloadBudget {

    static final String BUDGET_SECONDS_PROPERTY = "uiptv.reload.budget.seconds";
    private static final int DEFAULT_BUDGET_SECONDS = 120;

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
