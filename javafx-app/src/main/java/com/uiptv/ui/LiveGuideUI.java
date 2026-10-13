package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One scroll surface keeps channel rows and programme columns on the same time axis. */
public class LiveGuideUI extends VBox implements SearchTarget {
    private static final double CHANNEL_WIDTH = 200;
    private static final double HOUR_WIDTH = 240;
    private double hourWidth = HOUR_WIDTH;
    private final Label selectedProgramme = new Label();
    private final VBox rows = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(rows);
    private final Label status = new Label();
    private final AtomicInteger generation = new AtomicInteger();
    private List<LiveGuideApplicationService.Row> data = List.of();
    private java.util.function.Predicate<LiveGuideApplicationService.Row> rowFilter = _ -> true;
    private String accountId = "", query = "";
    private long windowStart;
    private boolean loading;
    private long loadedAt;
    private int hourOffset;
    private final CheckBox showAllChannels = new CheckBox(I18n.tr("epgShowAllChannels"));
    private final javafx.animation.Timeline clock = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.minutes(1), _ -> { if (isVisible()) refreshIfNeeded(); }));

    public LiveGuideUI() {
        getStyleClass().add("live-guide");
        setSpacing(8);
        scroll.setFitToHeight(false);
        scroll.setPannable(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Button previous = new Button("‹"), now = new Button(I18n.tr("epgNow")), next = new Button("›");
        previous.setTooltip(new Tooltip(I18n.tr("autoBack"))); next.setTooltip(new Tooltip(I18n.tr("epgNext")));
        previous.setOnAction(_ -> { hourOffset -= 6; render(); });
        next.setOnAction(_ -> { hourOffset += 6; render(); });
        now.setOnAction(_ -> { hourOffset = 0; invalidate(); refreshIfNeeded(); });
        showAllChannels.setOnAction(_ -> render());
        Button zoomIn = new Button("+"), zoomOut = new Button("−");
        zoomIn.setTooltip(new Tooltip(I18n.tr("epgZoomIn"))); zoomOut.setTooltip(new Tooltip(I18n.tr("epgZoomOut")));
        zoomIn.setOnAction(_ -> { hourWidth = Math.min(720, hourWidth + 120); render(); });
        zoomOut.setOnAction(_ -> { hourWidth = Math.max(240, hourWidth - 120); render(); });
        FlowPane toolbar = new FlowPane(8, 8, previous, now, next, status, showAllChannels, zoomOut, zoomIn);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        selectedProgramme.setWrapText(true); selectedProgramme.setMinHeight(Region.USE_PREF_SIZE); selectedProgramme.setMaxWidth(Double.MAX_VALUE);
        selectedProgramme.getStyleClass().add("live-guide-selected-programme");
        selectedProgramme.setVisible(false); selectedProgramme.setManaged(false);
        getChildren().addAll(toolbar, selectedProgramme, scroll);
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((_, _, scene) -> { if (scene == null) clock.stop(); else clock.play(); });
    }

    public void setRowFilter(java.util.function.Predicate<LiveGuideApplicationService.Row> filter) {
        rowFilter = filter == null ? _ -> true : filter; render();
    }

    public void setContext(String id) {
        String next = id == null ? "" : id;
        if (Objects.equals(accountId, next)) return;
        accountId = next;
        hourOffset = 0;
        generation.incrementAndGet();
        loading = false; loadedAt = 0; data = List.of();
        rows.getChildren().clear(); selectedProgramme.setVisible(false); selectedProgramme.setManaged(false);
    }

    public void refreshIfNeeded() {
        if (loading || System.currentTimeMillis() - loadedAt < 60_000) { render(); return; }
        loading = true;
        status.setText(I18n.tr("epgLoading"));
        int request = generation.incrementAndGet();
        String requestedId = accountId;
        Thread.ofVirtual().start(() -> {
            try {
                List<LiveGuideApplicationService.Row> loaded = new LiveGuideApplicationService().list(requestedId);
                Platform.runLater(() -> {
                    if (request != generation.get()) return;
                    data = loaded; loading = false; loadedAt = System.currentTimeMillis(); render();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (request != generation.get()) return;
                    loading = false; status.setText(I18n.tr("epgUnavailable"));
                });
            }
        });
    }

    public void invalidate() { loadedAt = 0; }
    public void dispose() { generation.incrementAndGet(); loading = false; clock.stop(); }
    @Override public void setSearchQuery(String text) { query = text == null ? "" : text.toLowerCase(Locale.ROOT); render(); }

    private void render() {
        if (loading && data.isEmpty()) return;
        windowStart = Instant.now().atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).minusHours(1).plusHours(hourOffset).toInstant().toEpochMilli();
        rows.getChildren().clear();
        HBox header = new HBox();
        Label channelHeading = new Label(DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(windowStart).atZone(ZoneId.systemDefault())));
        channelHeading.setMinWidth(CHANNEL_WIDTH); header.getChildren().add(channelHeading);
        for (int hour = 0; hour < 6; hour++) {
            Label tick = new Label(formatTime(windowStart + hour * 3_600_000L));
            tick.setMinWidth(hourWidth); header.getChildren().add(tick);
        }
        rows.getChildren().add(header);
        int count = 0;
        for (LiveGuideApplicationService.Row row : data) {
            if (!rowFilter.test(row)) continue;
            Channel channel = row.channel();
            if (!showAllChannels.isSelected() && row.programmes().stream().noneMatch(p -> p.stop() > System.currentTimeMillis())) continue;
            if (channel.getName() == null || !channel.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            count++;
            HBox line = new HBox();
            Button name = new Button(channel.getName());
            name.getStyleClass().add("live-guide-channel");
            if (ThumbnailAwareUI.areThumbnailsEnabled() && channel.getLogo() != null && !channel.getLogo().isBlank()) {
                com.uiptv.widget.AsyncImageView thumbnail = new com.uiptv.widget.AsyncImageView();
                thumbnail.loadImage(channel.getLogo(), "channel"); name.setGraphic(thumbnail);
            }
            name.setMinWidth(CHANNEL_WIDTH); name.setPrefWidth(CHANNEL_WIDTH); name.setMaxWidth(CHANNEL_WIDTH);
            name.setWrapText(true); name.setMinHeight(90); name.setOnAction(_ -> play(row));
            name.setTooltip(new Tooltip(row.accountName()));
            Pane timeline = new Pane();
            timeline.setMinSize(hourWidth * 6, 90); timeline.setPrefSize(hourWidth * 6, 90);
            boolean hasProgramme = false;
            for (EpgProgramme p : row.programmes()) {
                long left = Math.max(p.start(), windowStart), right = Math.min(p.stop(), windowStart + 6 * 3_600_000L);
                if (right <= left) continue;
                hasProgramme = true;
                Button block = new Button(p.title());
                block.getStyleClass().add("live-guide-programme");
                if (p.start() <= System.currentTimeMillis() && p.stop() > System.currentTimeMillis()) block.getStyleClass().add("current-programme");
                block.setLayoutX((left - windowStart) / 3_600_000.0 * hourWidth);
                block.setLayoutY(3);
                block.setPrefSize(Math.max(1, (right - left) / 3_600_000.0 * hourWidth - 3), 84);
                block.setMinWidth(0); block.setWrapText(true); block.setTextOverrun(OverrunStyle.CLIP);
                block.setTooltip(new Tooltip(p.title() + "\n" + formatTime(p.start()) + " – " + formatTime(p.stop()) + "\n" + p.description()));
                block.setOnAction(_ -> {
                    selectedProgramme.setText(channel.getName() + " · " + p.title() + " · " + formatTime(p.start()) + " – " + formatTime(p.stop()));
                    selectedProgramme.setVisible(true); selectedProgramme.setManaged(true); play(row);
                });
                timeline.getChildren().add(block);
            }
            if (!hasProgramme) { Label empty = new Label(I18n.tr("epgNoGuide")); empty.setLayoutY(18); timeline.getChildren().add(empty); }
            javafx.scene.shape.Line marker = new javafx.scene.shape.Line(hourWidth * (System.currentTimeMillis() - windowStart) / 3_600_000.0, 0,
                    hourWidth * (System.currentTimeMillis() - windowStart) / 3_600_000.0, 90);
            marker.getStyleClass().add("live-guide-now-marker"); marker.setMouseTransparent(true);
            if (marker.getStartX() >= 0 && marker.getStartX() <= hourWidth * 6) timeline.getChildren().add(marker);
            line.getChildren().addAll(name, timeline); rows.getChildren().add(line);
        }
        status.setText(count == 0 ? I18n.tr("epgNoGuide") : (accountId.isBlank() ? I18n.tr("autoFavorite") : data.getFirst().accountName()));
    }

    private static String formatTime(long time) { return DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())); }

    private void play(LiveGuideApplicationService.Row row) {
        Thread.ofVirtual().start(() -> {
            Account account = AccountService.getInstance().getById(row.accountId());
            if (account == null) return;
            AccountMediaContext context = AccountMediaContext.from(account, Account.AccountAction.itv);
            String player = ConfigurationService.getInstance().read().getDefaultPlayerPath();
            Platform.runLater(() -> {
                if (account.isParentalLock() && row.channel().getCensored() == 1
                        && !FilterLockDialogs.ensureUnlocked(this, "filterLockUnlockCensoredChannelReason")) return;
                PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(context, row.channel(), player)
                        .categoryId(row.channel().getCategoryId()).channelId(row.channel().getChannelId())
                        .errorPrefix(I18n.tr("autoErrorPlayingChannelPrefix")));
            });
        });
    }
}
