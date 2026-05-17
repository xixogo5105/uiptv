package com.uiptv.ui.util;

import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCacheManagerTest {

    @AfterEach
    void tearDown() {
        ImageCacheManager.clearCache();
    }

    @Test
    void inMemoryImageCacheIsBounded() throws Exception {
        Map<String, Image> cache = imageCache();
        ImageCacheManager.clearCache();
        int maxEntries = Integer.getInteger("uiptv.image.cache.max.entries", 120);

        for (int i = 0; i < maxEntries + 10; i++) {
            cache.put("watching-now:http://image.test/" + i + ".png", Mockito.mock(Image.class));
        }

        assertTrue(cache.size() <= maxEntries);
        assertFalse(cache.containsKey("watching-now:http://image.test/0.png"));
    }

    @Test
    void scopedClearRemovesMatchingMemoryAndLoadingEntriesOnly() throws Exception {
        Map<String, Image> cache = imageCache();
        Map<String, CompletableFuture<Image>> loadingTasks = loadingTasks();
        ImageCacheManager.clearCache();
        Image watchingNowImage = Mockito.mock(Image.class);
        Image otherImage = Mockito.mock(Image.class);
        CompletableFuture<Image> watchingNowFuture = new CompletableFuture<>();
        CompletableFuture<Image> otherFuture = new CompletableFuture<>();

        cache.put("watching-now:http://image.test/a.png", watchingNowImage);
        cache.put("bookmark:http://image.test/b.png", otherImage);
        loadingTasks.put("watching-now:http://image.test/loading-a.png", watchingNowFuture);
        loadingTasks.put("bookmark:http://image.test/loading-b.png", otherFuture);

        ImageCacheManager.clearCache("Watching-Now");

        assertFalse(cache.containsKey("watching-now:http://image.test/a.png"));
        assertSame(otherImage, cache.get("bookmark:http://image.test/b.png"));
        assertTrue(watchingNowFuture.isCancelled());
        assertFalse(loadingTasks.containsKey("watching-now:http://image.test/loading-a.png"));
        assertSame(otherFuture, loadingTasks.get("bookmark:http://image.test/loading-b.png"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Image> imageCache() throws Exception {
        return (Map<String, Image>) staticField("IMAGE_CACHE").get(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, CompletableFuture<Image>> loadingTasks() throws Exception {
        return (Map<String, CompletableFuture<Image>>) staticField("LOADING_TASKS").get(null);
    }

    private Field staticField(String name) throws Exception {
        Field field = ImageCacheManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
