package com.uiptv.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class WatchingNowMediaCardFactory {
    static final String KEY_CARD_LABELS = "cardLabels";
    private static final double TITLE_ROW_GAP = 8;

    private WatchingNowMediaCardFactory() {
    }

    enum CardType {
        VOD("watching-now-vod-card"),
        SERIES("watching-now-series-card");

        private final String styleClass;

        CardType(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    enum PlotPlacement {
        DETAILS,
        FULL_WIDTH
    }

    static Builder builder(CardType type) {
        return new Builder(type);
    }

    static StackPane createPosterWrap(ImageView poster) {
        StackPane posterWrap = new StackPane(poster);
        posterWrap.getStyleClass().add("watching-now-card-poster-wrap");
        posterWrap.setAlignment(Pos.CENTER);
        posterWrap.setMinWidth(Region.USE_PREF_SIZE);
        posterWrap.setMaxWidth(Region.USE_PREF_SIZE);
        return posterWrap;
    }

    static Label createChip(String text) {
        Label chip = new Label(text == null ? "" : text);
        chip.getStyleClass().add("watching-now-card-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setMaxWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    static Label createPlotLabel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Label plot = new Label(text);
        plot.getStyleClass().add("watching-now-card-plot");
        plot.setWrapText(true);
        plot.setMaxWidth(Double.MAX_VALUE);
        plot.setMinWidth(0);
        plot.setMinHeight(Region.USE_PREF_SIZE);
        return plot;
    }

    static final class CardNodes {
        private final HBox card;
        private final VBox details;
        private final Label title;
        private final Label account;
        private final FlowPane metadataRow;
        private final StackPane posterWrap;

        private CardNodes(HBox card, VBox details, Label title, Label account, FlowPane metadataRow, StackPane posterWrap) {
            this.card = card;
            this.details = details;
            this.title = title;
            this.account = account;
            this.metadataRow = metadataRow;
            this.posterWrap = posterWrap;
        }

        HBox card() {
            return card;
        }

        VBox details() {
            return details;
        }

        Label title() {
            return title;
        }

        Label account() {
            return account;
        }

        FlowPane metadataRow() {
            return metadataRow;
        }

        StackPane posterWrap() {
            return posterWrap;
        }
    }

    static final class Builder {
        private final CardType type;
        private final List<Node> metadataNodes = new ArrayList<>();
        private final List<String> extraStyleClasses = new ArrayList<>();
        private String titleText = "";
        private String accountText = "";
        private ImageView poster;
        private boolean posterVisible = true;
        private Button actionButton;
        private Node plotNode;
        private PlotPlacement plotPlacement = PlotPlacement.DETAILS;
        private Node footerNode;
        private boolean focusTraversable;

        private Builder(CardType type) {
            this.type = type == null ? CardType.VOD : type;
        }

        Builder title(String titleText) {
            this.titleText = titleText == null ? "" : titleText;
            return this;
        }

        Builder account(String accountText) {
            this.accountText = accountText == null ? "" : accountText;
            return this;
        }

        Builder poster(ImageView poster, boolean visible) {
            this.poster = poster;
            this.posterVisible = visible;
            return this;
        }

        Builder actionButton(Button actionButton) {
            this.actionButton = actionButton;
            return this;
        }

        Builder metadataNodes(Collection<? extends Node> nodes) {
            if (nodes != null) {
                metadataNodes.addAll(nodes);
            }
            return this;
        }

        Builder plot(Node plotNode, PlotPlacement placement) {
            this.plotNode = plotNode;
            this.plotPlacement = placement == null ? PlotPlacement.DETAILS : placement;
            return this;
        }

        Builder footer(Node footerNode) {
            this.footerNode = footerNode;
            return this;
        }

        Builder focusTraversable(boolean focusTraversable) {
            this.focusTraversable = focusTraversable;
            return this;
        }

        Builder extraStyleClass(String styleClass) {
            if (styleClass != null && !styleClass.isBlank()) {
                extraStyleClasses.add(styleClass);
            }
            return this;
        }

        CardNodes build() {
            HBox card = new HBox(16);
            card.setAlignment(Pos.TOP_LEFT);
            card.setFocusTraversable(focusTraversable);
            card.setPadding(new Insets(14));
            card.setMinWidth(0);
            card.setMaxWidth(Double.MAX_VALUE);
            card.getStyleClass().add("uiptv-card");
            card.getStyleClass().add(type.styleClass);
            card.getStyleClass().addAll(extraStyleClasses);

            StackPane posterWrap = null;
            if (poster != null) {
                posterWrap = createPosterWrap(poster);
                posterWrap.setVisible(posterVisible);
                posterWrap.setManaged(posterVisible);
            }

            VBox details = new VBox(8);
            details.getStyleClass().add("watching-now-card-text");
            details.setMaxWidth(Double.MAX_VALUE);
            details.setMinWidth(0);
            details.setFillWidth(true);
            HBox.setHgrow(details, Priority.ALWAYS);

            Label title = createTitle();
            Label account = createAccount();
            Region titleSpacer = new Region();
            titleSpacer.setMinWidth(0);
            HBox.setHgrow(titleSpacer, Priority.ALWAYS);
            HBox titleRow = new HBox(TITLE_ROW_GAP);
            titleRow.setAlignment(Pos.TOP_LEFT);
            titleRow.setMinWidth(0);
            titleRow.setMaxWidth(Double.MAX_VALUE);

            details.getChildren().add(titleRow);
            installAdaptiveAccountPlacement(details, titleRow, title, account, titleSpacer, actionButton);
            FlowPane metadataRow = createMetadataRow();
            if (!metadataRow.getChildren().isEmpty()) {
                details.getChildren().add(metadataRow);
            }
            if (plotPlacement == PlotPlacement.DETAILS && plotNode != null) {
                prepareFlexibleNode(plotNode);
                details.getChildren().add(plotNode);
            }
            if (footerNode != null) {
                details.getChildren().add(footerNode);
            }

            HBox topRow = new HBox(16);
            topRow.setAlignment(Pos.TOP_LEFT);
            topRow.setMinWidth(0);
            topRow.setMaxWidth(Double.MAX_VALUE);
            if (posterWrap != null) {
                topRow.getChildren().add(posterWrap);
            }
            topRow.getChildren().add(details);
            HBox.setHgrow(details, Priority.ALWAYS);

            VBox cardBody = new VBox(4);
            cardBody.setMaxWidth(Double.MAX_VALUE);
            cardBody.setMinWidth(0);
            cardBody.setFillWidth(true);
            HBox.setHgrow(cardBody, Priority.ALWAYS);
            cardBody.getChildren().add(topRow);
            if (plotPlacement == PlotPlacement.FULL_WIDTH && plotNode != null) {
                prepareFlexibleNode(plotNode);
                if (plotNode instanceof Region region) {
                    region.prefWidthProperty().bind(cardBody.widthProperty().subtract(6));
                }
                cardBody.getChildren().add(plotNode);
            }

            card.getChildren().add(cardBody);
            return new CardNodes(card, details, title, account, metadataRow, posterWrap);
        }

        private void installAdaptiveAccountPlacement(VBox details,
                                                     HBox titleRow,
                                                     Label title,
                                                     Label account,
                                                     Region titleSpacer,
                                                     Button actionButton) {
            Runnable update = () -> updateAccountPlacement(details, titleRow, title, account, titleSpacer, actionButton);
            titleRow.widthProperty().addListener((_, _, _) -> update.run());
            update.run();
        }

        private void updateAccountPlacement(VBox details,
                                            HBox titleRow,
                                            Label title,
                                            Label account,
                                            Region titleSpacer,
                                            Button actionButton) {
            boolean accountVisible = account.isManaged();
            boolean inlineAccount = accountVisible && accountFitsInline(titleRow, title, account, actionButton);

            details.getChildren().remove(account);
            account.setWrapText(!inlineAccount);
            account.setMaxWidth(inlineAccount ? Region.USE_PREF_SIZE : Double.MAX_VALUE);

            if (inlineAccount) {
                if (actionButton == null) {
                    titleRow.getChildren().setAll(title, account);
                } else {
                    titleRow.getChildren().setAll(title, account, titleSpacer, actionButton);
                }
                return;
            }

            if (actionButton == null) {
                titleRow.getChildren().setAll(title);
            } else {
                titleRow.getChildren().setAll(title, titleSpacer, actionButton);
            }
            if (accountVisible && !details.getChildren().contains(account)) {
                details.getChildren().add(Math.min(1, details.getChildren().size()), account);
            }
        }

        private boolean accountFitsInline(HBox titleRow, Label title, Label account, Button actionButton) {
            double availableWidth = titleRow.getWidth();
            if (availableWidth <= 0) {
                return false;
            }
            int childCount = actionButton == null ? 2 : 4;
            double actionWidth = actionButton == null ? 0 : actionButton.prefWidth(-1);
            double requiredWidth = preferredTextWidth(title)
                    + preferredTextWidth(account)
                    + actionWidth
                    + ((childCount - 1) * TITLE_ROW_GAP);
            return requiredWidth <= availableWidth;
        }

        private double preferredTextWidth(Labeled labeled) {
            double preferredWidth = labeled.prefWidth(-1);
            if (preferredWidth > 0) {
                return preferredWidth;
            }
            String text = labeled.getText();
            return text == null ? 0 : (text.length() * 7.0) + 16.0;
        }

        private Label createAccount() {
            Label account = new Label(accountText);
            account.getStyleClass().add("watching-now-card-account");
            account.setWrapText(true);
            account.setTextOverrun(OverrunStyle.ELLIPSIS);
            account.setMaxWidth(Double.MAX_VALUE);
            account.setMinWidth(0);
            account.setMinHeight(Region.USE_PREF_SIZE);
            account.setVisible(!accountText.isBlank());
            account.setManaged(!accountText.isBlank());
            return account;
        }

        private Label createTitle() {
            Label title = new Label(titleText);
            title.getStyleClass().add("strong-label");
            title.getStyleClass().add("watching-now-card-title");
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setMinWidth(0);
            title.setMinHeight(Region.USE_PREF_SIZE);
            title.setMouseTransparent(true);
            HBox.setHgrow(title, Priority.ALWAYS);
            return title;
        }

        private FlowPane createMetadataRow() {
            FlowPane metadataRow = new FlowPane(8, 6);
            metadataRow.getStyleClass().add("watching-now-card-meta-row");
            metadataRow.getChildren().addAll(metadataNodes);
            return metadataRow;
        }

        private void prepareFlexibleNode(Node node) {
            if (node instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
                region.setMinWidth(0);
                region.setMinHeight(Region.USE_PREF_SIZE);
            }
        }
    }
}
