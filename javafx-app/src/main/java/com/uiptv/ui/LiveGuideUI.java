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
    private static final double ROW_HEIGHT = 94, HEADER_HEIGHT = 32;
    private List<LiveGuideApplicationService.Row> filteredRows = List.of();
    private int renderedStart = -1, renderedEnd = -1;
    private boolean viewportRefreshPending;
    private double hourWidth = HOUR_WIDTH;
    private final Label selectedProgramme = new Label();
    private final Map<LiveGuideApplicationService.Row, com.uiptv.widget.PlayingCardIndicator> playingIndicators = new LinkedHashMap<>();
    private final javafx.beans.value.ChangeListener<com.uiptv.player.MediaPlayerFactory.GuideContext> playbackListener = (_, _, _) -> updatePlayingIndicators();
    private final VBox rows = new VBox();
    private final ScrollPane scroll = new ScrollPane(rows);
    private final Label status = new Label();
    private final AtomicInteger generation = new AtomicInteger();
    private List<LiveGuideApplicationService.Row> data = List.of();
    private java.util.function.Predicate<LiveGuideApplicationService.Row> rowFilter = _ -> true;
    private String accountId = "", query = "";
    private long windowStart;
    private boolean loading;
    private Thread nativeWorker;
    private Thread catalogWorker;
    private String nativeWindow = "";
    private String sourceWarning = "";
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
        scroll.vvalueProperty().addListener((_, _, _) -> scheduleViewportRefresh());
        scroll.viewportBoundsProperty().addListener((_, _, _) -> scheduleViewportRefresh());
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
        Button refresh = new Button(I18n.tr("epgRefreshGuide"));
        refresh.setOnAction(_ -> {
            generation.incrementAndGet(); if (nativeWorker != null) nativeWorker.interrupt();
            loading = true; status.setText(I18n.tr("epgLoading"));
            Thread.ofVirtual().start(() -> {
                new EpgApplicationService().clearCache();
                Platform.runLater(() -> { loading = false; nativeWindow = ""; sourceWarning = ""; invalidate(); refreshIfNeeded(); });
            });
        });
        FlowPane toolbar = new FlowPane(8, 8, previous, now, next, refresh, status, showAllChannels, zoomOut, zoomIn);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        selectedProgramme.setWrapText(true); selectedProgramme.setMinHeight(Region.USE_PREF_SIZE); selectedProgramme.setMaxWidth(Double.MAX_VALUE);
        selectedProgramme.getStyleClass().add("live-guide-selected-programme");
        selectedProgramme.setVisible(false); selectedProgramme.setManaged(false);
        getChildren().addAll(toolbar, selectedProgramme, scroll);
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((_, oldScene, scene) -> {
            if (oldScene != null) com.uiptv.player.MediaPlayerFactory.guideContextProperty().removeListener(playbackListener);
            if (scene == null) { generation.incrementAndGet(); loading = false; if (catalogWorker != null) catalogWorker.interrupt(); if (nativeWorker != null) nativeWorker.interrupt(); clock.stop(); hidePlayingIndicators(); }
            else { com.uiptv.player.MediaPlayerFactory.guideContextProperty().addListener(playbackListener); updatePlayingIndicators(); clock.play(); }
        });
    }

    public void setRowFilter(java.util.function.Predicate<LiveGuideApplicationService.Row> filter) {
        rowFilter = filter == null ? _ -> true : filter; render();
    }

    public void setContext(String id) {
        String next = id == null ? "" : id;
        if (Objects.equals(accountId, next)) return;
        accountId = next;
        hourOffset = 0;
        if (catalogWorker != null) catalogWorker.interrupt();
        generation.incrementAndGet();
        if (nativeWorker != null) nativeWorker.interrupt();
        nativeWindow = "";
        loading = false; loadedAt = 0; data = List.of();
        hidePlayingIndicators(); playingIndicators.clear();
        filteredRows = List.of(); renderedStart = renderedEnd = -1;
        rows.getChildren().clear(); selectedProgramme.setVisible(false); selectedProgramme.setManaged(false);
    }

    public void refreshIfNeeded() {
        if (loading || System.currentTimeMillis() - loadedAt < 30 * 60_000L) { render(); return; }
        loading = true;
        status.setText(I18n.tr("epgLoading"));
        int request = generation.incrementAndGet();
        String requestedId = accountId;
        if (catalogWorker != null) catalogWorker.interrupt();
        catalogWorker = Thread.ofVirtual().start(() -> {
            try {
                List<LiveGuideApplicationService.Row> loaded = new LiveGuideApplicationService().list(requestedId, channels -> Platform.runLater(() -> {
                    if (request != generation.get()) return;
                    data = channels; render(); status.setText(I18n.tr("epgLoading"));
                }), false);
                String warning = new EpgApplicationService().globalWarning();
                Platform.runLater(() -> {
                    if (request != generation.get()) return;
                    data = loaded; sourceWarning = warning.isBlank() ? "" : I18n.tr(warning); loading = false; loadedAt = System.currentTimeMillis(); render();
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
    public void dispose() {
        generation.incrementAndGet(); if (nativeWorker != null) nativeWorker.interrupt();
        if (catalogWorker != null) catalogWorker.interrupt();
        loading = false; clock.stop(); hidePlayingIndicators();
        data = List.of(); filteredRows = List.of(); playingIndicators.clear(); rows.getChildren().clear();
        com.uiptv.player.MediaPlayerFactory.guideContextProperty().removeListener(playbackListener);
    }
    @Override public void setSearchQuery(String text) { query = text == null ? "" : text.toLowerCase(Locale.ROOT); render(); }

    private void render() {
        if (loading && data.isEmpty()) return;
        windowStart = Instant.now().atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).minusHours(1).plusHours(hourOffset).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        filteredRows = data.stream().filter(rowFilter)
                .filter(row -> showAllChannels.isSelected() || row.nativePending() || row.programmes().stream().anyMatch(p -> p.stop() > now))
                .filter(row -> row.channel().getName() != null && row.channel().getName().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        renderViewport(true);
        status.setText((filteredRows.isEmpty() ? I18n.tr("epgNoGuide") : (accountId.isBlank() ? I18n.tr("autoFavorite") : filteredRows.getFirst().accountName())) + (sourceWarning.isBlank() ? "" : " · " + sourceWarning));
    }

    private void scheduleViewportRefresh() {
        if (viewportRefreshPending) return;
        viewportRefreshPending = true;
        Platform.runLater(() -> { viewportRefreshPending = false; if (getScene() != null) renderViewport(false); });
    }

    private void renderViewport(boolean force) {
        double viewportHeight = scroll.getViewportBounds().getHeight();
        if (viewportHeight <= 0) viewportHeight = 540;
        double totalHeight = HEADER_HEIGHT + filteredRows.size() * ROW_HEIGHT;
        double top = Math.max(0, (totalHeight - viewportHeight) * scroll.getVvalue() - HEADER_HEIGHT);
        int capacity = (int) Math.ceil(viewportHeight / ROW_HEIGHT) + 8;
        int start = Math.min(Math.max(0, filteredRows.size() - capacity), Math.max(0, (int) (top / ROW_HEIGHT) - 4));
        int end = Math.min(filteredRows.size(), start + capacity);
        if (!force && start == renderedStart && end == renderedEnd) return;
        renderedStart = start; renderedEnd = end;
        hidePlayingIndicators(); playingIndicators.clear(); rows.getChildren().clear();
        HBox header = new HBox();
        header.setMinHeight(HEADER_HEIGHT); header.setPrefHeight(HEADER_HEIGHT); header.setMaxHeight(HEADER_HEIGHT);
        Label channelHeading = new Label(DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(windowStart).atZone(ZoneId.systemDefault())));
        channelHeading.setMinWidth(CHANNEL_WIDTH); header.getChildren().add(channelHeading);
        for (int hour = 0; hour < 6; hour++) {
            Label tick = new Label(formatTime(windowStart + hour * 3_600_000L));
            tick.setMinWidth(hourWidth); header.getChildren().add(tick);
        }
        rows.getChildren().add(header);
        addSpacer(start * ROW_HEIGHT);
        for (int index = start; index < end; index++) {
            LiveGuideApplicationService.Row row = filteredRows.get(index);
            Channel channel = row.channel();
            HBox line = new HBox();
            line.setMinHeight(ROW_HEIGHT); line.setPrefHeight(ROW_HEIGHT); line.setMaxHeight(ROW_HEIGHT);
            Button name = new Button(channel.getName());
            name.getStyleClass().add("live-guide-channel");
            HBox graphic = new HBox(6); graphic.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            if (ThumbnailAwareUI.areThumbnailsEnabled() && channel.getLogo() != null && !channel.getLogo().isBlank()) {
                com.uiptv.widget.AsyncImageView thumbnail = new com.uiptv.widget.AsyncImageView();
                thumbnail.loadImage(channel.getLogo(), "channel"); graphic.getChildren().add(thumbnail);
            }
            var indicator = new com.uiptv.widget.PlayingCardIndicator();
            graphic.getChildren().add(indicator); playingIndicators.put(row, indicator); name.setGraphic(graphic);
            installPlaybackActivation(name, row);
            MenuItem mapping = new MenuItem(I18n.tr("epgGuideIdOverride"));
            mapping.setOnAction(_ -> editGuideMapping(row));
            name.setContextMenu(new ContextMenu(mapping));
            name.setMinWidth(CHANNEL_WIDTH); name.setPrefWidth(CHANNEL_WIDTH); name.setMaxWidth(CHANNEL_WIDTH);
            name.setWrapText(true); name.setMinHeight(90);
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
                double blockWidth = Math.max(1, (right - left) / 3_600_000.0 * hourWidth - 3);
                block.setPrefSize(blockWidth, 84);
                block.setMinWidth(0); block.setMaxWidth(blockWidth);
                block.setWrapText(false); block.setTextOverrun(OverrunStyle.ELLIPSIS);
                // JavaFX's automatic wrapping splits long words into letters. Insert only
                // word-boundary line breaks, then let the control elide oversized words.
                Runnable updateTitle = () -> block.setText(wrapProgrammeTitle(p.title(),
                        Math.max(0, blockWidth - block.getInsets().getLeft() - block.getInsets().getRight()), block.getFont(), 3));
                block.fontProperty().addListener((_, _, _) -> updateTitle.run());
                block.insetsProperty().addListener((_, _, _) -> updateTitle.run());
                updateTitle.run();
                block.setAccessibleText(p.title());
                block.setTooltip(new Tooltip(p.title() + "\n" + formatTime(p.start()) + " – " + formatTime(p.stop()) + "\n" + p.description()));
                block.setOnAction(_ -> {
                    selectedProgramme.setText(channel.getName() + " · " + p.title() + " · " + formatTime(p.start()) + " – " + formatTime(p.stop()));
                    selectedProgramme.setVisible(true); selectedProgramme.setManaged(true);
                });
                installPlaybackActivation(block, row);
                timeline.getChildren().add(block);
            }
            if (!hasProgramme) {
                Label message = new Label(I18n.tr(row.nativePending() ? "epgLoading" : "epgNoGuide"));
                message.getStyleClass().add("live-guide-empty-message");
                StackPane empty = new StackPane(message);
                empty.getStyleClass().add("live-guide-empty-slot");
                empty.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                empty.setLayoutY(3); empty.setPrefSize(hourWidth * 6 - 3, 84);
                timeline.getChildren().add(empty);
            }
            javafx.scene.shape.Line marker = new javafx.scene.shape.Line(hourWidth * (System.currentTimeMillis() - windowStart) / 3_600_000.0, 0,
                    hourWidth * (System.currentTimeMillis() - windowStart) / 3_600_000.0, 90);
            marker.getStyleClass().add("live-guide-now-marker"); marker.setMouseTransparent(true);
            if (marker.getStartX() >= 0 && marker.getStartX() <= hourWidth * 6) timeline.getChildren().add(marker);
            line.getChildren().addAll(name, timeline); rows.getChildren().add(line);
        }
        addSpacer((filteredRows.size() - end) * ROW_HEIGHT);
        updatePlayingIndicators();
        hydrateViewport(start, end);
    }

    private void editGuideMapping(LiveGuideApplicationService.Row row) {
        Thread.ofVirtual().start(() -> {
            var mappings = new com.uiptv.application.EpgMappingApplicationService();
            String previous = mappings.read(row.accountId(), row.channel().getChannelId());
            Platform.runLater(() -> {
                TextInputDialog dialog = new TextInputDialog(previous);
                dialog.setTitle(I18n.tr("epgGuideIdOverride"));
                dialog.setHeaderText(row.channel().getName());
                dialog.setContentText(I18n.tr("epgGuideIdHelp"));
                dialog.showAndWait().ifPresent(value -> Thread.ofVirtual().start(() -> {
                    mappings.save(row.accountId(), row.channel().getChannelId(), value);
                    Platform.runLater(() -> { invalidate(); refreshIfNeeded(); });
                }));
            });
        });
    }

    private void hydrateViewport(int start, int end) {
        if (loading || getScene() == null || !EpgModeUI.isFeatureEnabled()) return;
        List<LiveGuideApplicationService.Row> visible = filteredRows.subList(start, end).stream().filter(LiveGuideApplicationService.Row::nativePending).toList();
        if (visible.isEmpty()) return;
        String window = generation.get() + ":" + start + ":" + end;
        if (window.equals(nativeWindow)) return;
        nativeWindow = window;
        if (nativeWorker != null) nativeWorker.interrupt();
        int request = generation.get();
        nativeWorker = Thread.ofVirtual().start(() -> {
            LiveGuideApplicationService service = new LiveGuideApplicationService();
            Map<LiveGuideApplicationService.Row, LiveGuideApplicationService.Row> updates = new IdentityHashMap<>();
            long publishedAt = System.nanoTime();
            try {
                for (LiveGuideApplicationService.Row row : visible) {
                    if (Thread.currentThread().isInterrupted()) return;
                    updates.put(row, service.hydrate(row));
                    if (System.nanoTime() - publishedAt >= 100_000_000L) {
                        publishHydratedRows(request, updates);
                        updates.clear(); publishedAt = System.nanoTime();
                    }
                }
                if (!updates.isEmpty()) publishHydratedRows(request, updates);
            } finally {
                Thread owner = Thread.currentThread();
                Platform.runLater(() -> {
                    if (nativeWorker != owner) return;
                    nativeWorker = null; nativeWindow = "";
                    if (request == generation.get() && getScene() != null && renderedStart >= 0)
                        hydrateViewport(renderedStart, renderedEnd);
                });
            }
        });
    }

    private void publishHydratedRows(int request, Map<LiveGuideApplicationService.Row, LiveGuideApplicationService.Row> updates) {
        Map<LiveGuideApplicationService.Row, LiveGuideApplicationService.Row> batch = new IdentityHashMap<>(updates);
        String warning = new EpgApplicationService().globalWarning();
        Platform.runLater(() -> {
            if (request != generation.get() || getScene() == null) return;
            sourceWarning = warning.isBlank() ? "" : I18n.tr(warning);
            data = data.stream().map(item -> batch.getOrDefault(item, item)).toList();
            render();
        });
    }

    private void addSpacer(double height) {
        if (height <= 0) return;
        Region spacer = new Region();
        spacer.setMinHeight(height); spacer.setPrefHeight(height); spacer.setMaxHeight(height);
        spacer.setMinWidth(CHANNEL_WIDTH + hourWidth * 6);
        rows.getChildren().add(spacer);
    }

    static String wrapProgrammeTitle(String title, double width, javafx.scene.text.Font font, int maxLines) {
        if (title == null || title.isBlank()) return "";
        List<String> lines = new ArrayList<>();
        String line = "";
        javafx.scene.text.Text measure = new javafx.scene.text.Text();
        measure.setFont(font);
        for (String word : title.strip().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            measure.setText(candidate);
            if (!line.isEmpty() && measure.getLayoutBounds().getWidth() > width) {
                lines.add(line);
                if (lines.size() == maxLines) {
                    lines.set(maxLines - 1, lines.getLast() + "…");
                    return String.join("\n", lines);
                }
                line = word;
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) lines.add(line);
        return String.join("\n", lines);
    }

    private void installPlaybackActivation(Button button, LiveGuideApplicationService.Row row) {
        button.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 2) {
                play(row); event.consume();
            }
        });
        button.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                button.fire(); play(row); event.consume();
            }
        });
    }

    private void hidePlayingIndicators() {
        playingIndicators.values().forEach(indicator -> { indicator.setVisible(false); indicator.setManaged(false); });
    }

    private void updatePlayingIndicators() {
        var context = com.uiptv.player.MediaPlayerFactory.guideContextProperty().get();
        for (var entry : playingIndicators.entrySet()) {
            var row = entry.getKey();
            boolean active = context != null && context.account() != null && context.account().action() == Account.AccountAction.itv
                    && Objects.equals(context.account().dbId(), row.accountId())
                    && Objects.equals(context.channel().getChannelId(), row.channel().getChannelId());
            entry.getValue().setManaged(active); entry.getValue().setVisible(active);
        }
    }

    private static String formatTime(long time) { return DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())); }

    protected void play(LiveGuideApplicationService.Row row) {
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
