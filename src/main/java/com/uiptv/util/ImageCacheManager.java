package com.uiptv.util;

import javafx.scene.image.Image;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.LaxRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ImageCacheManager {

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setRedirectStrategy(new LaxRedirectStrategy())
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(5))
                    .setResponseTimeout(Timeout.ofSeconds(10))
                    .build())
            .build();
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Image>> LOADING_TASKS = new ConcurrentHashMap<>();

    public static CompletableFuture<Image> loadImageAsync(String url, String caller) {
        String cacheKey = caller + ":" + url;
        if (url == null || !url.startsWith("http")) {
            return CompletableFuture.completedFuture(null);
        }

        if (IMAGE_CACHE.containsKey(cacheKey)) {
            return CompletableFuture.completedFuture(IMAGE_CACHE.get(cacheKey));
        }

        return LOADING_TASKS.computeIfAbsent(cacheKey, k -> {
            try {
                HttpGet request = new HttpGet(url);
                request.setConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.of(Duration.ofSeconds(5)))
                        .setResponseTimeout(Timeout.of(Duration.ofSeconds(10)))
                        .build());
                request.setHeader("User-Agent", "Mozilla/5.0");
                request.setHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");

                return CompletableFuture.supplyAsync(() -> {
                            try {
                                return HTTP_CLIENT.execute(request, response -> {
                                    if (response.getCode() >= 400) {
                                        System.err.println("Failed to load image from " + url + ", status code: " + response.getCode());
                                        return null;
                                    }

                                    HttpEntity entity = response.getEntity();
                                    if (entity == null) {
                                        return null;
                                    }

                                    try (InputStream inputStream = entity.getContent()) {
                                        Image image = new Image(inputStream);
                                        if (image.isError()) {
                                            System.err.println("Failed to decode image from " + url);
                                            return null;
                                        }
                                        IMAGE_CACHE.put(cacheKey, image);
                                        return image;
                                    }
                                });
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .exceptionally(e -> {
                            System.err.println("Failed to load image from " + url + ": " + e.getMessage());
                            return null;
                        }).whenComplete((img, ex) -> LOADING_TASKS.remove(cacheKey));
            } catch (Exception e) {
                System.err.println("Failed to create request for " + url + ": " + e.getMessage());
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    public static void clearCache() {
        IMAGE_CACHE.clear();
        LOADING_TASKS.clear();
    }

    public static void clearCache(String caller) {
        String prefix = caller + ":";
        IMAGE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        LOADING_TASKS.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
