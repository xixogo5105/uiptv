package com.uiptv.util;

import com.uiptv.ui.LogDisplayUI;
import javafx.scene.image.Image;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.LaxRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ImageCacheManager {

    private static final long NEGATIVE_CACHE_MS_404 = 15L * 60L * 1000L;
    private static final long NEGATIVE_CACHE_MS_ERROR = 2L * 60L * 1000L;
    private static final long NEGATIVE_CACHE_MS_429_DEFAULT = 90L * 1000L;
    private static final int MAX_MEMORY_IMAGES = Integer.getInteger("uiptv.image.cache.max.entries", 120);
    private static final int MAX_IMAGE_DECODE_WIDTH = Integer.getInteger("uiptv.image.decode.max.width", 640);
    private static final int MAX_IMAGE_DECODE_HEIGHT = Integer.getInteger("uiptv.image.decode.max.height", 640);
    private static final long DISK_CACHE_MAX_BYTES = Long.getLong("uiptv.image.cache.disk.max.bytes", 512L * 1024L * 1024L);
    private static final long DISK_CACHE_TRIM_TO_BYTES = Long.getLong("uiptv.image.cache.disk.trim.bytes", 384L * 1024L * 1024L);
    private static final long DISK_CACHE_TRIM_INTERVAL_MS = Long.getLong("uiptv.image.cache.disk.trim.interval.ms", 5L * 60L * 1000L);
    private static final long TRANSIENT_CACHE_TRIM_INTERVAL_MS = Long.getLong("uiptv.image.cache.transient.trim.interval.ms", 60_000L);
    private static final int NEGATIVE_CACHE_MAX_ENTRIES = Integer.getInteger("uiptv.image.cache.negative.max.entries", 20_000);
    private static final int HOST_STATE_MAX_ENTRIES = Integer.getInteger("uiptv.image.cache.host.max.entries", 2_048);
    // Use bounded maps to prevent unbounded memory growth
    private static final Map<String, Long> NEGATIVE_CACHE_UNTIL = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > NEGATIVE_CACHE_MAX_ENTRIES;
                }
            }
    );
    private static final Map<String, Long> HOST_BACKOFF_UNTIL = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > HOST_STATE_MAX_ENTRIES;
                }
            }
    );
    // Use LinkedHashMap with size limit instead of unbounded ConcurrentHashMap to prevent memory leak
    private static final Map<String, Semaphore> HOST_SEMAPHORES = Collections.synchronizedMap(
            new LinkedHashMap<String, Semaphore>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Semaphore> eldest) {
                    return size() > HOST_STATE_MAX_ENTRIES;
                }
            }
    );
    private static final Map<String, Long> HOST_LOG_UNTIL = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > HOST_STATE_MAX_ENTRIES;
                }
            }
    );
    private static final int HOST_PARALLEL_LIMIT = 2;
    private static final long HOST_LOG_WINDOW_MS = 60_000L;
    private static final Path DISK_CACHE_DIR = resolveDiskCacheDir();
    private static final Object DISK_TRIM_LOCK = new Object();
    private static final Object TRANSIENT_TRIM_LOCK = new Object();
    private static volatile long lastDiskTrimMs = 0L;
    private static volatile long lastTransientTrimMs = 0L;

    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER =
            PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(40)
                    .setMaxConnPerRoute(8)
                    .build();

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setConnectionManager(CONNECTION_MANAGER)
            .setRedirectStrategy(new LaxRedirectStrategy())
            .disableAutomaticRetries()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(15))
                    .setConnectTimeout(Timeout.ofSeconds(8))
                    .setResponseTimeout(Timeout.ofSeconds(15))
                    .build())
            .build();
    private static final Map<String, Image> IMAGE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > MAX_MEMORY_IMAGES;
                }
            });
    private static final Map<String, CompletableFuture<Image>> LOADING_TASKS = new ConcurrentHashMap<>();

    public static CompletableFuture<Image> loadImageAsync(String url, String caller) {
        String normalizedCaller = normalizeCaller(caller);
        String cacheKey = normalizedCaller + ":" + url;
        if (url == null || !url.startsWith("http")) {
            return CompletableFuture.completedFuture(null);
        }
        trimTransientCachesIfNeeded();
        long now = System.currentTimeMillis();
        Long blockedUntil = NEGATIVE_CACHE_UNTIL.get(cacheKey);
        if (blockedUntil != null) {
            if (blockedUntil > now) {
                return CompletableFuture.completedFuture(null);
            }
            NEGATIVE_CACHE_UNTIL.remove(cacheKey, blockedUntil);
        }

        Image cached = IMAGE_CACHE.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        Image diskCached = loadImageFromDisk(cacheKey, normalizedCaller);
        if (diskCached != null) {
            IMAGE_CACHE.put(cacheKey, diskCached);
            return CompletableFuture.completedFuture(diskCached);
        }

        return LOADING_TASKS.computeIfAbsent(cacheKey, k -> {
            return CompletableFuture.supplyAsync(() -> fetchImageWithFallback(url, cacheKey, normalizedCaller))
                    .exceptionally(e -> {
                        logImageIssue(url, "Failed to load image: " + e.getMessage());
                        NEGATIVE_CACHE_UNTIL.put(cacheKey, System.currentTimeMillis() + NEGATIVE_CACHE_MS_ERROR);
                        return null;
                    }).whenComplete((img, ex) -> LOADING_TASKS.remove(cacheKey));
        });
    }

    private static Image fetchImageWithFallback(String url, String cacheKey, String caller) {
        List<String> candidates = new ArrayList<>();
        candidates.add(url);
        if (url.startsWith("http://")) {
            candidates.add("https://" + url.substring("http://".length()));
        }

        for (String candidate : candidates) {
            try {
                ImagePayload payload = fetchSingleImage(candidate);
                if (payload != null && payload.image != null) {
                    IMAGE_CACHE.put(cacheKey, payload.image);
                    persistImageToDisk(cacheKey, caller, payload.bytes);
                    NEGATIVE_CACHE_UNTIL.remove(cacheKey);
                    return payload.image;
                }
            } catch (HttpStatusException e) {
                if (e.statusCode == 404) {
                    // Try next candidate (e.g. https fallback). If this was the last one, negative-cache it.
                    if (candidate.equals(candidates.get(candidates.size() - 1))) {
                        NEGATIVE_CACHE_UNTIL.put(cacheKey, System.currentTimeMillis() + NEGATIVE_CACHE_MS_404);
                    }
                } else if (e.statusCode == 429) {
                    long retryAfterMs = e.retryAfterMs > 0 ? e.retryAfterMs : NEGATIVE_CACHE_MS_429_DEFAULT;
                    NEGATIVE_CACHE_UNTIL.put(cacheKey, System.currentTimeMillis() + retryAfterMs);
                    setHostBackoff(candidate, retryAfterMs);
                } else {
                    logImageIssue(candidate, "Image HTTP status: " + e.statusCode);
                }
            } catch (Exception e) {
                if (candidate.equals(candidates.get(candidates.size() - 1))) {
                    NEGATIVE_CACHE_UNTIL.put(cacheKey, System.currentTimeMillis() + NEGATIVE_CACHE_MS_ERROR);
                }
            }
        }
        return null;
    }

    private static ImagePayload fetchSingleImage(String url) throws Exception {
        if (isHostBackedOff(url)) {
            return null;
        }
        String host = hostOf(url);
        Semaphore semaphore = hostSemaphore(host);
        if (!semaphore.tryAcquire(3, TimeUnit.SECONDS)) {
            return null;
        }
        HttpGet request = new HttpGet(url);
        request.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(Duration.ofSeconds(15)))
                .setConnectTimeout(Timeout.of(Duration.ofSeconds(8)))
                .setResponseTimeout(Timeout.of(Duration.ofSeconds(15)))
                .build());
        request.setHeader("User-Agent", "Mozilla/5.0");
        // Prefer JPEG/PNG for JavaFX compatibility; AVIF/WebP responses are often not decodable.
        request.setHeader("Accept", "image/jpeg,image/png,image/*;q=0.8,*/*;q=0.5");
        request.setHeader("Accept-Language", "en-US,en;q=0.8");

        try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
            if (response.getCode() >= 400) {
                long retryAfterMs = parseRetryAfterMs(response);
                throw new HttpStatusException(response.getCode(), retryAfterMs);
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return null;
            }

            try (InputStream inputStream = entity.getContent()) {
                byte[] imageBytes = inputStream.readAllBytes();
                if (imageBytes.length == 0) {
                    return null;
                }
                Image image = new Image(
                        new ByteArrayInputStream(imageBytes),
                        MAX_IMAGE_DECODE_WIDTH,
                        MAX_IMAGE_DECODE_HEIGHT,
                        true,
                        true
                );
                if (image.isError()) {
                    return null;
                }
                return new ImagePayload(image, imageBytes);
            }
        } finally {
            semaphore.release();
        }
    }

    private static Path resolveDiskCacheDir() {
        List<Path> candidates = new ArrayList<>();
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null && !tmpDir.isBlank()) {
            candidates.add(Path.of(tmpDir, "uiptv-image-cache"));
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            candidates.add(Path.of(userHome, ".uiptv", "cache", "images"));
        }
        for (Path candidate : candidates) {
            try {
                Files.createDirectories(candidate);
                if (Files.isWritable(candidate)) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void persistImageToDisk(String cacheKey, String caller, byte[] bytes) {
        if (DISK_CACHE_DIR == null || bytes == null || bytes.length == 0) {
            return;
        }
        try {
            Path path = cacheFilePath(cacheKey, caller);
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            trimDiskCacheIfNeeded();
        } catch (Exception ignored) {
        }
    }

    private static Image loadImageFromDisk(String cacheKey, String caller) {
        if (DISK_CACHE_DIR == null) {
            return null;
        }
        try {
            Path path = cacheFilePath(cacheKey, caller);
            if (!Files.exists(path)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                Files.deleteIfExists(path);
                return null;
            }
            Image image = new Image(
                    new ByteArrayInputStream(bytes),
                    MAX_IMAGE_DECODE_WIDTH,
                    MAX_IMAGE_DECODE_HEIGHT,
                    true,
                    true
            );
            if (image.isError()) {
                Files.deleteIfExists(path);
                return null;
            }
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void trimDiskCacheIfNeeded() {
        if (DISK_CACHE_DIR == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDiskTrimMs < DISK_CACHE_TRIM_INTERVAL_MS) {
            return;
        }
        synchronized (DISK_TRIM_LOCK) {
            long insideLockNow = System.currentTimeMillis();
            if (insideLockNow - lastDiskTrimMs < DISK_CACHE_TRIM_INTERVAL_MS) {
                return;
            }
            try {
                List<DiskEntry> entries = new ArrayList<>();
                try (var paths = Files.list(DISK_CACHE_DIR)) {
                    paths.filter(Files::isRegularFile).forEach(path -> {
                        try {
                            long size = Files.size(path);
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            entries.add(new DiskEntry(path, size, modified));
                        } catch (Exception ignored) {
                        }
                    });
                }
                long totalBytes = 0L;
                for (DiskEntry entry : entries) {
                    totalBytes += entry.size;
                }
                if (totalBytes <= DISK_CACHE_MAX_BYTES) {
                    lastDiskTrimMs = insideLockNow;
                    return;
                }

                entries.sort(Comparator.comparingLong(entry -> entry.lastModifiedMs));
                for (DiskEntry entry : entries) {
                    if (totalBytes <= DISK_CACHE_TRIM_TO_BYTES) {
                        break;
                    }
                    try {
                        if (Files.deleteIfExists(entry.path)) {
                            totalBytes -= entry.size;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            } finally {
                lastDiskTrimMs = insideLockNow;
            }
        }
    }

    private static Path cacheFilePath(String cacheKey, String caller) {
        String callerHash = sha256Hex(normalizeCaller(caller));
        String keyHash = sha256Hex(cacheKey);
        return DISK_CACHE_DIR.resolve(callerHash + "_" + keyHash + ".img");
    }

    private static String normalizeCaller(String caller) {
        if (caller == null || caller.isBlank()) {
            return "global";
        }
        return caller.trim().toLowerCase();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static class HttpStatusException extends Exception {
        private final int statusCode;
        private final long retryAfterMs;

        private HttpStatusException(int statusCode, long retryAfterMs) {
            this.statusCode = statusCode;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static long parseRetryAfterMs(CloseableHttpResponse response) {
        try {
            String raw = response.getHeader("Retry-After") != null ? response.getHeader("Retry-After").getValue() : "";
            if (raw == null || raw.isBlank()) {
                return NEGATIVE_CACHE_MS_429_DEFAULT;
            }
            long secs = Long.parseLong(raw.trim());
            return Math.max(10_000L, secs * 1000L);
        } catch (Exception ignored) {
            return NEGATIVE_CACHE_MS_429_DEFAULT;
        }
    }

    private static void setHostBackoff(String url, long retryAfterMs) {
        String host = hostOf(url);
        if (host.isBlank()) {
            return;
        }
        HOST_BACKOFF_UNTIL.put(host, System.currentTimeMillis() + Math.max(retryAfterMs, NEGATIVE_CACHE_MS_429_DEFAULT));
    }

    private static void logImageIssue(String url, String message) {
        String host = hostOf(url);
        String key = host.isBlank() ? url : host;
        long now = System.currentTimeMillis();
        Long mutedUntil = HOST_LOG_UNTIL.get(key);
        if (mutedUntil != null && mutedUntil > now) {
            return;
        }
        HOST_LOG_UNTIL.put(key, now + HOST_LOG_WINDOW_MS);
        LogDisplayUI.addLog(message + (host.isBlank() ? "" : " (" + host + ")"));
    }

    private static boolean isHostBackedOff(String url) {
        String host = hostOf(url);
        if (host.isBlank()) {
            return false;
        }
        Long blockedUntil = HOST_BACKOFF_UNTIL.get(host);
        if (blockedUntil == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (blockedUntil > now) {
            return true;
        }
        HOST_BACKOFF_UNTIL.remove(host, blockedUntil);
        return false;
    }

    private static Semaphore hostSemaphore(String host) {
        String key = host == null ? "" : host;
        return HOST_SEMAPHORES.computeIfAbsent(key, h -> new Semaphore(HOST_PARALLEL_LIMIT));
    }

    private static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void clearCache() {
        IMAGE_CACHE.clear();
        LOADING_TASKS.clear();
        NEGATIVE_CACHE_UNTIL.clear();
        HOST_BACKOFF_UNTIL.clear();
        HOST_SEMAPHORES.clear();
        HOST_LOG_UNTIL.clear();
        clearDiskCache();
    }

    public static void clearCache(String caller) {
        String normalizedCaller = normalizeCaller(caller);
        String prefix = normalizedCaller + ":";
        IMAGE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        LOADING_TASKS.keySet().removeIf(key -> key.startsWith(prefix));
        NEGATIVE_CACHE_UNTIL.keySet().removeIf(key -> key.startsWith(prefix));
        clearDiskCache(normalizedCaller);
    }

    private static void trimTransientCachesIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastTransientTrimMs < TRANSIENT_CACHE_TRIM_INTERVAL_MS) {
            return;
        }
        synchronized (TRANSIENT_TRIM_LOCK) {
            long insideLockNow = System.currentTimeMillis();
            if (insideLockNow - lastTransientTrimMs < TRANSIENT_CACHE_TRIM_INTERVAL_MS) {
                return;
            }
            pruneExpiringMap(NEGATIVE_CACHE_UNTIL, insideLockNow, NEGATIVE_CACHE_MAX_ENTRIES);
            pruneExpiringMap(HOST_BACKOFF_UNTIL, insideLockNow, HOST_STATE_MAX_ENTRIES);
            pruneExpiringMap(HOST_LOG_UNTIL, insideLockNow, HOST_STATE_MAX_ENTRIES);
            trimHostSemaphores();
            lastTransientTrimMs = insideLockNow;
        }
    }

    private static void pruneExpiringMap(Map<String, Long> map, long now, int maxEntries) {
        if (map.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> entry : new ArrayList<>(map.entrySet())) {
            Long until = entry.getValue();
            if (until == null || until <= now) {
                map.remove(entry.getKey(), until);
            }
        }
        if (map.size() <= maxEntries) {
            return;
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue() == null ? Long.MIN_VALUE : entry.getValue()));
        int toRemove = map.size() - maxEntries;
        for (Map.Entry<String, Long> entry : entries) {
            if (toRemove <= 0) {
                break;
            }
            if (map.remove(entry.getKey(), entry.getValue())) {
                toRemove--;
            }
        }
    }

    private static void trimHostSemaphores() {
        if (HOST_SEMAPHORES.size() <= HOST_STATE_MAX_ENTRIES) {
            return;
        }
        for (String host : new ArrayList<>(HOST_SEMAPHORES.keySet())) {
            if (HOST_SEMAPHORES.size() <= HOST_STATE_MAX_ENTRIES) {
                return;
            }
            if (!HOST_BACKOFF_UNTIL.containsKey(host) && !HOST_LOG_UNTIL.containsKey(host)) {
                HOST_SEMAPHORES.remove(host);
            }
        }
        if (HOST_SEMAPHORES.size() <= HOST_STATE_MAX_ENTRIES) {
            return;
        }
        int over = HOST_SEMAPHORES.size() - HOST_STATE_MAX_ENTRIES;
        for (String host : new ArrayList<>(HOST_SEMAPHORES.keySet())) {
            if (over <= 0) {
                break;
            }
            if (HOST_SEMAPHORES.remove(host) != null) {
                over--;
            }
        }
    }

    private static void clearDiskCache() {
        if (DISK_CACHE_DIR == null) {
            return;
        }
        try (var paths = Files.list(DISK_CACHE_DIR)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void clearDiskCache(String caller) {
        if (DISK_CACHE_DIR == null) {
            return;
        }
        String callerPrefix = sha256Hex(normalizeCaller(caller)) + "_";
        try (var paths = Files.list(DISK_CACHE_DIR)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(callerPrefix))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private static final class ImagePayload {
        private final Image image;
        private final byte[] bytes;

        private ImagePayload(Image image, byte[] bytes) {
            this.image = image;
            this.bytes = bytes;
        }
    }

    private static final class DiskEntry {
        private final Path path;
        private final long size;
        private final long lastModifiedMs;

        private DiskEntry(Path path, long size, long lastModifiedMs) {
            this.path = path;
            this.size = size;
            this.lastModifiedMs = lastModifiedMs;
        }
    }
}
