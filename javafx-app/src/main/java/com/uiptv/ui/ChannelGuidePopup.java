package com.uiptv.ui;

import com.uiptv.application.EpgProgramme;
import com.uiptv.application.LiveGuideApplicationService;
import com.uiptv.util.I18n;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** A separate native popup window remains usable alongside the heavyweight VLC surface. */
final class ChannelGuidePopup {
    private static final String GUIDE_ACTION_STYLE_CLASS = "epg-guide-action";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm");
    private ChannelGuidePopup() { }

    static Popup show(Node anchor, LiveGuideApplicationService.Row row) {
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) return null;
        Rectangle2D screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight())
                .getFirst().getVisualBounds();
        double width = Math.clamp(Math.min(screen.getWidth() - 32, anchor.getScene().getWindow().getWidth() - 32), 240, 960);
        Popup popup = new Popup();
        popup.setAutoHide(true); popup.setHideOnEscape(true); popup.setAutoFix(true);
        VBox body = content(row, width, popup::hide);
        body.getStylesheets().addAll(anchor.getScene().getStylesheets());
        for (Parent parent = anchor.getParent(); parent != null; parent = parent.getParent())
            for (String sheet : parent.getStylesheets()) if (!body.getStylesheets().contains(sheet)) body.getStylesheets().add(sheet);
        popup.getContent().add(body);
        popup.setOnHidden(_ -> { release(body); popup.getContent().clear(); });
        double x = Math.clamp(bounds.getMinX(), screen.getMinX() + 16, screen.getMaxX() - width - 16);
        double y = bounds.getMaxY() + 8;
        if (y + 370 > screen.getMaxY()) y = Math.max(screen.getMinY() + 16, bounds.getMinY() - 370);
        popup.show(anchor, x, y);
        return popup;
    }

    static VBox content(LiveGuideApplicationService.Row row, double width, Runnable onClose) {
        Label heading = new Label(row.channel().getName());
        heading.getStyleClass().add("epg-guide-popup-heading");
        heading.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(heading, Priority.ALWAYS);
        Button close = new com.uiptv.widget.CloseIconButton(I18n.tr("autoClose")); close.getStyleClass().add(GUIDE_ACTION_STYLE_CLASS); close.setOnAction(_ -> onClose.run());
        HBox header = new HBox(12, heading, close); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        ListView<EpgProgramme> programmes = new ListView<>(FXCollections.observableArrayList(row.programmes()));
        programmes.setOrientation(Orientation.HORIZONTAL);
        double contentWidth = width - 32;
        double cellWidth = (contentWidth - 4) / Math.clamp(Math.min(row.programmes().size(), (int) (contentWidth / 280)), 1, Integer.MAX_VALUE);
        programmes.setPrefHeight(248); programmes.setMinHeight(248);
        long clock = System.currentTimeMillis();
        EpgProgramme following = row.programmes().stream().filter(p -> p.start() > clock).findFirst().orElse(null);
        programmes.setPlaceholder(new Label(I18n.tr("epgNoGuide")));
        programmes.setCellFactory(_ -> new ListCell<>() {
            {
                setMinWidth(cellWidth); setPrefWidth(cellWidth); setMaxWidth(cellWidth);
                setMinHeight(0); setPrefHeight(220); setMaxHeight(220);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(javafx.geometry.Pos.TOP_LEFT);
            }
            @Override protected void updateItem(EpgProgramme p, boolean empty) {
                super.updateItem(p, empty); setText(null);
                if (empty || p == null) { setGraphic(null); return; }
                boolean current = p.start() <= clock && p.stop() > clock;
                Label badge = new Label(programmeBadge(p, following, current));
                badge.getStyleClass().add("epg-programme-badge");
                if (current) badge.getStyleClass().add("epg-now-badge");
                Label title = new Label(p.title()); title.setWrapText(true); title.getStyleClass().add("epg-programme-title");
                title.setMinHeight(42); title.setMaxHeight(42); title.setTextOverrun(OverrunStyle.ELLIPSIS);
                Label time = new Label(TIME.format(Instant.ofEpochMilli(p.start()).atZone(ZoneId.systemDefault())) + " – "
                        + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(p.stop()).atZone(ZoneId.systemDefault())));
                time.getStyleClass().add("epg-programme-time"); time.setWrapText(true);
                Label description = new Label(p.description()); description.setWrapText(true); description.setMinHeight(0); description.setMaxHeight(110);
                description.setTextOverrun(OverrunStyle.ELLIPSIS); description.getStyleClass().add("epg-programme-description");
                VBox card = new VBox(8, badge, title, time);
                if (current) {
                    ProgressBar progress = new ProgressBar(Math.clamp((clock - p.start()) / (double) (p.stop() - p.start()), 0, 1));
                    progress.setMaxWidth(Double.MAX_VALUE); progress.getStyleClass().add("epg-programme-progress"); card.getChildren().add(progress);
                }
                card.getChildren().add(description);
                card.getStyleClass().addAll("epg-programme-card", "epg-guide-popup-programme");
                card.setPrefWidth(cellWidth - 12); card.setMaxWidth(cellWidth - 12); card.setMinHeight(220); card.setPrefHeight(220);
                if (current) card.getStyleClass().addAll("current-programme", "epg-current-card");
                Tooltip details = new Tooltip(p.title() + "\n" + p.description()); details.setWrapText(true); details.setMaxWidth(460);
                Tooltip.install(card, details); setGraphic(card);
            }
        });
        Button previous = navigationButton("M10 3 L4 9 L10 15");
        Button next = navigationButton("M4 3 L10 9 L4 15");
        previous.setAccessibleText(I18n.tr("autoBack")); next.setAccessibleText(I18n.tr("epgNext"));
        var position = new java.util.concurrent.atomic.AtomicInteger();
        previous.setOnAction(_ -> { int index = Math.max(0, position.decrementAndGet()); position.set(index); programmes.scrollTo(index); });
        next.setOnAction(_ -> { int index = Math.clamp(position.incrementAndGet(), 0, Math.max(0, row.programmes().size() - 1)); position.set(index); programmes.scrollTo(index); });
        Button nowButton = new Button(I18n.tr("epgNow"));
        nowButton.getStyleClass().addAll(GUIDE_ACTION_STYLE_CLASS, "epg-guide-now-button");
        nowButton.setOnAction(_ -> {
            int index = nowIndex(row.programmes(), System.currentTimeMillis());
            if (index >= 0) { position.set(index); programmes.scrollTo(index); }
        });
        HBox navigation = new HBox(8, previous, nowButton, next); navigation.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        VBox body = new VBox(12, header, programmes, navigation); body.getStyleClass().addAll("root", "epg-guide-popup");
        body.setPrefWidth(width); body.setMaxWidth(width);
        int index = nowIndex(row.programmes(), System.currentTimeMillis());
        if (index >= 0) { position.set(index); programmes.scrollTo(index); }
        return body;
    }

    private static String programmeBadge(EpgProgramme programme, EpgProgramme following, boolean current) {
        if (current) return I18n.tr("epgNow");
        if (programme.equals(following)) return I18n.tr("epgNext");
        return DateTimeFormatter.ofPattern("EEE d MMM").format(
                Instant.ofEpochMilli(programme.start()).atZone(ZoneId.systemDefault()));
    }

    static int nowIndex(java.util.List<EpgProgramme> programmes, long now) {
        for (int i = 0; i < programmes.size(); i++) {
            EpgProgramme programme = programmes.get(i);
            if (programme.start() <= now && now < programme.stop()) return i;
        }
        for (int i = 0; i < programmes.size(); i++) if (programmes.get(i).start() > now) return i;
        return programmes.isEmpty() ? -1 : programmes.size() - 1;
    }

    private static Button navigationButton(String path) {
        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent(path); icon.getStyleClass().add("epg-guide-navigation-icon");
        icon.setMouseTransparent(true);
        Button button = new Button(); button.setGraphic(icon);
        button.getStyleClass().add(GUIDE_ACTION_STYLE_CLASS);
        return button;
    }

    static void release(Parent content) {
        for (Node node : content.lookupAll(".list-view")) if (node instanceof ListView<?> list) list.getItems().clear();
    }
}
