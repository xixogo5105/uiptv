package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class BookmarkCard extends HBox {
    private final AsyncImageView imageView = new AsyncImageView();
    private final Label subtitleLabel = new Label();
    private final Label drmBadge = new Label(I18n.tr("autoDrm"));

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
        this(title, "", subtitle, logoUrl, loadImage, imageCacheName, drmProtected, trailingAction);
    }

    public BookmarkCard(String title,
                        String titleSuffix,
                        String subtitle,
                        String logoUrl,
                        boolean loadImage,
                        String imageCacheName,
                        boolean drmProtected,
                        Node trailingAction) {
        getStyleClass().add("bookmark-card");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeTextNode(subtitleLabel);
        UiRenderQuality.optimizeTextNode(drmBadge);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setMinHeight(104);
        setMaxHeight(Region.USE_PREF_SIZE);

        subtitleLabel.getStyleClass().add("bookmark-channel-account");
        subtitleLabel.setMinWidth(0);
        subtitleLabel.setMaxWidth(Double.MAX_VALUE);
        subtitleLabel.setWrapText(true);
        String safeSubtitle = subtitle == null ? "" : subtitle;
        subtitleLabel.setText(safeSubtitle);
        subtitleLabel.setVisible(!safeSubtitle.isBlank());
        subtitleLabel.setManaged(!safeSubtitle.isBlank());

        drmBadge.getStyleClass().add("drm-badge");
        drmBadge.setVisible(drmProtected);
        drmBadge.setManaged(drmProtected);

        TextFlow titleFlow = createTitleFlow(title, titleSuffix);
        HBox titleRow = new HBox(6, titleFlow, drmBadge);
        UiRenderQuality.optimizeLayout(titleRow);
        titleRow.setAlignment(Pos.TOP_LEFT);
        titleRow.setFillHeight(false);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleFlow, Priority.ALWAYS);

        if (trailingAction != null) {
            trailingAction.getStyleClass().add("bookmark-card-title-action");
            titleRow.getChildren().add(trailingAction);
        }

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

    private TextFlow createTitleFlow(String title, String titleSuffix) {
        String safeTitle = title == null ? "" : title;
        String safeSuffix = titleSuffix == null ? "" : titleSuffix;
        Text titleText = new Text(safeTitle);
        titleText.getStyleClass().add("bookmark-channel-title-text");
        UiRenderQuality.optimizeTextNode(titleText);

        TextFlow titleFlow = new TextFlow(titleText);
        titleFlow.getStyleClass().add("bookmark-title-flow");
        titleFlow.setMinWidth(0);
        titleFlow.setMaxWidth(Double.MAX_VALUE);
        titleFlow.setLineSpacing(1.5);
        UiRenderQuality.optimizeLayout(titleFlow);

        if (!safeSuffix.isBlank()) {
            Text suffixText = new Text(safeTitle.isBlank() ? safeSuffix : " " + safeSuffix);
            suffixText.getStyleClass().add("bookmark-account-suffix-text");
            UiRenderQuality.optimizeTextNode(suffixText);
            titleFlow.getChildren().add(suffixText);
        }
        return titleFlow;
    }
}
