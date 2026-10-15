package com.uiptv.widget;

import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.ui.util.ImageCacheManager;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncImageViewEpgFallbackTest {
    @BeforeAll static void initialize() throws Exception { FxTestSupport.initJavaFx(); }
    @Test void fallbackUsesExistingThumbnailCacheAndNeverReplacesAvailablePrimaryImage() throws Exception {
        runOnFxThread(() -> {
            try(var images=mockStatic(ImageCacheManager.class)) {
                var primary=new WritableImage(4,4); var fallback=new WritableImage(8,8);
                images.when(() -> ImageCacheManager.normalizeLoadUrl("https://example.test/primary.png")).thenReturn("primary");
                images.when(() -> ImageCacheManager.normalizeLoadUrl("https://example.test/epg.png")).thenReturn("epg");
                images.when(() -> ImageCacheManager.getCachedImage("channel:primary")).thenReturn(primary);
                images.when(() -> ImageCacheManager.getCachedImage("channel:epg")).thenReturn(fallback);
                var withPrimary=new AsyncImageView(); withPrimary.loadImage("https://example.test/primary.png","channel");
                withPrimary.setFallbackImage("https://example.test/epg.png","channel");
                assertSame(primary,((ImageView)withPrimary.getChildren().get(1)).getImage());
                var missing=new AsyncImageView(); missing.loadImage("","channel"); missing.setFallbackImage("https://example.test/epg.png","channel");
                assertSame(fallback,((ImageView)missing.getChildren().get(1)).getImage());
                missing.setFallbackImage("","channel"); assertNull(((ImageView)missing.getChildren().get(1)).getImage());
                missing.clearImage(); withPrimary.clearImage();
            }
            return null;
        });
    }
}
