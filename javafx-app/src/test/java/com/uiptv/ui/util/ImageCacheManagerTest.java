package com.uiptv.ui.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import com.uiptv.ui.ThumbnailAwareUI;
import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCacheManagerTest {
    private static final String PNG_1X1_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAF/wJ+gN3W1QAAAABJRU5ErkJggg==";
    private static final byte[] PNG_1X1_BYTES = Base64.getDecoder().decode(PNG_1X1_BASE64);

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
            cache.put("watching-now:http://image.test/" + i + ".png", mock(Image.class));
        }

        assertTrue(cache.size() <= maxEntries);
        assertFalse(cache.containsKey("watching-now:http://image.test/0.png"));
    }

    @Test
    void scopedClearRemovesMatchingMemoryAndLoadingEntriesOnly() throws Exception {
        Map<String, Image> cache = imageCache();
        Map<String, CompletableFuture<Image>> loadingTasks = loadingTasks();
        ImageCacheManager.clearCache();
        Image watchingNowImage = mock(Image.class);
        Image otherImage = mock(Image.class);
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

    @Test
    void inlineDataImagesAreLoadedOffCallerThread() throws Exception {
        LoaderBlock loaderBlock = blockImageLoader();
        String url = "data:image/png;base64," + PNG_1X1_BASE64;
        String cacheKey = "channel:" + url;
        CompletableFuture<Image> future = null;

        try (MockedStatic<ThumbnailAwareUI> thumbnails = mockStatic(ThumbnailAwareUI.class)) {
            thumbnails.when(ThumbnailAwareUI::areThumbnailsEnabled).thenReturn(true);
            future = ImageCacheManager.loadImageAsync(url, "Channel");

            assertFalse(future.isDone());
            assertTrue(loadingTasks().containsKey(cacheKey));
        } finally {
            loaderBlock.release();
        }
        if (future != null) {
            future.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void diskCachedImagesAreLoadedOffCallerThread() throws Exception {
        String url = "http://image.test/logo.png";
        String normalizedCaller = "channel";
        String cacheKey = normalizedCaller + ":" + url;
        Path cacheFile = cacheFilePath(cacheKey, normalizedCaller);
        Files.createDirectories(cacheFile.getParent());
        Files.write(cacheFile, PNG_1X1_BYTES);
        LoaderBlock loaderBlock = blockImageLoader();
        CompletableFuture<Image> future = null;

        try (MockedStatic<ThumbnailAwareUI> thumbnails = mockStatic(ThumbnailAwareUI.class)) {
            thumbnails.when(ThumbnailAwareUI::areThumbnailsEnabled).thenReturn(true);
            future = ImageCacheManager.loadImageAsync(url, "Channel");

            assertFalse(future.isDone());
            assertTrue(loadingTasks().containsKey(cacheKey));
        } finally {
            loaderBlock.release();
        }
        if (future != null) {
            future.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void localFileImagesAreAcceptedByLoader() throws Exception {
        Path imageFile = Files.createTempFile("uiptv-image-cache-manager", ".png");
        Files.write(imageFile, PNG_1X1_BYTES);

        try (MockedStatic<ThumbnailAwareUI> thumbnails = mockStatic(ThumbnailAwareUI.class)) {
            thumbnails.when(ThumbnailAwareUI::areThumbnailsEnabled).thenReturn(true);

            assertDoesNotThrow(() -> {
                Image image = ImageCacheManager.loadImageAsync(imageFile.toUri().toString(), "Channel")
                        .get(3, TimeUnit.SECONDS);
                assertTrue(image == null || !image.isError());
            });
        } finally {
            Files.deleteIfExists(imageFile);
        }
    }

    @Test
    void supportedImageUrlsAreMatchedCaseInsensitively() throws Exception {
        assertTrue((Boolean) invoke("isSupportedImageUrl", new Class[]{String.class}, "HTTPS://image.test/logo.png"));
        assertTrue((Boolean) invoke("isSupportedImageUrl", new Class[]{String.class}, "FILE:/tmp/logo.png"));
        assertTrue((Boolean) invoke("isSupportedImageUrl", new Class[]{String.class}, "DATA:image/png;base64," + PNG_1X1_BASE64));
        assertFalse((Boolean) invoke("isSupportedImageUrl", new Class[]{String.class}, "ftp://image.test/logo.png"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void imageCandidatesUpgradeHttpCaseInsensitively() throws Exception {
        java.util.List<String> candidates = (java.util.List<String>) invoke(
                "buildImageCandidates",
                new Class[]{String.class},
                "HTTP://image.test/logo.png"
        );

        assertTrue(candidates.contains("HTTP://image.test/logo.png"));
        assertTrue(candidates.contains("https://image.test/logo.png"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Image> imageCache() throws Exception {
        return (Map<String, Image>) staticField("IMAGE_CACHE").get(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, CompletableFuture<Image>> loadingTasks() throws Exception {
        return (Map<String, CompletableFuture<Image>>) staticField("LOADING_TASKS").get(null);
    }

    private ThreadPoolExecutor imageLoader() throws Exception {
        return (ThreadPoolExecutor) staticField("IMAGE_LOADER").get(null);
    }

    private Field staticField(String name) throws Exception {
        Field field = ImageCacheManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private Path cacheFilePath(String cacheKey, String caller) throws Exception {
        Method method = ImageCacheManager.class.getDeclaredMethod("cacheFilePath", String.class, String.class);
        method.setAccessible(true);
        return (Path) method.invoke(null, cacheKey, caller);
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ImageCacheManager.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private LoaderBlock blockImageLoader() throws Exception {
        ThreadPoolExecutor executor = imageLoader();
        int workers = executor.getMaximumPoolSize();
        CountDownLatch started = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < workers; i++) {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertTrue(started.await(3, TimeUnit.SECONDS));
        return new LoaderBlock(release);
    }

    private record LoaderBlock(CountDownLatch releaseLatch) {
        private void release() {
            releaseLatch.countDown();
        }
    }
}
