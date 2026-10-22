package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.util.I18n;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** One cancellable loader per virtual grid. Detached cards retain no schedule or timer. */
final class CardEpgSupport {
    /**
     * Hydrate a bounded first window in display order. The grid itself remains virtualized, so
     * this is a cap on EPG work rather than a request to render or retain the whole catalogue.
     */
    private static final int EPG_BATCH_SIZE = Math.max(1, Integer.getInteger("uiptv.cardEpg.batchSize", 25));
    private final Node owner;
    private final Map<Region, Binding> attached = new IdentityHashMap<>();
    private final Map<Region, Supplier<LiveGuideApplicationService.Row>> pending = new WeakHashMap<>();
    private final Map<Region, javafx.beans.value.ChangeListener<Bounds>> cardBoundsListeners = new IdentityHashMap<>();
    private final PauseTransition pulse = new PauseTransition(Duration.millis(40));
    private final PauseTransition layoutRetry = new PauseTransition(Duration.millis(120));
    private final Timeline clock = new Timeline(new KeyFrame(Duration.minutes(1), _ -> { restoreSummaries(); queue(); }));
    private final Map<ScrollPane, javafx.beans.value.ChangeListener<Number>> scrollListeners = new IdentityHashMap<>();
    private final Map<ScrollPane, javafx.beans.value.ChangeListener<Bounds>> viewportBoundsListeners = new IdentityHashMap<>();
    private Thread worker;
    private Thread popupWorker;
    private long popupRevision;
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private int layoutRetryAttempts;
    private javafx.stage.Popup popup;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final class Binding {
        final Pane panel;
        final Supplier<LiveGuideApplicationService.Row> source;
        long refreshAt;
        EpgProgramme current;
        EpgProgramme next;
        boolean sourceWarning;
        Binding(Pane panel, Supplier<LiveGuideApplicationService.Row> source) { this.panel = panel; this.source = source; }
    }

