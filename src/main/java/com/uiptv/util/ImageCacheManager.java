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
import java.time.Duration;
import java.util.ArrayList;
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
    private static final Map<String, Long> NEGATIVE_CACHE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<String, Long> HOST_BACKOFF_UNTIL = new ConcurrentHashMap<>();
    private static final Map<String, Semaphore> HOST_SEMAPHORES = new ConcurrentHashMap<>();
    private static final Map<String, Long> HOST_LOG_UNTIL = new ConcurrentHashMap<>();
    private static final int HOST_PARALLEL_LIMIT = 2;
    private static final long HOST_LOG_WINDOW_MS = 60_000L;

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
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Image>> LOADING_TASKS = new ConcurrentHashMap<>();

    public static CompletableFuture<Image> loadImageAsync(String url, String caller) {
        String cacheKey = caller + ":" + url;
        if (url == null || !url.startsWith("http")) {
            return CompletableFuture.completedFuture(null);
        }
        Long blockedUntil = NEGATIVE_CACHE_UNTIL.get(cacheKey);
        if (blockedUntil != null && blockedUntil > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(null);
        }

        if (IMAGE_CACHE.containsKey(cacheKey)) {
            return CompletableFuture.completedFuture(IMAGE_CACHE.get(cacheKey));
        }

        return LOADING_TASKS.computeIfAbsent(cacheKey, k -> {
            return CompletableFuture.supplyAsync(() -> fetchImageWithFallback(url, cacheKey))
                    .exceptionally(e -> {
                        logImageIssue(url, "Failed to load image: " + e.getMessage());
                        NEGATIVE_CACHE_UNTIL.put(cacheKey, System.currentTimeMillis() + NEGATIVE_CACHE_MS_ERROR);
                        return null;
                    }).whenComplete((img, ex) -> LOADING_TASKS.remove(cacheKey));
        });
    }

    private static Image fetchImageWithFallback(String url, String cacheKey) {
        List<String> candidates = new ArrayList<>();
        candidates.add(url);
        if (url.startsWith("http://")) {
            candidates.add("https://" + url.substring("http://".length()));
        }

        for (String candidate : candidates) {
            try {
                Image image = fetchSingleImage(candidate);
                if (image != null) {
                    IMAGE_CACHE.put(cacheKey, image);
                    NEGATIVE_CACHE_UNTIL.remove(cacheKey);
                    return image;
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

    private static Image fetchSingleImage(String url) throws Exception {
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
                Image image = new Image(new ByteArrayInputStream(imageBytes));
                if (image.isError()) {
                    return null;
                }
                return image;
            }
        } finally {
            semaphore.release();
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
        return blockedUntil > System.currentTimeMillis();
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
    }

    public static void clearCache(String caller) {
        String prefix = caller + ":";
        IMAGE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        LOADING_TASKS.keySet().removeIf(key -> key.startsWith(prefix));
        NEGATIVE_CACHE_UNTIL.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
