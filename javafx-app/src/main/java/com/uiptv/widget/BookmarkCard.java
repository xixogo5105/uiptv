package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class BookmarkCard extends HBox {
    private final AsyncImageView imageView = new AsyncImageView();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final Label drmBadge = new Label(I18n.tr("autoDrm"));
    private final Pane spacer = new Pane();

    public BookmarkCard(String title,
                        String subtitle,
                        String logoUrl,
                        boolean loadImage,
                        String imageCacheName,
                        boolean drmProtected) {
        getStyleClass().add("bookmark-card");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setMinHeight(78);
        setMaxHeight(92);

        titleLabel.getStyleClass().add("bookmark-channel-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setText(title == null ? "" : title);

        subtitleLabel.getStyleClass().add("bookmark-channel-account");
        subtitleLabel.setMaxWidth(Double.MAX_VALUE);
        subtitleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        subtitleLabel.setText(subtitle == null ? "" : subtitle);

        drmBadge.getStyleClass().add("drm-badge");
        drmBadge.setVisible(drmProtected);
        drmBadge.setManaged(drmProtected);

        HBox titleRow = new HBox(6, titleLabel, drmBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        VBox text = new VBox(4, titleRow, subtitleLabel);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (loadImage) {
            imageView.loadImage(logoUrl, imageCacheName);
        } else {
            imageView.clearImage();
        }
        getChildren().addAll(imageView, text, spacer);
    }
}
