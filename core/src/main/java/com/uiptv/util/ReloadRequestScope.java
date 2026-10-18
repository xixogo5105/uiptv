package com.uiptv.util;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Thread-scoped catalogue cancellation and optional network limits.
 * Total duration, request duration and request count limits default to disabled (zero):
 * provider catalogue size and category counts are not bounded. HTTP connection/read timeouts
 * and interruption still apply. Explicit uiptv.reload.* properties opt into stricter limits.
 */
public final class ReloadRequestScope implements AutoCloseable {
    private static final ThreadLocal<ReloadRequestScope> ACTIVE = new ThreadLocal<>();
    private static final ScheduledThreadPoolExecutor WATCHDOG = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "catalogue-request-deadlines"); thread.setDaemon(true); return thread;
    });
    static { WATCHDOG.setRemoveOnCancelPolicy(true); }
    private final ReloadRequestScope previous;
    private final Thread owner = Thread.currentThread();
    private final long deadline;
    private final long requestNanos;
    private final int maxRequests;
    private volatile String failure;
    private int requests;

    public ReloadRequestScope(Duration total, Duration perRequest, int maxRequests, Consumer<String> log) {
        previous = ACTIVE.get(); this.deadline = total.isZero() || total.isNegative() ? Long.MAX_VALUE : System.nanoTime() + total.toNanos();
        this.requestNanos = perRequest.toNanos(); this.maxRequests = maxRequests;
        ACTIVE.set(this);
    }
    public static ReloadRequestScope catalogue(Consumer<String> log) {
        return new ReloadRequestScope(Duration.ofSeconds(Math.max(0, Integer.getInteger("uiptv.reload.budget.seconds", 0))),
                Duration.ofSeconds(Math.max(0, Integer.getInteger("uiptv.reload.request.seconds", 0))),
                Integer.getInteger("uiptv.reload.max.requests", 0), log);
    }
    public static boolean active() { return ACTIVE.get() != null; }
    public static void checkCurrent() { if (ACTIVE.get() != null) ACTIVE.get().check(); }
    public void check() {
        if (owner.isInterrupted()) failure = "Catalogue refresh cancelled";
        if (failure == null && System.nanoTime() >= deadline) failure = "Catalogue refresh time limit reached";
        if (failure != null) throw new LimitExceeded(failure + "; existing cache retained where not yet replaced");
    }
    public static AutoCloseable watch(HttpUriRequestBase request) {
        ReloadRequestScope scope = ACTIVE.get();
        if (scope == null) return () -> {};
        scope.check();
        if (scope.maxRequests > 0 && scope.requests >= scope.maxRequests) {
            scope.failure = "Catalogue refresh request limit reached (" + scope.maxRequests + ")"; scope.check();
        }
        scope.requests++;
        long end = Math.min(scope.deadline, scope.requestNanos <= 0 ? Long.MAX_VALUE : System.nanoTime() + scope.requestNanos);
        ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
            if (scope.owner.isInterrupted() || System.nanoTime() >= end) {
                scope.failure = scope.owner.isInterrupted() ? "Catalogue refresh cancelled" : "Catalogue request deadline reached";
                request.cancel();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return () -> watchdog.cancel(false);
    }
    public int requests() { return requests; }
    @Override public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
    public static final class LimitExceeded extends IllegalStateException {
        LimitExceeded(String message) { super(message); }
    }
}
