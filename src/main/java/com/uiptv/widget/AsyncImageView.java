package com.uiptv.widget;

import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.Objects;

public class AsyncImageView extends StackPane {
    private static final int IMAGE_VIEW_WIDTH = 36;
    private static final int IMAGE_VIEW_HEIGHT = 36;
    public static final String IMAGE_VIEW_STYLE_CSS = "channel-logo-view";
    private final ImageView imageView = new ImageView();
    private String currentUrl;

    public AsyncImageView() {
        // Style the StackPane itself to provide a background
        getStyleClass().add(IMAGE_VIEW_STYLE_CSS);
        setAlignment(Pos.CENTER);
        setPrefSize(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);
        setMinSize(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);
        setMaxSize(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);

        // Configure the internal ImageView
        // Make image slightly smaller than the container to ensure background border is visible
        imageView.setFitWidth(IMAGE_VIEW_WIDTH - 6); 
        imageView.setFitHeight(IMAGE_VIEW_HEIGHT - 6);
        imageView.setPreserveRatio(true);

        getChildren().add(imageView);
    }

    public void loadImage(String url, String type) {
        this.currentUrl = url;
        imageView.setImage(ImageCacheManager.DEFAULT_IMAGE);

        if (url == null || url.isEmpty()) {
            return;
        }

        ImageCacheManager.loadImageAsync(url, type)
                .thenAccept(image -> {
                    if (image != null && Objects.equals(url, this.currentUrl)) {
                        Platform.runLater(() -> {
                            // Final check inside the UI thread to prevent race conditions
                            if (Objects.equals(url, this.currentUrl)) {
                                imageView.setImage(image);
                            }
                        });
                    }
                });
    }
}
