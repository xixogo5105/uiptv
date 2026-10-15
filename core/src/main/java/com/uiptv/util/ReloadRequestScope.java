package com.uiptv.util;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Thread-scoped catalogue network limits; does not change playback or EPG request policy. */
public final class ReloadRequestScope implements AutoCloseable {
    private static final ThreadLocal<ReloadRequestScope> ACTIVE = new ThreadLocal<>();
    private static final ScheduledThreadPoolExecutor WATCHDOG = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "catalogue-request-deadlines"); thread.setDaemon(true); return thread;
    });
    static { WATCHDOG.setRemoveOnCancelPolicy(true); }
    private final ReloadRequestScope previous;
    private final Thread owner = Thread.currentThread();
    private final long deadline, requestNanos;
    private final int maxRequests;
    private final Consumer<String> log;
    private volatile String failure;
    private int requests;

    public ReloadRequestScope(Duration total, Duration perRequest, int maxRequests, Consumer<String> log) {
        previous = ACTIVE.get(); this.deadline = total.isZero() || total.isNegative() ? Long.MAX_VALUE : System.nanoTime() + total.toNanos();
        this.requestNanos = perRequest.toNanos(); this.maxRequests = maxRequests; this.log = log;
        ACTIVE.set(this);
    }
    public static ReloadRequestScope catalogue(Consumer<String> log) {
        return new ReloadRequestScope(Duration.ofSeconds(Math.max(0, Integer.getInteger("uiptv.reload.budget.seconds", 120))),
                Duration.ofSeconds(Math.max(1, Integer.getInteger("uiptv.reload.request.seconds", 30))),
                Math.max(1, Integer.getInteger("uiptv.reload.max.requests", 24)), log);
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
        if (scope.requests >= scope.maxRequests) {
            scope.failure = "Catalogue refresh request limit reached (" + scope.maxRequests + ")"; scope.check();
        }
        scope.requests++;
        if (scope.log != null) scope.log.accept("Catalogue request " + scope.requests + "/" + scope.maxRequests);
        long end = Math.min(scope.deadline, System.nanoTime() + scope.requestNanos);
        ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
            if (scope.owner.isInterrupted() || System.nanoTime() >= end) {
                scope.failure = scope.owner.isInterrupted() ? "Catalogue refresh cancelled" : "Catalogue request deadline reached";
                request.cancel();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return () -> watchdog.cancel(false);
    }
    public int requests() { return requests; }
    @Override public void close() { ACTIVE.set(previous); }
    public static final class LimitExceeded extends IllegalStateException {
        private LimitExceeded(String message) { super(message); }
    }
}
