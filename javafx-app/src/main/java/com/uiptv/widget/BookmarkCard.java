package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class BookmarkCard extends HBox {
    private static final double TITLE_ROW_GAP = 6.0;
    private static final double MIN_FIRST_LINE_WIDTH = 34.0;

    private final AsyncImageView imageView = new AsyncImageView();
    private final Label titleLabel = new Label();
    private final Label titleContinuationLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final Label drmBadge = new Label(I18n.tr("autoDrm"));
    private final String fullTitle;

    public BookmarkCard(String title,
                        String subtitle,
                        String logoUrl,
                        boolean loadImage,
                        String imageCacheName,
                        boolean drmProtected) {
        this(title, subtitle, logoUrl, loadImage, imageCacheName, drmProtected, null);
    }

    public BookmarkCard(String title,
                        String subtitle,
                        String logoUrl,
                        boolean loadImage,
                        String imageCacheName,
                        boolean drmProtected,
                        Node trailingAction) {
        fullTitle = title == null ? "" : title;
        getStyleClass().add("bookmark-card");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeTextNode(titleLabel);
        UiRenderQuality.optimizeTextNode(titleContinuationLabel);
        UiRenderQuality.optimizeTextNode(subtitleLabel);
        UiRenderQuality.optimizeTextNode(drmBadge);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setMinHeight(104);
        setMaxHeight(Region.USE_PREF_SIZE);

        titleLabel.getStyleClass().add("bookmark-channel-title");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setWrapText(false);
        titleLabel.setText(fullTitle);

        titleContinuationLabel.getStyleClass().add("bookmark-channel-title");
        titleContinuationLabel.setMinWidth(0);
        titleContinuationLabel.setMaxWidth(Double.MAX_VALUE);
        titleContinuationLabel.setWrapText(true);
        titleContinuationLabel.setVisible(false);
        titleContinuationLabel.setManaged(false);

        subtitleLabel.getStyleClass().add("bookmark-channel-account");
        subtitleLabel.setMinWidth(0);
        subtitleLabel.setMaxWidth(Double.MAX_VALUE);
        subtitleLabel.setWrapText(true);
        subtitleLabel.setText(subtitle == null ? "" : subtitle);

        drmBadge.getStyleClass().add("drm-badge");
        drmBadge.setVisible(drmProtected);
        drmBadge.setManaged(drmProtected);

        HBox titleRow = new HBox(6, titleLabel, drmBadge);
        UiRenderQuality.optimizeLayout(titleRow);
        titleRow.setAlignment(Pos.TOP_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        if (trailingAction != null) {
            trailingAction.getStyleClass().add("bookmark-card-title-action");
            titleRow.getChildren().add(trailingAction);
        }

        VBox titleBlock = new VBox(0, titleRow, titleContinuationLabel);
        UiRenderQuality.optimizeLayout(titleBlock);
        titleBlock.setMinWidth(0);
        titleBlock.setMaxWidth(Double.MAX_VALUE);
        titleBlock.widthProperty().addListener((_, _, width) ->
                updateTitleSplit(width.doubleValue(), trailingAction));
        titleLabel.fontProperty().addListener((_, _, _) ->
                updateTitleSplit(titleBlock.getWidth(), trailingAction));
        drmBadge.widthProperty().addListener((_, _, _) ->
                updateTitleSplit(titleBlock.getWidth(), trailingAction));
        if (trailingAction != null) {
            trailingAction.layoutBoundsProperty().addListener((_, _, _) ->
                    updateTitleSplit(titleBlock.getWidth(), trailingAction));
        }
        Platform.runLater(() -> updateTitleSplit(titleBlock.getWidth(), trailingAction));

        VBox text = new VBox(4, titleBlock, subtitleLabel);
        UiRenderQuality.optimizeLayout(text);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        if (!loadImage) {
            getChildren().add(text);
            return;
        }

        imageView.loadImage(logoUrl, imageCacheName);
        getChildren().addAll(imageView, text);
    }

    private void updateTitleSplit(double availableWidth, Node trailingAction) {
        if (availableWidth <= 0 || fullTitle.isBlank()) {
            titleLabel.setText(fullTitle);
            setContinuationText("");
            return;
        }

        double reservedWidth = 0;
        if (drmBadge.isManaged()) {
            reservedWidth += nodeWidth(drmBadge) + TITLE_ROW_GAP;
        }
        if (trailingAction != null && trailingAction.isManaged()) {
            reservedWidth += nodeWidth(trailingAction) + TITLE_ROW_GAP;
        }

        double firstLineWidth = Math.max(MIN_FIRST_LINE_WIDTH, availableWidth - reservedWidth);
        if (measureText(fullTitle, titleLabel.getFont()) <= firstLineWidth) {
            titleLabel.setText(fullTitle);
            setContinuationText("");
            return;
        }

        String prefix = firstLinePrefix(fullTitle, titleLabel.getFont(), firstLineWidth);
        if (prefix.length() >= fullTitle.length()) {
            titleLabel.setText(fullTitle);
            setContinuationText("");
            return;
        }

        titleLabel.setText(prefix);
        setContinuationText(fullTitle.substring(prefix.length()).stripLeading());
    }

    private void setContinuationText(String text) {
        boolean hasText = text != null && !text.isBlank();
        titleContinuationLabel.setText(hasText ? text : "");
        titleContinuationLabel.setVisible(hasText);
        titleContinuationLabel.setManaged(hasText);
    }

    private String firstLinePrefix(String text, Font font, double maxWidth) {
        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = text.substring(0, mid).stripTrailing();
            if (candidate.isEmpty() || measureText(candidate, font) <= maxWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (best >= text.length()) {
            return text;
        }

        int wordSplit = best;
        while (wordSplit > 0 && !Character.isWhitespace(text.charAt(wordSplit - 1))) {
            wordSplit--;
        }
        if (wordSplit >= Math.max(4, best / 2)) {
            return text.substring(0, wordSplit).stripTrailing();
        }
        return text.substring(0, Math.max(1, best)).stripTrailing();
    }

    private double measureText(String text, Font font) {
        Text measurement = new Text(text);
        measurement.setFont(font);
        return Math.ceil(measurement.getLayoutBounds().getWidth());
    }

    private double nodeWidth(Node node) {
        double width = node.getLayoutBounds().getWidth();
        if (width <= 0 && node instanceof Region region) {
            width = region.prefWidth(-1);
        }
        return Math.max(0, width);
    }
}
