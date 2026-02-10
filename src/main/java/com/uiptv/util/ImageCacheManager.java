package com.uiptv.util;

import javafx.scene.image.Image;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ImageCacheManager {

    public static final Image DEFAULT_IMAGE = new Image(ImageCacheManager.class.getResource("/icons/others/tv.png").toExternalForm());
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Image>> LOADING_TASKS = new ConcurrentHashMap<>();

    public static CompletableFuture<Image> loadImageAsync(String url, String caller) {
        String cacheKey = caller + ":" + url;
        if (url == null || !url.startsWith("http")) {
            return CompletableFuture.completedFuture(DEFAULT_IMAGE);
        }

        if (IMAGE_CACHE.containsKey(cacheKey)) {
            return CompletableFuture.completedFuture(IMAGE_CACHE.get(cacheKey));
        }

        return LOADING_TASKS.computeIfAbsent(cacheKey, k -> {
            try {
                URL urlObject = new URL(url);
                URI uri = new URI(urlObject.getProtocol(), urlObject.getUserInfo(), urlObject.getHost(), urlObject.getPort(), urlObject.getPath(), urlObject.getQuery(), urlObject.getRef());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        .GET()
                        .build();

                return HTTP_CLIENT
                        .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                        .thenApply(resp -> {
                            if (resp.statusCode() >= 400) {
                                System.err.println("Failed to load image from " + url + ", status code: " + resp.statusCode());
                                return DEFAULT_IMAGE;
                            }
                            Image image = new Image(resp.body());
                            if (image.isError()) {
                                System.err.println("Failed to decode image from " + url);
                                return DEFAULT_IMAGE;
                            }
                            IMAGE_CACHE.put(cacheKey, image);
                            return image;
                        })
                        .exceptionally(e -> {
                            System.err.println("Failed to load image from " + url + ": " + e.getMessage());
                            return DEFAULT_IMAGE;
                        }).whenComplete((img, ex) -> LOADING_TASKS.remove(cacheKey));
            } catch (Exception e) {
                System.err.println("Failed to create URI from " + url + ": " + e.getMessage());
                return CompletableFuture.completedFuture(DEFAULT_IMAGE);
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
