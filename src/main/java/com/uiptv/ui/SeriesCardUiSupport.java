package com.uiptv.ui;

import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.io.InputStream;

import static com.uiptv.util.StringUtils.isBlank;

public final class SeriesCardUiSupport {
    private static final String IMDB_LOGO_RESOURCE = "/icons/common/imdb-logo.png";
    private static final Image IMDB_LOGO_IMAGE = loadImdbLogoImage();

    private SeriesCardUiSupport() {
    }

    public static ImageView createCroppedPoster(String url, int width, int height, String cacheKey) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        if (!isBlank(url)) {
            ImageCacheManager.loadImageAsync(url, cacheKey).thenAccept(image -> {
                if (image != null) {
                    Platform.runLater(() -> {
                        imageView.setImage(image);
                        Rectangle2D viewport = centerCropViewport(image.getWidth(), image.getHeight(), width, height);
                        imageView.setViewport(viewport);
                        imageView.setPreserveRatio(false);
                        imageView.setFitWidth(width);
                        imageView.setFitHeight(height);
                    });
                }
            });
        }
        return imageView;
    }

    public static ImageView createFitPoster(String url, int width, int height, String cacheKey) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        if (!isBlank(url)) {
            ImageCacheManager.loadImageAsync(url, cacheKey).thenAccept(image -> {
                if (image != null) {
                    Platform.runLater(() -> {
                        imageView.setImage(image);
                        imageView.setViewport(null);
                        imageView.setPreserveRatio(true);
                        imageView.setFitWidth(width);
                        imageView.setFitHeight(height);
                    });
                }
            });
        }
        return imageView;
    }

    public static HBox createImdbRatingPill(String rating, String imdbUrl) {
        if (isBlank(rating) || isBlank(imdbUrl)) {
            return null;
        }
        HBox pill = new HBox(6);
        pill.setPadding(new Insets(4, 8, 4, 8));
        pill.setStyle("-fx-background-color: #f5c518; -fx-background-radius: 8; -fx-border-color: rgba(245,197,24,0.7); -fx-border-radius: 8;");
        pill.setMinWidth(Region.USE_PREF_SIZE);
        pill.setMaxWidth(Region.USE_PREF_SIZE);
        pill.setCursor(Cursor.HAND);

        ImageView logo = new ImageView();
        if (IMDB_LOGO_IMAGE != null) {
            logo.setImage(IMDB_LOGO_IMAGE);
            logo.setFitHeight(18);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
        }
        Label value = new Label(rating);
        value.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #111111;");
        pill.getChildren().addAll(logo, value);
        pill.setOnMouseClicked(e -> RootApplication.openInBrowser(imdbUrl));
        return pill;
    }

    private static Image loadImdbLogoImage() {
        try (InputStream input = SeriesCardUiSupport.class.getResourceAsStream(IMDB_LOGO_RESOURCE)) {
            if (input == null) {
                return null;
            }
            return new Image(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Rectangle2D centerCropViewport(double imageWidth, double imageHeight, double targetWidth, double targetHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }
        double imageRatio = imageWidth / imageHeight;
        double targetRatio = targetWidth / targetHeight;
        if (imageRatio > targetRatio) {
            double cropWidth = imageHeight * targetRatio;
            double x = (imageWidth - cropWidth) / 2.0;
            return new Rectangle2D(x, 0, cropWidth, imageHeight);
        }
        double cropHeight = imageWidth / targetRatio;
        double y = (imageHeight - cropHeight) / 2.0;
        return new Rectangle2D(0, y, imageWidth, cropHeight);
    }
}