    CardEpgSupport(Node owner) {
        this.owner = owner;
        pulse.setOnFinished(_ -> refresh());
        layoutRetry.setOnFinished(_ -> {
            if (layoutRetryAttempts <= 0 || owner.getScene() == null
                    || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()) return;
            layoutRetryAttempts--;
            queue();
        });
        clock.setCycleCount(Animation.INDEFINITE);
        owner.sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) stop();
            else if (EpgModeUI.isFeatureEnabled() && !EpgModeUI.isPlaybackOnly()) {
                clock.play(); retryAfterLayout(); queue();
            }
        });
        owner.visibleProperty().addListener((_, _, visible) -> {
            if (!visible.booleanValue()) stop();
            else if (EpgModeUI.isFeatureEnabled() && !EpgModeUI.isPlaybackOnly()) {
                attached.keySet().forEach(card -> { observeScroll(card); observeCardBounds(card); });
                restoreSummaries(); clock.play(); retryAfterLayout(); queue();
            }
        });
        // Do not observe the owner's complete layout bounds here. Rendering an
        // EPG panel changes the grid's layout bounds, which would queue another
        // EPG refresh and rebuild the panel again. That feedback loop keeps
        // the JavaFX pulse/render thread busy even when the window is idle.
        // Scene, visibility, scroll, cache, and clock changes already cover
        // the meaningful refresh boundaries.
        EpgModeUI.observeCache(owner, () -> {
            if (!EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()) stop();
            else { clock.play(); retryAfterLayout(); queue(); }
                attached.values().forEach(binding -> binding.refreshAt = 0);
        });
    }

    Region bind(Region card, Supplier<LiveGuideApplicationService.Row> source) {
        pending.put(card, source);
        if (EpgModeUI.isFeatureEnabled() && !EpgModeUI.isPlaybackOnly()) {
            attach(card, source);
            pending.remove(card);
        }
        return card;
    }

    void refreshMode() {
        if (!EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()) return;
        for (var entry : new ArrayList<>(pending.entrySet())) {
            attach(entry.getKey(), entry.getValue());
            pending.remove(entry.getKey());
        }
        retryAfterLayout();
    }

    private void attach(Region card, Supplier<LiveGuideApplicationService.Row> source) {
        boolean programmeRow = card.getStyleClass().contains("epg-programme-aware-row");
        Pane panel = programmeRow ? new HBox(12) : new VBox(5);
        panel.getStyleClass().add("virtual-card-epg");
        panel.setMinWidth(0); panel.setMaxWidth(Double.MAX_VALUE); panel.setVisible(false); panel.setManaged(false);
        card.getStyleClass().add("epg-summary-card");
        if (programmeRow && card instanceof GridPane grid) {
            panel.getStyleClass().add("epg-programme-columns");
            GridPane.setHgrow(panel, Priority.ALWAYS);
            grid.add(panel, 1, 0);
        } else if (card instanceof VBox vertical) {
            vertical.setAlignment(javafx.geometry.Pos.TOP_LEFT); vertical.getChildren().add(panel);
        } else if (card instanceof HBox horizontal) {
            var children = new ArrayList<>(horizontal.getChildren()); horizontal.getChildren().clear();
            HBox header = new HBox(horizontal.getSpacing()); header.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            header.getStyleClass().add("epg-card-header"); header.getChildren().setAll(children);
            for (Node child : children) if (child instanceof VBox text) text.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            VBox body = new VBox(8, header, panel); body.setMinWidth(0); body.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(body, Priority.ALWAYS); horizontal.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            horizontal.getChildren().setAll(body);
        }
        Binding binding = new Binding(panel, source);
        displayLoading(panel);
        card.sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) {
                attached.remove(card);
                removeCardBoundsListener(card);
                queue();
            } else {
                attached.put(card, binding);
                if (System.currentTimeMillis() < binding.refreshAt) displayPanel(panel, binding.current, binding.next);
                observeScroll(card);
                observeCardBounds(card);
                retryAfterLayout();
                queue();
            }
        });
        if (card.getScene() != null) {
            attached.put(card, binding);
            observeScroll(card);
            observeCardBounds(card);
            retryAfterLayout();
            queue();
        }
    }

    private void observeScroll(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll) {
                scrollListeners.computeIfAbsent(scroll, ignored -> {
                    javafx.beans.value.ChangeListener<Number> listener = (_, _, _) -> queue();
                    scroll.vvalueProperty().addListener(listener);
                    return listener;
                });
                viewportBoundsListeners.computeIfAbsent(scroll, ignored -> {
                    javafx.beans.value.ChangeListener<Bounds> listener = (_, _, _) -> queue();
                    scroll.viewportBoundsProperty().addListener(listener);
                    return listener;
                });
            }
        }
    }

    private void observeCardBounds(Region card) {
        if (cardBoundsListeners.containsKey(card)) {
            queueWhenVisible(card);
            return;
        }
        javafx.beans.value.ChangeListener<Bounds> listener = (_, _, _) -> queueWhenVisible(card);
        cardBoundsListeners.put(card, listener);
        card.boundsInLocalProperty().addListener(listener);
        queueWhenVisible(card);
    }

    private void queueWhenVisible(Region card) {
        if (visible(card)) {
            removeCardBoundsListener(card);
            queue();
        }
    }

    private void removeCardBoundsListener(Region card) {
        javafx.beans.value.ChangeListener<Bounds> listener = cardBoundsListeners.remove(card);
        if (listener != null) card.boundsInLocalProperty().removeListener(listener);
    }

    void queue() {
        if (owner.getScene() == null || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()) return;
        revision.incrementAndGet();
        if (worker != null) {
            worker.interrupt();
        }
        pulse.playFromStart();
    }

    private void retryAfterLayout() {
        if (owner.getScene() == null || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()
                || attached.isEmpty()) return;
        if (layoutRetryAttempts <= 0) layoutRetryAttempts = 18;
        layoutRetry.playFromStart();
    }

    private boolean visible(Region card) {
        return card.getScene() != null && card.getWidth() > 0 && card.getHeight() > 0
                && visibleInWindow(card) && visibleInAncestors(card);
    }

    private boolean visibleInWindow(Region card) {
        var window = card.getScene().getWindow();
        if (window != null && (!window.isShowing() || window instanceof javafx.stage.Stage stage && stage.isIconified())) return false;
        var bounds = card.localToScene(card.getBoundsInLocal());
        return bounds.intersects(0, 0, card.getScene().getWidth(), card.getScene().getHeight());
    }

    private boolean visibleInAncestors(Region card) {
        var bounds = card.localToScene(card.getBoundsInLocal());
        for (Node node = card; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
            if (node instanceof ScrollPane scroll) {
                Node viewport = scroll.lookup(".viewport");
                if (viewport == null || viewport.getBoundsInLocal().getWidth() <= 0 || viewport.getBoundsInLocal().getHeight() <= 0) return false;
                if (!bounds.intersects(viewport.localToScene(viewport.getBoundsInLocal()))) return false;
            }
        }
        return true;
    }

    private void refresh() {
        if (owner.getScene() == null || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()) return;
        long request = revision.get();
        var cards = cardsToRefresh(System.currentTimeMillis());
        if (cards.isEmpty()) {
            retryIfLayoutPending();
            return;
        }
        layoutRetryAttempts = 0;
        layoutRetry.stop();
        worker = Thread.ofVirtual().start(() -> refreshWorker(request, cards));
    }

    private List<Map.Entry<Region, Binding>> cardsToRefresh(long now) {
        return attached.entrySet().stream().filter(entry -> {
                    boolean cardVisible = visible(entry.getKey());
                    if (cardVisible) removeCardBoundsListener(entry.getKey());
                    return cardVisible && now >= entry.getValue().refreshAt;
                })
                .sorted(Comparator
                        .comparingDouble((Map.Entry<Region, Binding> entry) -> screenBounds(entry.getKey()).getMinY())
                        .thenComparingDouble(entry -> screenBounds(entry.getKey()).getMinX()))
                .limit(EPG_BATCH_SIZE)
                .map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
    }

    private void retryIfLayoutPending() {
        boolean waitingForLayout = attached.keySet().stream().anyMatch(card ->
                card.getScene() != null && (card.getWidth() <= 0 || card.getHeight() <= 0));
        boolean attachedToScene = attached.keySet().stream().anyMatch(card -> card.getScene() != null);
        if (waitingForLayout || (layoutRetryAttempts > 0 && attachedToScene)) retryAfterLayout();
    }

    private void refreshWorker(long request, List<Map.Entry<Region, Binding>> cards) {
        try {
            List<LiveGuideApplicationService.Row> sources = new ArrayList<>();
            List<Map.Entry<Region, Binding>> permitted = new ArrayList<>();
            collectSources(request, cards, sources, permitted);
            var service = new LiveGuideApplicationService();
            var summaries = service.cachedSummaries(sources);
            publishSummaries(request, permitted, summaries, service.cachedIcons(summaries), service.cachedSourceWarnings(summaries));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception _) {
            Platform.runLater(() -> {
                if (request != revision.get()) return;
                cards.forEach(entry -> {
                    entry.getValue().refreshAt = System.currentTimeMillis() + 30_000;
                    displayUnavailable(entry.getValue().panel);
                });
            });
        } finally {
            Platform.runLater(() -> { if (request == revision.get()) pulse.playFromStart(); });
        }
    }

    private void collectSources(long request, List<Map.Entry<Region, Binding>> cards,
                                List<LiveGuideApplicationService.Row> sources,
                                List<Map.Entry<Region, Binding>> permitted) throws InterruptedException, java.util.concurrent.ExecutionException {
        for (var entry : cards) {
            if (Thread.currentThread().isInterrupted()) return;
            var allowed = new java.util.concurrent.CompletableFuture<Boolean>();
            Platform.runLater(() -> allowed.complete(request == revision.get() && attached.containsKey(entry.getKey()) && visible(entry.getKey())));
            if (Boolean.TRUE.equals(allowed.get())) {
                LiveGuideApplicationService.Row source = entry.getValue().source.get();
                if (source != null) {
                    sources.add(source);
                    permitted.add(entry);
                } else {
                    scheduleEmptySummary(request, entry);
                }
            }
        }
    }

    private void scheduleEmptySummary(long request, Map.Entry<Region, Binding> entry) {
        Platform.runLater(() -> {
            if (request == revision.get() && attached.containsKey(entry.getKey()) && visible(entry.getKey())) {
                entry.getValue().refreshAt = System.currentTimeMillis() + 30 * 60_000L;
                displayPanel(entry.getValue().panel, null, null);
            }
        });
    }

    private void publishSummaries(long request, List<Map.Entry<Region, Binding>> entries,
                                  List<LiveGuideApplicationService.Row> summaries, Map<LiveGuideApplicationService.Row, String> icons,
                                  Map<LiveGuideApplicationService.Row, Boolean> sourceWarnings) {
        for (int index = 0; index < summaries.size(); index++) {
            var entry = entries.get(index);
            var loaded = summaries.get(index);
            long now = System.currentTimeMillis();
            EpgProgramme current = currentProgramme(loaded, now);
            EpgProgramme next = nextProgramme(loaded, now);
            boolean sourceWarning = sourceWarnings.getOrDefault(loaded, false);
            Platform.runLater(() -> publishSummary(request, entry, loaded, current, next, icons, sourceWarning));
        }
    }

    private EpgProgramme currentProgramme(LiveGuideApplicationService.Row row, long now) {
        return row == null ? null : row.programmes().stream().filter(p -> p.start() <= now && now < p.stop()).findFirst().orElse(null);
    }

    private EpgProgramme nextProgramme(LiveGuideApplicationService.Row row, long now) {
        return row == null ? null : row.programmes().stream().filter(p -> p.start() > now).findFirst().orElse(null);
    }

    private void publishSummary(long request, Map.Entry<Region, Binding> entry,
                                LiveGuideApplicationService.Row loaded, EpgProgramme current, EpgProgramme next,
                                Map<LiveGuideApplicationService.Row, String> icons, boolean sourceWarning) {
        if (request != revision.get() || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()
                || !attached.containsKey(entry.getKey()) || !visible(entry.getKey())) return;
        long refreshAt = System.currentTimeMillis() + 30 * 60_000L;
        if (current != null) refreshAt = Math.min(refreshAt, current.stop());
        if (next != null) refreshAt = Math.min(refreshAt, next.start());
        entry.getValue().refreshAt = Math.max(System.currentTimeMillis() + 30_000, refreshAt);
        entry.getValue().current = current;
        entry.getValue().next = next;
        entry.getValue().sourceWarning = sourceWarning;
        displayPanel(entry.getValue().panel, current, next, sourceWarning);
        if (entry.getKey().lookup(".channel-logo-view") instanceof com.uiptv.widget.AsyncImageView image) {
            image.setFallbackImage(icons.getOrDefault(loaded, ""), "channel");
        }
    }

    private static Bounds screenBounds(Region card) {
        return card.localToScene(card.getBoundsInLocal());
    }

    private void restoreSummaries() {
        long now = System.currentTimeMillis();
        attached.forEach((card, binding) -> {
            if (visible(card) && now < binding.refreshAt) displayPanel(binding.panel, binding.current, binding.next, binding.sourceWarning);
        });
    }

    static void display(Pane panel, EpgProgramme current, EpgProgramme next) {
        display(panel, current, next, System.currentTimeMillis());
    }

    static void display(Pane panel, EpgProgramme current, EpgProgramme next, long now) {
        displayPanel(panel, current, next);
    }

    private static void displayPanel(Pane panel, EpgProgramme current, EpgProgramme next) {
        displayPanel(panel, current, next, false);
    }

    private static void displayPanel(Pane panel, EpgProgramme current, EpgProgramme next, boolean sourceWarning) {
        panel.getChildren().clear();
        if (panel instanceof HBox columns) {
            VBox nowColumn = new VBox(3);
            nowColumn.getStyleClass().add("epg-programme-column");
            nowColumn.setMinWidth(0); nowColumn.setMaxWidth(Double.MAX_VALUE);
            VBox nextColumn = new VBox(3);
            nextColumn.getStyleClass().add("epg-programme-column");
            nextColumn.setMinWidth(0); nextColumn.setMaxWidth(Double.MAX_VALUE);
            String missingNow = current != null ? ""
                    : next != null ? "—"
                    : sourceWarning ? I18n.tr("epgSourceUnavailable") : I18n.tr("epgNoGuide");
            nowColumn.getChildren().add(programmeSummary(current, missingNow));
            if (current != null) {
                long now = System.currentTimeMillis();
                double fraction = current.stop() <= current.start() ? 0
                        : Math.clamp((now - current.start()) / (double) (current.stop() - current.start()), 0, 1);
                ProgressBar progress = new ProgressBar(fraction);
                progress.getStyleClass().add("epg-programme-progress");
                progress.setMaxWidth(Double.MAX_VALUE);
                nowColumn.getChildren().add(progress);
            }
            if (sourceWarning && (current != null || next != null)) nowColumn.getChildren().add(sourceWarningLabel(true));
            nextColumn.getChildren().add(programmeSummary(next, next == null ? "—" : ""));
            HBox.setHgrow(nowColumn, Priority.ALWAYS);
            HBox.setHgrow(nextColumn, Priority.ALWAYS);
            columns.getChildren().setAll(nowColumn, nextColumn);
        } else {
            VBox vertical = (VBox) panel;
            vertical.getChildren().add(programme(I18n.tr("epgNow"), current));
            if (current != null) {
                long now = System.currentTimeMillis();
                double fraction = current.stop() <= current.start() ? 0
                        : Math.clamp((now - current.start()) / (double) (current.stop() - current.start()), 0, 1);
                ProgressBar progress = new ProgressBar(fraction);
                progress.getStyleClass().add("epg-programme-progress");
                progress.setMaxWidth(Double.MAX_VALUE);
                vertical.getChildren().add(progress);
            }
            vertical.getChildren().add(programme(I18n.tr("epgNext"), next));
            if (sourceWarning) vertical.getChildren().add(sourceWarningLabel(current != null || next != null));
        }
        panel.setVisible(true); panel.setManaged(true);
    }

    private static Label sourceWarningLabel(boolean hasGuide) {
        Label label = new Label(I18n.tr(hasGuide ? "epgSourceStale" : "epgSourceUnavailable"));
        label.getStyleClass().add("epg-programme-time");
        return label;
    }

    private static VBox programme(String badge, EpgProgramme programme) {
        return programme(badge, programme, true, I18n.tr("epgNoGuide"));
    }

    private static VBox programmeSummary(EpgProgramme programme, String emptyText) {
        return programme("", programme, false, emptyText);
    }

    private static VBox programme(String badge, EpgProgramme programme, boolean showBadge, String emptyText) {
        String text = programme == null ? emptyText : programme.title();
        Label title = new Label(programme == null || !showBadge ? text : badge + ": " + text);
        title.getStyleClass().add("virtual-card-epg-title");
        title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.setTextOverrun(OverrunStyle.ELLIPSIS);
        Tooltip tooltip = new Tooltip(text); tooltip.setWrapText(true); tooltip.setMaxWidth(400); title.setTooltip(tooltip);
        if (programme == null) return new VBox(2, title);
        Label time = new Label(TIME.format(Instant.ofEpochMilli(programme.start()).atZone(ZoneId.systemDefault())) + " – "
                + TIME.format(Instant.ofEpochMilli(programme.stop()).atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("epg-programme-time"); return new VBox(2, title, time);
    }

    private static void displayLoading(Pane panel) {
        panel.getChildren().clear();
        ProgressIndicator loading = new ProgressIndicator();
        loading.setMaxSize(16, 16); loading.setMinSize(16, 16);
        Label label = new Label(I18n.tr("epgLoading"));
        label.getStyleClass().add("epg-programme-time");
        if (panel instanceof HBox row) {
            VBox now = new VBox(3, new HBox(6, loading, label));
            now.getStyleClass().add("epg-programme-column");
            VBox next = programmeSummary(null, "—");
            next.getStyleClass().add("epg-programme-column");
            HBox.setHgrow(now, Priority.ALWAYS); HBox.setHgrow(next, Priority.ALWAYS);
            row.getChildren().setAll(now, next);
        } else ((VBox) panel).getChildren().setAll(new HBox(6, loading, label));
        panel.setVisible(true); panel.setManaged(true);
    }

    private static void displayUnavailable(Pane panel) {
        panel.getChildren().clear();
        Label label = new Label(I18n.tr("epgSourceUnavailable"));
        label.getStyleClass().add("epg-programme-time");
        if (panel instanceof HBox row) {
            VBox now = new VBox(2, label); now.getStyleClass().add("epg-programme-column");
            VBox next = programmeSummary(null, "—"); next.getStyleClass().add("epg-programme-column");
            HBox.setHgrow(now, Priority.ALWAYS); HBox.setHgrow(next, Priority.ALWAYS);
            row.getChildren().setAll(now, next);
        } else panel.getChildren().add(label);
        panel.setVisible(true); panel.setManaged(true);
    }

    void addGuideMenu(ContextMenu menu, Node anchor, Supplier<LiveGuideApplicationService.Row> source) {
        if (!EpgModeUI.isFeatureEnabled()) return;
        MenuItem guide = new MenuItem(I18n.tr("epgOpenGuide"));
        guide.setOnAction(_ -> openGuide(menu, anchor, source));
        menu.getItems().add(guide);
    }

    private void openGuide(ContextMenu menu, Node anchor, Supplier<LiveGuideApplicationService.Row> source) {
        menu.hide();
        if (popup != null) popup.hide();
        long request = ++popupRevision;
        if (popupWorker != null) popupWorker.interrupt();
        popupWorker = Thread.ofVirtual().start(() -> loadGuideRow(request, anchor, source));
    }

    private void loadGuideRow(long request, Node anchor, Supplier<LiveGuideApplicationService.Row> source) {
        LiveGuideApplicationService.Row row = source.get();
        if (row != null) row = new LiveGuideApplicationService().hydrate(row);
        LiveGuideApplicationService.Row loaded = row;
        Platform.runLater(() -> showGuidePopup(request, anchor, loaded));
    }

    private void showGuidePopup(long request, Node anchor, LiveGuideApplicationService.Row row) {
        if (request != popupRevision || owner.getScene() == null || !EpgModeUI.isFeatureEnabled() || row == null) return;
        popup = ChannelGuidePopup.show(anchor == null || anchor.getScene() == null ? owner : anchor, row);
    }

    void stop() {
        revision.incrementAndGet();
        popupRevision++;
        if (popupWorker != null) {
            popupWorker.interrupt();
            popupWorker = null;
        }
        pulse.stop(); layoutRetry.stop(); layoutRetryAttempts = 0;
        clock.stop(); if (worker != null) { worker.interrupt(); worker = null; }
        if (popup != null) { popup.hide(); popup = null; }
        scrollListeners.forEach((scroll, listener) -> scroll.vvalueProperty().removeListener(listener)); scrollListeners.clear();
        viewportBoundsListeners.forEach((scroll, listener) -> scroll.viewportBoundsProperty().removeListener(listener)); viewportBoundsListeners.clear();
        new ArrayList<>(cardBoundsListeners.keySet()).forEach(this::removeCardBoundsListener);
        attached.values().forEach(binding -> { binding.panel.getChildren().clear(); binding.panel.setManaged(false); binding.panel.setVisible(false); });
    }
}
