package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.util.I18n;
import javafx.animation.*;
import javafx.application.Platform;
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
    private final Node owner;
    private final Map<Region, Binding> attached = new IdentityHashMap<>();
    private final PauseTransition pulse = new PauseTransition(Duration.millis(40));
    private final Timeline clock = new Timeline(new KeyFrame(Duration.minutes(1), _ -> { restoreSummaries(); queue(); }));
    private final Map<ScrollPane, javafx.beans.value.ChangeListener<Number>> scrollListeners = new IdentityHashMap<>();
    private Thread worker;
    private Thread popupWorker;
    private long popupRevision;
    private long revision;
    private javafx.stage.Popup popup;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final class Binding {
        final VBox panel;
        final Supplier<LiveGuideApplicationService.Row> source;
        long refreshAt;
        EpgProgramme current, next;
        Binding(VBox panel, Supplier<LiveGuideApplicationService.Row> source) { this.panel = panel; this.source = source; }
    }

    CardEpgSupport(Node owner) {
        this.owner = owner;
        pulse.setOnFinished(_ -> refresh()); clock.setCycleCount(Animation.INDEFINITE);
        owner.sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) stop();
            else if (EpgModeUI.isFeatureEnabled()) { clock.play(); queue(); }
        });
        owner.visibleProperty().addListener((_, _, visible) -> {
            if (!visible) stop();
            else if (EpgModeUI.isFeatureEnabled()) {
                attached.keySet().forEach(this::observeScroll); restoreSummaries(); clock.play(); queue();
            }
        });
        owner.layoutBoundsProperty().addListener((_, _, _) -> queue());
        EpgModeUI.observeCache(owner, () -> {
            if (!EpgModeUI.isFeatureEnabled()) stop();
            else { clock.play(); queue(); }
            attached.values().forEach(binding -> { binding.refreshAt = 0; });
        });
    }

    Region bind(Region card, Supplier<LiveGuideApplicationService.Row> source) {
        if (!EpgModeUI.isFeatureEnabled()) return card;
        VBox panel = new VBox(5); panel.getStyleClass().add("virtual-card-epg");
        panel.setMinWidth(0); panel.setMaxWidth(Double.MAX_VALUE); panel.setVisible(false); panel.setManaged(false);
        card.getStyleClass().add("epg-summary-card");
        if (card instanceof VBox vertical) {
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
        card.sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) { attached.remove(card); queue(); }
            else { attached.put(card, binding); if (System.currentTimeMillis() < binding.refreshAt) display(panel, binding.current, binding.next); observeScroll(card); queue(); }
        });
        if (card.getScene() != null) { attached.put(card, binding); observeScroll(card); queue(); }
        return card;
    }

    private void observeScroll(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll && !scrollListeners.containsKey(scroll)) {
                javafx.beans.value.ChangeListener<Number> listener = (_, _, _) -> queue();
                scroll.vvalueProperty().addListener(listener); scrollListeners.put(scroll, listener);
            }
        }
    }

    void queue() {
        if (owner.getScene() == null || !EpgModeUI.isFeatureEnabled()) return;
        revision++; if (worker != null) worker.interrupt(); pulse.playFromStart();
    }

    private boolean visible(Region card) {
        if (card.getScene() == null || card.getWidth() <= 0 || card.getHeight() <= 0) return false;
        var window = card.getScene().getWindow();
        if (window != null && (!window.isShowing() || window instanceof javafx.stage.Stage stage && stage.isIconified())) return false;
        var bounds = card.localToScene(card.getBoundsInLocal());
        if (!bounds.intersects(0, 0, card.getScene().getWidth(), card.getScene().getHeight())) return false;
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
        if (owner.getScene() == null || !EpgModeUI.isFeatureEnabled()) return;
        long now = System.currentTimeMillis(), request = revision;
        var cards = attached.entrySet().stream().filter(entry -> visible(entry.getKey()) && now >= entry.getValue().refreshAt)
                .limit(16).map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
        if (cards.isEmpty()) return;
        worker = Thread.ofVirtual().start(() -> {
            try {
                List<LiveGuideApplicationService.Row> sources=new ArrayList<>();
                List<Map.Entry<Region,Binding>> permitted=new ArrayList<>();
                for (var entry : cards) {
                    if (Thread.currentThread().isInterrupted()) return;
                    var allowed = new java.util.concurrent.CompletableFuture<Boolean>();
                    Platform.runLater(() -> allowed.complete(request == revision && attached.containsKey(entry.getKey()) && visible(entry.getKey())));
                    if (!allowed.get()) continue;
                    LiveGuideApplicationService.Row source=entry.getValue().source.get();
                    if(source!=null) { sources.add(source); permitted.add(entry); }
                    else Platform.runLater(() -> {
                        if(request==revision && attached.containsKey(entry.getKey()) && visible(entry.getKey())) {
                            entry.getValue().refreshAt=System.currentTimeMillis()+30*60_000L;
                            display(entry.getValue().panel,null,null);
                        }
                    });
                }
                var summaries=new LiveGuideApplicationService().cachedSummaries(sources);
                var icons=new LiveGuideApplicationService().cachedIcons(summaries);
                for(int i=0;i<summaries.size();i++) {
                    var entry=permitted.get(i);
                    LiveGuideApplicationService.Row loaded=summaries.get(i);
                    long time = System.currentTimeMillis();
                    EpgProgramme current = loaded == null ? null : loaded.programmes().stream().filter(p -> p.start() <= time && time < p.stop()).findFirst().orElse(null);
                    EpgProgramme next = loaded == null ? null : loaded.programmes().stream().filter(p -> p.start() > time).findFirst().orElse(null);
                    Platform.runLater(() -> {
                        if (request != revision || !EpgModeUI.isFeatureEnabled() || !attached.containsKey(entry.getKey()) || !visible(entry.getKey())) return;
                        long refreshAt = System.currentTimeMillis() + 30 * 60_000L;
                        if (current != null) refreshAt = Math.min(refreshAt, current.stop());
                        if (next != null) refreshAt = Math.min(refreshAt, next.start());
                        entry.getValue().refreshAt = Math.max(System.currentTimeMillis() + 30_000, refreshAt);
                        entry.getValue().current = current; entry.getValue().next = next;
                        display(entry.getValue().panel,current,next);
                        if(entry.getKey().lookup(".channel-logo-view") instanceof com.uiptv.widget.AsyncImageView image)
                            image.setFallbackImage(icons.getOrDefault(loaded,""),"channel");
                    });
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                Platform.runLater(() -> { if (request == revision) cards.forEach(entry -> entry.getValue().refreshAt = System.currentTimeMillis() + 30_000); });
            } finally {
                Platform.runLater(() -> { if (request == revision) pulse.playFromStart(); });
            }
        });
    }

    private void restoreSummaries() {
        long now = System.currentTimeMillis();
        attached.forEach((card, binding) -> {
            if (visible(card) && now < binding.refreshAt) display(binding.panel, binding.current, binding.next);
        });
    }

    static void display(VBox panel, EpgProgramme current, EpgProgramme next) {
        panel.getChildren().clear();
        panel.getChildren().add(programme(I18n.tr("epgNow"), current));
        double fraction = current == null || current.stop() <= current.start() ? 0
                : Math.clamp((System.currentTimeMillis() - current.start()) / (double) (current.stop() - current.start()), 0, 1);
        ProgressBar progress = new ProgressBar(fraction);
        progress.getStyleClass().add("epg-programme-progress");
        progress.setMaxWidth(Double.MAX_VALUE);
        panel.getChildren().addAll(progress, programme(I18n.tr("epgNext"), next));
        panel.setVisible(true); panel.setManaged(true);
    }

    private static VBox programme(String badge, EpgProgramme programme) {
        String text = programme == null ? I18n.tr("epgNoProgrammeDetails") : programme.title();
        Label title = new Label(badge + ": " + text); title.getStyleClass().add("virtual-card-epg-title");
        title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.setTextOverrun(OverrunStyle.ELLIPSIS);
        Tooltip tooltip = new Tooltip(text); tooltip.setWrapText(true); tooltip.setMaxWidth(400); title.setTooltip(tooltip);
        Label time = new Label(programme == null ? "—" : TIME.format(Instant.ofEpochMilli(programme.start()).atZone(ZoneId.systemDefault())) + " – "
                + TIME.format(Instant.ofEpochMilli(programme.stop()).atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("epg-programme-time"); return new VBox(2, title, time);
    }

    void addGuideMenu(ContextMenu menu, Node anchor, Supplier<LiveGuideApplicationService.Row> source) {
        if (!EpgModeUI.isFeatureEnabled()) return;
        MenuItem guide = new MenuItem(I18n.tr("epgOpenGuide"));
        guide.setOnAction(_ -> {
            menu.hide();
            if (popup != null) popup.hide();
            long request = ++popupRevision;
            if (popupWorker != null) popupWorker.interrupt();
            popupWorker = Thread.ofVirtual().start(() -> {
                LiveGuideApplicationService.Row row = source.get();
                if (row != null) row = new LiveGuideApplicationService().hydrate(row);
                LiveGuideApplicationService.Row loaded = row;
                Platform.runLater(() -> {
                    if (request != popupRevision || owner.getScene() == null || !EpgModeUI.isFeatureEnabled() || loaded == null) return;
                    popup = ChannelGuidePopup.show(anchor == null || anchor.getScene() == null ? owner : anchor, loaded);
                });
            });
        });
        menu.getItems().add(guide);
    }

    void stop() {
        revision++; popupRevision++; if (popupWorker != null) { popupWorker.interrupt(); popupWorker = null; }
        pulse.stop(); clock.stop(); if (worker != null) { worker.interrupt(); worker = null; }
        if (popup != null) { popup.hide(); popup = null; }
        scrollListeners.forEach((scroll, listener) -> scroll.vvalueProperty().removeListener(listener)); scrollListeners.clear();
        attached.values().forEach(binding -> { binding.panel.getChildren().clear(); binding.panel.setManaged(false); binding.panel.setVisible(false); });
    }
}
