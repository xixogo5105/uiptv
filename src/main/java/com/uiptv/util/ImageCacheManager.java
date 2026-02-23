package com.uiptv.util;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ImageCacheManager {

    private static final long NEGATIVE_CACHE_MS_404 = 15L * 60L * 1000L;
    private static final long NEGATIVE_CACHE_MS_ERROR = 2L * 60L * 1000L;
    private static final Map<String, Long> NEGATIVE_CACHE_UNTIL = new ConcurrentHashMap<>();

    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER =
            PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(160)
                    .setMaxConnPerRoute(40)
                    .build();

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setConnectionManager(CONNECTION_MANAGER)
            .setRedirectStrategy(new LaxRedirectStrategy())
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
                        System.err.println("Failed to load image from " + url + ": " + e.getMessage());
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
                        System.err.println("Failed to load image from " + candidate + ", status code: 404");
                    }
                } else {
                    System.err.println("Failed to load image from " + candidate + ", status code: " + e.statusCode);
                }
            } catch (Exception e) {
                if (candidate.equals(candidates.get(candidates.size() - 1))) {
                    NEGATIVE_CACHE_UNTIL.put(cacheKey, System.currentTimeMillis() + NEGATIVE_CACHE_MS_ERROR);
                    System.err.println("Failed to load image from " + candidate + ": " + e.getMessage());
                }
            }
        }
        return null;
    }

    private static Image fetchSingleImage(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(Duration.ofSeconds(15)))
                .setConnectTimeout(Timeout.of(Duration.ofSeconds(8)))
                .setResponseTimeout(Timeout.of(Duration.ofSeconds(15)))
                .build());
        request.setHeader("User-Agent", "Mozilla/5.0");

        try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
            if (response.getCode() >= 400) {
                throw new HttpStatusException(response.getCode());
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
        }
    }

    private static class HttpStatusException extends Exception {
        private final int statusCode;

        private HttpStatusException(int statusCode) {
            this.statusCode = statusCode;
        }
    }

    public static void clearCache() {
        IMAGE_CACHE.clear();
        LOADING_TASKS.clear();
        NEGATIVE_CACHE_UNTIL.clear();
    }

    public static void clearCache(String caller) {
        String prefix = caller + ":";
        IMAGE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        LOADING_TASKS.keySet().removeIf(key -> key.startsWith(prefix));
        NEGATIVE_CACHE_UNTIL.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
