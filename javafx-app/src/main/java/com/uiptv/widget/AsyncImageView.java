package com.uiptv.widget;

import com.uiptv.ui.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AsyncImageView extends StackPane {
    private static final int IMAGE_VIEW_WIDTH = 48;
    private static final int IMAGE_VIEW_HEIGHT = 48;
    private static final double IMAGE_PADDING = 6.0;
    private static final double ICON_PADDING = 12.0;
    public static final String IMAGE_VIEW_STYLE_CSS = "channel-logo-view";
    public static final String HAS_IMAGE_STYLE_CSS = "has-image";
    private static final String SVG_PATH = "M95.9,106.4l74.8,43.2l-74.8,42.7V106.4L95.9,106.4z M224.4,213.9V85.3H31.6v128.5H224.4z M224.4,63.7c5.7,0,10.7,2.1,15.1,6.3c4.4,4.2,6.5,9.3,6.5,15.3v128.5c0,5.7-2.2,10.6-6.5,14.8c-4.4,4.2-9.4,6.3-15.1,6.3H31.6c-5.7,0-10.7-2.1-15.1-6.3c-4.4-4.2-6.5-9.1-6.5-14.8V85.3c0-6,2.2-11.1,6.5-15.3c4.4-4.2,9.4-6.3,15.1-6.3h81.3L77.8,28.6l7.5-7.5L128,63.7L170.7,21l7.5,7.5l-35.2,35.2H224.4z";

    private final ImageView imageView = new ImageView();
    private final Region defaultIcon = new Region();
    @SuppressWarnings("java:S1450")
    private String currentUrl;
    private volatile javafx.animation.PauseTransition loadDebounce;
    private static volatile boolean scrolling = false;
    private static final ConcurrentLinkedQueue<AsyncImageView> PENDING_UI_UPDATES = new ConcurrentLinkedQueue<>();
    private static volatile boolean uiUpdateScheduled = false;
    private static final Object UI_UPDATE_LOCK = new Object();
    private volatile javafx.scene.image.Image pendingImage;
    private volatile String pendingUrl;

    public static void setScrolling(boolean active) {
        scrolling = active;
    }

    private static long scrollDebounceMillis() {
        return scrolling ? 400 : 300;
    }

    public AsyncImageView() {
        getStyleClass().add(IMAGE_VIEW_STYLE_CSS);
        setAlignment(Pos.CENTER);
        setPrefSize(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);
        setMinSize(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);
        setMaxSize(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);

        imageView.setFitWidth(IMAGE_VIEW_WIDTH - IMAGE_PADDING);
        imageView.setFitHeight(IMAGE_VIEW_HEIGHT - IMAGE_PADDING);
        imageView.setPreserveRatio(true);
        imageView.setVisible(false);

        defaultIcon.setShape(new javafx.scene.shape.SVGPath());
        ((javafx.scene.shape.SVGPath) defaultIcon.getShape()).setContent(SVG_PATH);
        defaultIcon.getStyleClass().add("default-channel-icon");
        defaultIcon.setMaxSize(IMAGE_VIEW_WIDTH - ICON_PADDING, IMAGE_VIEW_HEIGHT - ICON_PADDING);
        defaultIcon.setMinSize(IMAGE_VIEW_WIDTH - ICON_PADDING, IMAGE_VIEW_HEIGHT - ICON_PADDING);
        defaultIcon.setPrefSize(IMAGE_VIEW_WIDTH - ICON_PADDING, IMAGE_VIEW_HEIGHT - ICON_PADDING);
        defaultIcon.getStyleClass().add("default-channel-icon-shape");

        getChildren().addAll(defaultIcon, imageView);
    }

    public void loadImage(String url, String type) {
        if (url == null || url.isEmpty()) {
            clearImage();
            return;
        }
        if (Objects.equals(url, this.currentUrl) && imageView.getImage() != null) {
            return;
        }

        this.currentUrl = url;

        String cacheKey = type.toLowerCase(Locale.ROOT) + ":" + ImageCacheManager.normalizeLoadUrl(url);
        Image cached = ImageCacheManager.getCachedImage(cacheKey);
        if (cached != null) {
            applyImage(cached);
            return;
        }

        if (loadDebounce == null) {
            loadDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(scrollDebounceMillis()));
        } else {
            loadDebounce.setDuration(javafx.util.Duration.millis(scrollDebounceMillis()));
        }
        loadDebounce.setOnFinished(_ -> scheduleLoad(url, type));
        loadDebounce.playFromStart();
    }

    private void applyImage(Image image) {
        imageView.setImage(image);
        imageView.setVisible(true);
        defaultIcon.setVisible(false);
        if (!getStyleClass().contains(HAS_IMAGE_STYLE_CSS)) {
            getStyleClass().add(HAS_IMAGE_STYLE_CSS);
        }
    }

    private void scheduleLoad(String url, String type) {
        if (!Objects.equals(url, this.currentUrl)) {
            return;
        }
        if (scrolling) {
            rescheduleDebouncedLoad(url, type);
            return;
        }
        loadImageAsync(url, type);
    }

    private void rescheduleDebouncedLoad(String url, String type) {
        if (loadDebounce == null) {
            loadDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(scrollDebounceMillis()));
        } else {
            loadDebounce.setDuration(javafx.util.Duration.millis(scrollDebounceMillis()));
        }
        loadDebounce.setOnFinished(_ -> scheduleLoad(url, type));
        loadDebounce.playFromStart();
    }

    private void loadImageAsync(String url, String type) {
        ImageCacheManager.loadImageAsync(url, type)
                .thenAccept(image -> {
                    if (image != null && Objects.equals(url, this.currentUrl)) {
                        pendingImage = image;
                        pendingUrl = url;
                        queueUiUpdate();
                    }
                });
    }

    private void queueUiUpdate() {
        synchronized (UI_UPDATE_LOCK) {
            PENDING_UI_UPDATES.add(this);
            if (!uiUpdateScheduled) {
                uiUpdateScheduled = true;
                Platform.runLater(this::processPendingUiUpdates);
            }
        }
    }

    private void processPendingUiUpdates() {
        synchronized (UI_UPDATE_LOCK) {
            uiUpdateScheduled = false;
            AsyncImageView view;
            while ((view = PENDING_UI_UPDATES.poll()) != null) {
                view.applyLoadedImage();
            }
        }
    }

    private void applyLoadedImage() {
        if (pendingImage == null || !Objects.equals(pendingUrl, currentUrl)) {
            pendingImage = null;
            pendingUrl = null;
            return;
        }
        Image image = pendingImage;
        pendingImage = null;
        pendingUrl = null;
        imageView.setImage(image);
        imageView.setVisible(true);
        defaultIcon.setVisible(false);
        if (!getStyleClass().contains(HAS_IMAGE_STYLE_CSS)) {
            getStyleClass().add(HAS_IMAGE_STYLE_CSS);
        }
    }

    public void clearImage() {
        if (loadDebounce != null) {
            loadDebounce.stop();
        }
        this.currentUrl = null;
        this.pendingImage = null;
        this.pendingUrl = null;
        imageView.setImage(null);
        imageView.setVisible(false);
        defaultIcon.setVisible(true);
        getStyleClass().remove(HAS_IMAGE_STYLE_CSS);
    }
}
