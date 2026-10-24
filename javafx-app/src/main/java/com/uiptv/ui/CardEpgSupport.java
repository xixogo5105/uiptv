package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.util.I18n;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
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
    private final Map<Node, javafx.beans.value.ChangeListener<Boolean>> ancestorVisibilityListeners = new IdentityHashMap<>();
    private final Map<Node, javafx.beans.value.ChangeListener<Parent>> ancestorParentListeners = new IdentityHashMap<>();
    private final PauseTransition pulse = new PauseTransition(Duration.millis(40));
    private final PauseTransition layoutRetry = new PauseTransition(Duration.millis(120));
    private final Timeline clock = new Timeline(new KeyFrame(Duration.minutes(1), _ -> { updateProgress(); queue(); }));
    private final Map<ScrollPane, javafx.beans.value.ChangeListener<Number>> scrollListeners = new IdentityHashMap<>();
    private final Map<ScrollPane, javafx.beans.value.ChangeListener<Bounds>> viewportBoundsListeners = new IdentityHashMap<>();
    private boolean compactProgrammeRows;
    private final java.util.concurrent.atomic.AtomicLong nextSummaryFailureLogAt = new java.util.concurrent.atomic.AtomicLong();
    private Thread worker;
    private Thread popupWorker;
    private long popupRevision;
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private int layoutRetryAttempts;
    private javafx.stage.Popup popup;
    private boolean hierarchyRefreshScheduled;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final class Binding {
        final Pane panel;
        final Supplier<LiveGuideApplicationService.Row> source;
        final boolean programmeRow;
        boolean compact;
        long refreshAt;
        EpgProgramme current;
        EpgProgramme next;
        Binding(Pane panel, Supplier<LiveGuideApplicationService.Row> source, boolean programmeRow, boolean compact) {
            this.panel = panel;
            this.source = source;
            this.programmeRow = programmeRow;
            this.compact = compact;
        }
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
            refreshAncestorListeners();
            if (scene == null) stop();
            else scheduleHierarchyRefresh();
        });
        owner.visibleProperty().addListener((_, _, visible) -> {
            if (!visible.booleanValue()) stop();
            else scheduleHierarchyRefresh();
        });
        refreshAncestorListeners();
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

    /**
     * A card grid can stay attached to the scene while its tab or detail-page
     * ancestor is hidden. Observe the whole parent chain so a cache refresh queued
     * behind Settings resumes as soon as the user returns to the cards.
     */
    private void refreshAncestorListeners() {
        Set<Node> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node = owner; node != null; node = node.getParent()) ancestors.add(node);

        for (Node node : new ArrayList<>(ancestorVisibilityListeners.keySet())) {
            if (ancestors.contains(node)) continue;
            node.visibleProperty().removeListener(ancestorVisibilityListeners.remove(node));
            node.parentProperty().removeListener(ancestorParentListeners.remove(node));
        }
        for (Node node : ancestors) {
            if (ancestorVisibilityListeners.containsKey(node)) continue;
            javafx.beans.value.ChangeListener<Boolean> visibilityListener = (_, _, visible) -> {
                if (Boolean.TRUE.equals(visible)) scheduleHierarchyRefresh();
                else stop();
            };
            javafx.beans.value.ChangeListener<Parent> parentListener = (_, _, _) -> refreshAncestorListeners();
            ancestorVisibilityListeners.put(node, visibilityListener);
            ancestorParentListeners.put(node, parentListener);
            node.visibleProperty().addListener(visibilityListener);
            node.parentProperty().addListener(parentListener);
        }
    }

    private void scheduleHierarchyRefresh() {
        if (hierarchyRefreshScheduled) return;
        hierarchyRefreshScheduled = true;
        try {
            Platform.runLater(() -> {
                hierarchyRefreshScheduled = false;
                if (!ownerVisibleInHierarchy() || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()) return;
                attached.keySet().forEach(card -> { observeScroll(card); observeCardBounds(card); });
                restoreSummaries();
                clock.play();
                retryAfterLayout();
                queue();
            });
        } catch (IllegalStateException _) {
            hierarchyRefreshScheduled = false;
        }
    }

    private boolean ownerVisibleInHierarchy() {
        if (owner.getScene() == null) return false;
        for (Node node = owner; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
        }
        var window = owner.getScene().getWindow();
        return window == null || window.isShowing()
                && (!(window instanceof javafx.stage.Stage stage) || !stage.isIconified());
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

    /** Switches full-guide rows to Now-only when the available catalogue pane is narrow. */
    void setCompactProgrammeRows(boolean compact) {
        if (compactProgrammeRows == compact) return;
        compactProgrammeRows = compact;
        attached.forEach((card, binding) -> {
            if (!binding.programmeRow) return;
            binding.compact = compact;
            setProgrammeRowColumns(card, compact);
            if (binding.refreshAt > 0) {
                displayPanel(binding.panel, binding.current, binding.next, System.currentTimeMillis(),
                        compact);
            } else {
                displayLoading(binding.panel, compact);
            }
        });
    }

    private static void setProgrammeRowColumns(Region card, boolean compact) {
        if (!(card instanceof GridPane grid) || !card.getStyleClass().contains("epg-programme-aware-row")
                || grid.getColumnConstraints().size() < 2) return;
        if (grid.getColumnConstraints().size() >= 4) {
            grid.getColumnConstraints().get(0).setPercentWidth(compact ? 38 : 36);
            grid.getColumnConstraints().get(1).setPercentWidth(compact ? 54 : 28);
            grid.getColumnConstraints().get(2).setPercentWidth(compact ? 0 : 28);
            grid.getColumnConstraints().get(3).setPercentWidth(8);
        } else if (grid.getColumnConstraints().size() >= 3) {
            grid.getColumnConstraints().get(0).setPercentWidth(compact ? 38 : 36);
            grid.getColumnConstraints().get(1).setPercentWidth(compact ? 54 : 56);
            grid.getColumnConstraints().get(2).setPercentWidth(8);
        } else {
            grid.getColumnConstraints().get(0).setPercentWidth(compact ? 38 : 36);
            grid.getColumnConstraints().get(1).setPercentWidth(compact ? 62 : 64);
        }
    }

    private void attach(Region card, Supplier<LiveGuideApplicationService.Row> source) {
        boolean programmeRow = card.getStyleClass().contains("epg-programme-aware-row");
        Pane panel = programmeRow ? new HBox(12) : new VBox(5);
        panel.getStyleClass().add("virtual-card-epg");
        panel.setMinWidth(0); panel.setMinHeight(Region.USE_PREF_SIZE); panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE); panel.setVisible(false); panel.setManaged(false);
        card.getStyleClass().add("epg-summary-card");
        card.setMaxHeight(Region.USE_PREF_SIZE);
        if (programmeRow && card instanceof GridPane grid) {
            panel.getStyleClass().add("epg-programme-columns");
            GridPane.setHgrow(panel, Priority.ALWAYS);
            grid.add(panel, 1, 0);
            if (grid.getColumnConstraints().size() >= 4) {
                GridPane.setColumnSpan(panel, 2);
            }
        } else if (card instanceof VBox vertical) {
            vertical.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            VBox.setVgrow(panel, Priority.ALWAYS);
            vertical.getChildren().add(panel);
        } else if (card instanceof HBox horizontal) {
            var children = new ArrayList<>(horizontal.getChildren()); horizontal.getChildren().clear();
            HBox header = new HBox(horizontal.getSpacing()); header.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            header.setMinHeight(Region.USE_PREF_SIZE);
            header.getStyleClass().add("epg-card-header"); header.getChildren().setAll(children);
            for (Node child : children) if (child instanceof VBox text) text.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            VBox body = new VBox(8, header, panel); body.setMinHeight(Region.USE_PREF_SIZE); body.setMinWidth(0); body.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(panel, Priority.ALWAYS);
            HBox.setHgrow(body, Priority.ALWAYS); horizontal.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            horizontal.getChildren().setAll(body);
        }
        Binding binding = new Binding(panel, source, programmeRow, compactProgrammeRows);
        setProgrammeRowColumns(card, binding.compact);
        card.sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) {
                attached.remove(card);
                removeCardBoundsListener(card);
                queue();
            } else {
                attached.put(card, binding);
                if (System.currentTimeMillis() < binding.refreshAt && panel.getChildren().isEmpty()) {
                    displayPanel(panel, binding.current, binding.next, System.currentTimeMillis(),
                            binding.compact);
                }
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
            var icons = service.cachedIcons(summaries);
            publishSummaries(request, permitted, summaries, icons);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            logSummaryFailure(error);
            Platform.runLater(() -> {
                if (request != revision.get()) return;
                cards.forEach(entry -> {
                    entry.getValue().refreshAt = System.currentTimeMillis() + 30_000;
                    Binding binding = entry.getValue();
                    long now = System.currentTimeMillis();
                    displayPanel(binding.panel, binding.current, binding.next, now,
                            binding.compact);
                });
            });
        } finally {
            Platform.runLater(() -> { if (request == revision.get()) pulse.playFromStart(); });
        }
    }

    private void logSummaryFailure(Exception error) {
        long now = System.currentTimeMillis();
        long nextAllowed = nextSummaryFailureLogAt.get();
        if (now < nextAllowed || !nextSummaryFailureLogAt.compareAndSet(nextAllowed, now + 60_000)) return;
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        com.uiptv.util.AppLog.addWarningLog(CardEpgSupport.class,
                "Card EPG summary lookup failed (" + root.getClass().getSimpleName()
                        + "); keeping the last displayed guide.");
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
                long now = System.currentTimeMillis();
                displayPanel(entry.getValue().panel, null, null, now, entry.getValue().compact);
            }
        });
    }

    private void publishSummaries(long request, List<Map.Entry<Region, Binding>> entries,
                                  List<LiveGuideApplicationService.Row> summaries,
                                  Map<LiveGuideApplicationService.Row, String> icons) {
        for (int index = 0; index < summaries.size(); index++) {
            var entry = entries.get(index);
            var loaded = summaries.get(index);
            long now = System.currentTimeMillis();
            EpgProgramme current = currentProgramme(loaded, now);
            EpgProgramme next = nextProgramme(loaded, now);
            Platform.runLater(() -> publishSummary(request, entry, loaded, current, next, icons));
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
                                Map<LiveGuideApplicationService.Row, String> icons) {
        if (request != revision.get() || !EpgModeUI.isFeatureEnabled() || EpgModeUI.isPlaybackOnly()
                || !attached.containsKey(entry.getKey()) || !visible(entry.getKey())) return;
        current = usableProgramme(current);
        next = usableProgramme(next);
        Binding binding = entry.getValue();
        boolean unchanged = Objects.equals(binding.current, current) && Objects.equals(binding.next, next)
                && !binding.panel.getChildren().isEmpty();
        long refreshAt = System.currentTimeMillis() + 30 * 60_000L;
        if (current != null) refreshAt = Math.min(refreshAt, current.stop());
        if (next != null) refreshAt = Math.min(refreshAt, next.start());
        entry.getValue().refreshAt = Math.max(System.currentTimeMillis() + 30_000, refreshAt);
        entry.getValue().current = current;
        entry.getValue().next = next;
        long now = System.currentTimeMillis();
        if (!unchanged) displayPanel(binding.panel, current, next, now, binding.compact);
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
            if (visible(card) && now < binding.refreshAt) {
                displayPanel(binding.panel, binding.current, binding.next, now,
                        binding.compact);
            }
        });
    }

    static void display(Pane panel, EpgProgramme current, EpgProgramme next) {
        display(panel, current, next, System.currentTimeMillis());
    }

    static void display(Pane panel, EpgProgramme current, EpgProgramme next, long now) {
        displayPanel(panel, current, next, now, false);
    }

    static void display(Pane panel, EpgProgramme current, EpgProgramme next, long now, boolean compact) {
        displayPanel(panel, current, next, now, compact);
    }

    private static void displayPanel(Pane panel, EpgProgramme current, EpgProgramme next,
                                     long now, boolean compact) {
        current = usableProgramme(current);
        next = usableProgramme(next);
        panel.getChildren().clear();
        if (current == null && next == null) {
            Label placeholder = programmePlaceholder(I18n.tr("epgNoGuide"));
            panel.getStyleClass().remove("virtual-card-epg-no-guide");
            if (panel instanceof VBox vertical) {
                panel.getStyleClass().add("virtual-card-epg-no-guide");
                Region flexibleSpace = new Region();
                flexibleSpace.setMinHeight(0);
                VBox.setVgrow(flexibleSpace, Priority.ALWAYS);
                Region separator = new Region();
                separator.getStyleClass().add("epg-no-guide-separator");
                separator.setMinHeight(1);
                separator.setPrefHeight(1);
                separator.setMaxHeight(1);
                separator.setMaxWidth(Double.MAX_VALUE);
                vertical.setFillWidth(true);
                vertical.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
                vertical.getChildren().setAll(flexibleSpace, separator, placeholder);
                VBox.setMargin(placeholder, new javafx.geometry.Insets(4, 0, 0, 0));
            } else if (panel instanceof HBox columns) {
                columns.getChildren().setAll(placeholder);
                columns.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                HBox.setHgrow(placeholder, Priority.ALWAYS);
            }
            panel.setVisible(true);
            panel.setManaged(true);
            requestCardHeightMeasurement(panel);
            return;
        }
        panel.getStyleClass().remove("virtual-card-epg-no-guide");
        if (panel instanceof HBox columns) {
            columns.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            VBox nowColumn = new VBox(3);
            nowColumn.getStyleClass().add("epg-programme-column");
            nowColumn.setMinWidth(0); nowColumn.setMaxWidth(Double.MAX_VALUE);
            VBox nextColumn = new VBox(3);
            nextColumn.getStyleClass().add("epg-programme-column");
            nextColumn.setMinWidth(0); nextColumn.setMaxWidth(Double.MAX_VALUE);
            if (current != null) nowColumn.getChildren().add(programmeSummary(current, ""));
            if (validTimes(current) && current.start() <= now && now < current.stop()) {
                double fraction = current.stop() <= current.start() ? 0
                        : Math.clamp((now - current.start()) / (double) (current.stop() - current.start()), 0, 1);
                ProgressBar progress = new ProgressBar(fraction);
                progress.getStyleClass().add("epg-programme-progress");
                progress.setMaxWidth(Double.MAX_VALUE);
                nowColumn.getChildren().add(progress);
            }
            if (next != null) nextColumn.getChildren().add(programmeSummary(next, ""));
            HBox.setHgrow(nowColumn, Priority.ALWAYS);
            HBox.setHgrow(nextColumn, Priority.ALWAYS);
            if (current == null) columns.getChildren().setAll(nextColumn);
            else if (compact || next == null) columns.getChildren().setAll(nowColumn);
            else columns.getChildren().setAll(nowColumn, nextColumn);
        } else {
            VBox vertical = (VBox) panel;
            vertical.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            if (current != null) vertical.getChildren().add(programme(I18n.tr("epgNow"), current));
            if (validTimes(current) && current.start() <= now && now < current.stop()) {
                ProgressBar progress = new ProgressBar(Math.clamp((now - current.start())
                        / (double) (current.stop() - current.start()), 0, 1));
                progress.getStyleClass().add("epg-programme-progress");
                progress.setMaxWidth(Double.MAX_VALUE);
                vertical.getChildren().add(progress);
            }
            if (next != null) vertical.getChildren().add(programme(I18n.tr("epgNext"), next));
        }
        panel.setVisible(true); panel.setManaged(true);
        requestCardHeightMeasurement(panel);
    }

    private static void requestCardHeightMeasurement(Pane panel) {
        for (Node parent = panel.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof Region card && card.getStyleClass().contains("epg-summary-card") && card.getWidth() > 0) {
                card.setMinHeight(Math.max(card.getMinHeight(), Math.ceil(card.prefHeight(card.getWidth()))));
            }
            if (parent instanceof com.uiptv.widget.ResponsiveCardGrid<?> grid) {
                grid.requestCardHeightMeasurement();
                break;
            }
        }
    }

    private static boolean validTimes(EpgProgramme programme) {
        return programme != null && programme.start() > 0 && programme.stop() > programme.start();
    }

    private static final Set<String> NO_GUIDE_TITLES = Set.of(
            "no details available", "no programme details", "no programme details available",
            "no program details", "no program details available", "no programme guide available",
            "no guide available", "no information", "no information available", "no info", "no data",
            "unknown", "unavailable", "n/a", "na", "-"
    );
    private static EpgProgramme usableProgramme(EpgProgramme programme) {
        if (programme == null || programme.title() == null || programme.title().isBlank()) return null;
        String normalized = programme.title().strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (NO_GUIDE_TITLES.contains(normalized) || !validTimes(programme)) return null;
        return programme;
    }

    /** Minute ticks change progress in place; card nodes and scroll state stay intact. */
    private void updateProgress() {
        long now = System.currentTimeMillis();
        attached.forEach((card, binding) -> {
            EpgProgramme progressProgramme = binding.current;
            if (visible(card) && validTimes(progressProgramme)) {
                Node node = binding.panel.lookup(".epg-programme-progress");
                if (node instanceof ProgressBar progress) {
                    boolean active = progressProgramme.start() <= now && now < progressProgramme.stop();
                    progress.setVisible(active); progress.setManaged(active);
                    if (active) progress.setProgress(Math.clamp((now - progressProgramme.start())
                            / (double) (progressProgramme.stop() - progressProgramme.start()), 0, 1));
                }
            }
        });
    }

    private static Label programmePlaceholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("virtual-card-epg-title", "epg-programme-placeholder");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        label.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
        label.setWrapText(true);
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(400);
        label.setTooltip(tooltip);
        return label;
    }

    private static VBox programme(String badge, EpgProgramme programme) {
        return programme(badge, programme, true, I18n.tr("epgNoProgrammeDetails"));
    }

    private static VBox programmeSummary(EpgProgramme programme, String emptyText) {
        return programme("", programme, false, emptyText);
    }

    private static VBox programme(String badge, EpgProgramme programme, boolean showBadge, String emptyText) {
        String text = programme == null ? emptyText : programme.title();
        Label title = new Label(!showBadge ? text : badge + ": " + text);
        title.getStyleClass().add("virtual-card-epg-title");
        title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.setTextOverrun(OverrunStyle.ELLIPSIS); title.setWrapText(true); title.setMinHeight(Region.USE_PREF_SIZE);
        Tooltip tooltip = new Tooltip(text); tooltip.setWrapText(true); tooltip.setMaxWidth(400); title.setTooltip(tooltip);
        if (!validTimes(programme)) return new VBox(2, title);
        Label time = new Label(TIME.format(Instant.ofEpochMilli(programme.start()).atZone(ZoneId.systemDefault())) + " – "
                + TIME.format(Instant.ofEpochMilli(programme.stop()).atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("epg-programme-time"); return new VBox(2, title, time);
    }

    private static void displayLoading(Pane panel, boolean compact) {
        panel.getChildren().clear();
        panel.getStyleClass().remove("virtual-card-epg-no-guide");
        if (panel instanceof VBox vertical) vertical.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        Label label = new Label(I18n.tr("epgLoading"));
        label.getStyleClass().add("epg-programme-time");
        if (panel instanceof HBox row) {
            HBox status = new HBox(6, label);
            status.setAlignment(javafx.geometry.Pos.CENTER);
            status.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(status, Priority.ALWAYS);
            row.setAlignment(javafx.geometry.Pos.CENTER);
            row.getChildren().setAll(status);
        } else ((VBox) panel).getChildren().setAll(new HBox(6, label));
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
