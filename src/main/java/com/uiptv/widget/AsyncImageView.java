package com.uiptv.widget;

import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Objects;

public class AsyncImageView extends StackPane {
    private static final int IMAGE_VIEW_WIDTH = 48;
    private static final int IMAGE_VIEW_HEIGHT = 48;
    public static final String IMAGE_VIEW_STYLE_CSS = "channel-logo-view";
    private static final String SVG_PATH = "M95.9,106.4l74.8,43.2l-74.8,42.7V106.4L95.9,106.4z M224.4,213.9V85.3H31.6v128.5H224.4z M224.4,63.7c5.7,0,10.7,2.1,15.1,6.3c4.4,4.2,6.5,9.3,6.5,15.3v128.5c0,5.7-2.2,10.6-6.5,14.8c-4.4,4.2-9.4,6.3-15.1,6.3H31.6c-5.7,0-10.7-2.1-15.1-6.3c-4.4-4.2-6.5-9.1-6.5-14.8V85.3c0-6,2.2-11.1,6.5-15.3c4.4-4.2,9.4-6.3,15.1-6.3h81.3L77.8,28.6l7.5-7.5L128,63.7L170.7,21l7.5,7.5l-35.2,35.2H224.4z";

    private final ImageView imageView = new ImageView();
    private final Region defaultIcon = new Region();
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
        imageView.setVisible(false);

        // Configure default icon
        defaultIcon.setShape(new javafx.scene.shape.SVGPath());
        ((javafx.scene.shape.SVGPath) defaultIcon.getShape()).setContent(SVG_PATH);
        defaultIcon.getStyleClass().add("default-channel-icon");
        defaultIcon.setMaxSize(IMAGE_VIEW_WIDTH - 12, IMAGE_VIEW_HEIGHT - 12);
        defaultIcon.setMinSize(IMAGE_VIEW_WIDTH - 12, IMAGE_VIEW_HEIGHT - 12);
        defaultIcon.setPrefSize(IMAGE_VIEW_WIDTH - 12, IMAGE_VIEW_HEIGHT - 12);
        // Set background color to apply to the shape
        defaultIcon.setStyle("-fx-background-color: -fx-text-fill;");

        getChildren().addAll(defaultIcon, imageView);
    }

    public void loadImage(String url, String type) {
        this.currentUrl = url;
        imageView.setVisible(false);
        defaultIcon.setVisible(true);

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
                                imageView.setVisible(true);
                                defaultIcon.setVisible(false);
                            }
                        });
                    }
                });
    }
}
