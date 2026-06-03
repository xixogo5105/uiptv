package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class BookmarkCard extends HBox {
    private final AsyncImageView imageView = new AsyncImageView();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final Label drmBadge = new Label(I18n.tr("autoDrm"));

    public BookmarkCard(String title,
                        String subtitle,
                        String logoUrl,
                        boolean loadImage,
                        String imageCacheName,
                        boolean drmProtected) {
        getStyleClass().add("bookmark-card");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeTextNode(titleLabel);
        UiRenderQuality.optimizeTextNode(subtitleLabel);
        UiRenderQuality.optimizeTextNode(drmBadge);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setMinHeight(78);
        setMaxHeight(Region.USE_PREF_SIZE);

        titleLabel.getStyleClass().add("bookmark-channel-title");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setWrapText(true);
        titleLabel.setText(title == null ? "" : title);

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
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        VBox text = new VBox(4, titleRow, subtitleLabel);
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
}
