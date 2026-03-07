package com.uiptv.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class InMemoryHlsService {
    private static InMemoryHlsService instance;
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    private final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingDeletes = new ConcurrentHashMap<>();
    private static final int MAX_SEGMENTS = 40;
    private static final long TS_DELETE_GRACE_MILLIS = 12_000;
    private final ScheduledExecutorService deleteScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "uiptv-hls-delete-grace");
            thread.setDaemon(true);
            return thread;
        }
    });

    private InMemoryHlsService() {
    }

    public static synchronized InMemoryHlsService getInstance() {
        if (instance == null) {
            instance = new InMemoryHlsService();
        }
        return instance;
    }

    public void put(String name, byte[] data) {
        if (name.endsWith(".ts")) {
            cancelPendingDelete(name);
            cleanupOldSegments();
        }

        storage.put(name, data);
        timestamps.put(name, System.currentTimeMillis());
    }

    public byte[] get(String name) {
        if (storage.containsKey(name)) {
            timestamps.put(name, System.currentTimeMillis());
        }
        return storage.get(name);
    }

    public void remove(String name) {
        if (!name.endsWith(".ts")) {
            removeNow(name);
            return;
        }

        ScheduledFuture<?> existing = pendingDeletes.remove(name);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> scheduled = deleteScheduler.schedule(() -> {
            removeNow(name);
            pendingDeletes.remove(name);
        }, TS_DELETE_GRACE_MILLIS, TimeUnit.MILLISECONDS);
        pendingDeletes.put(name, scheduled);
    }

    public boolean exists(String name) {
        return storage.containsKey(name);
    }

    public void clear() {
        for (ScheduledFuture<?> deleteTask : pendingDeletes.values()) {
            deleteTask.cancel(false);
        }
        pendingDeletes.clear();
        storage.clear();
        timestamps.clear();
    }

    private void cleanupOldSegments() {
        long tsCount = storage.keySet().stream().filter(k -> k.endsWith(".ts")).count();

        while (tsCount >= MAX_SEGMENTS) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
                if (entry.getKey().endsWith(".ts")) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestKey = entry.getKey();
                    }
                }
            }

            if (oldestKey != null) {
                removeNow(oldestKey);
                com.uiptv.util.AppLog.addLog("InMemoryHlsService: Evicted old segment " + oldestKey + " to free memory.");
                tsCount--;
            } else {
                break;
            }
        }
    }

    private void cancelPendingDelete(String name) {
        ScheduledFuture<?> pending = pendingDeletes.remove(name);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void removeNow(String name) {
        cancelPendingDelete(name);
        storage.remove(name);
        timestamps.remove(name);
    }
}
