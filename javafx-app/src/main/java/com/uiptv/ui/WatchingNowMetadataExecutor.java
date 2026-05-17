package com.uiptv.ui;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class WatchingNowMetadataExecutor {
    private static final int THREADS = Math.max(1, Integer.getInteger("uiptv.watchingnow.metadata.threads", 3));
    private static final int QUEUE_SIZE = Math.max(1, Integer.getInteger("uiptv.watchingnow.metadata.queue.size", 128));
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            THREADS,
            THREADS,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_SIZE),
            runnable -> {
                Thread thread = new Thread(runnable, "watching-now-metadata");
                thread.setDaemon(true);
                return thread;
            }
    );

    private WatchingNowMetadataExecutor() {
    }

    static boolean submit(Runnable task) {
        if (task == null) {
            return false;
        }
        try {
            EXECUTOR.execute(task);
            return true;
        } catch (RejectedExecutionException _) {
            return false;
        }
    }
}
