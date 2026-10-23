package com.uiptv.ui;

import com.uiptv.application.EpgRefreshJobService;
import com.uiptv.util.I18n;
import com.uiptv.widget.SegmentedProgressBar;
import com.uiptv.widget.SegmentedProgressBar.SegmentStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows account preparation and source-by-source work; closing leaves the refresh running. */
final class EpgRefreshProgressDialog {
    private static final double SCENE_WIDTH = 1368;
    private static final double SCENE_HEIGHT = 720;

    private EpgRefreshProgressDialog() { }

    static void open(javafx.scene.control.Control owner, Runnable finished) {
        open(owner, finished, null);
    }

    static void open(javafx.scene.control.Control owner, Runnable finished, Runnable guidesRefreshed) {
        var initial = EpgRefreshJobService.start();
        var completionReported = new AtomicBoolean();
        var refreshReported = new AtomicBoolean();
        notifyWhenGuidePublished(initial.id(), guidesRefreshed, refreshReported);
        Stage stage = new Stage();
        Window ownerWindow = owner.getScene() == null ? null : owner.getScene().getWindow();
        if (ownerWindow != null) stage.initOwner(ownerWindow);
        stage.initModality(Modality.NONE);
        stage.setTitle(I18n.tr("epgRefreshGuide"));
        Label title = new Label(I18n.tr("epgRefreshGuide"));
        title.getStyleClass().add("management-popup-title");
        Label phase = new Label();
        phase.getStyleClass().add("management-popup-subtitle");
        VBox header = new VBox(3, title, phase);
        header.setMinHeight(Region.USE_PREF_SIZE);
        header.getStyleClass().add("management-popup-header");

        Label count = new Label();
        count.getStyleClass().add("verification-progress-summary");
        count.setMinWidth(Region.USE_PREF_SIZE);
        ProgressIndicator activity = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        activity.setMinSize(22, 22);
        activity.setPrefSize(22, 22);
        activity.setMaxSize(22, 22);
        SegmentedProgressBar progress = new SegmentedProgressBar();
        progress.setMinWidth(0);
        HBox.setHgrow(progress, Priority.ALWAYS);
        HBox progressRow = new HBox(14, activity, count, progress);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.getStyleClass().add("verification-progress-row");
        Label current = new Label();
        current.getStyleClass().add("management-popup-subtitle");
        current.setWrapText(true);
        Label elapsed = new Label();
        elapsed.getStyleClass().add("management-popup-subtitle");
        Label phaseElapsed = new Label();
        phaseElapsed.getStyleClass().add("management-popup-subtitle");
        phaseElapsed.setMinWidth(Region.USE_PREF_SIZE);
        HBox progressDetails = new HBox(12, current, spacer(), phaseElapsed, elapsed);
        progressDetails.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(current, Priority.ALWAYS);
        current.setMinWidth(0);
        current.setMaxWidth(Double.MAX_VALUE);
        VBox progressCard = createProgressCard(progressRow, progressDetails);

        Label sourceHeading = new Label(I18n.tr("epgRefreshSourcesHeading"));
        sourceHeading.getStyleClass().add("management-popup-section-title");
        Label message = new Label();
        message.getStyleClass().add("epg-refresh-message");
        message.setWrapText(true);
        Label preparing = new Label();
        preparing.getStyleClass().add("epg-refresh-preparing");
        preparing.setWrapText(true);

        SourceCards sourceCards = new SourceCards();
        ScrollPane sources = new ScrollPane(sourceCards);
        sources.setFitToWidth(true);
        sources.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sources.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sources.getStyleClass().addAll("transparent-scroll-pane", "epg-refresh-source-list");
        sources.setMinHeight(0);
        sources.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(sources, Priority.ALWAYS);

        Label summaryTitle = new Label(I18n.tr("autoRunSummary"));
        summaryTitle.getStyleClass().add("management-popup-section-title");
        Label summaryCompleted = summaryLine();
        Label summaryGood = summaryLine();
        Label summaryPartial = summaryLine();
        Label summaryFailed = summaryLine();
        Label summaryProgrammes = summaryLine();
        VBox summaryCard = createSummaryCard(summaryTitle, summaryCompleted, summaryGood, summaryPartial,
                summaryFailed, summaryProgrammes);
        summaryCard.setVisible(false);
        summaryCard.setManaged(false);
        Separator summarySeparator = new Separator();
        summarySeparator.getStyleClass().add("epg-refresh-summary-separator");
        summarySeparator.setVisible(false);
        summarySeparator.setManaged(false);

        VBox sourceListAndSummary = new VBox(9, sources, summarySeparator, summaryCard);
        sourceListAndSummary.setMinHeight(0);
        sourceListAndSummary.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        sourceListAndSummary.setFillWidth(true);
        VBox.setVgrow(sources, Priority.ALWAYS);
        StackPane sourceContent = new StackPane(sourceListAndSummary, preparing);
        sourceContent.setAlignment(Pos.CENTER);
        sourceContent.setMinHeight(0);
        VBox.setVgrow(sourceContent, Priority.ALWAYS);
        VBox sourcesCard = new VBox(10, sourceHeading, message, sourceContent);
        sourcesCard.getStyleClass().addAll("management-popup-card", "reload-column-card", "verification-log-card", "epg-refresh-sources-card");
        sourcesCard.setFillWidth(true);
        sourcesCard.setMinHeight(0);
        VBox.setVgrow(sourcesCard, Priority.ALWAYS);

        Button action = new Button(I18n.tr("autoCancel"));
        action.getStyleClass().add("reload-secondary-button");
        HBox footer = new HBox(action);
        footer.getStyleClass().add("management-popup-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = createLayout(header, progressCard, sourcesCard, footer);
        Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
        RootApplication.applyThemeToPopupScene(scene);
        stage.setScene(scene);
        stage.setMaximized(true);

        Map<String, String> renderedStatuses = new HashMap<>();
        String[] renderedProgressPhase = {""};
        int[] renderedProgressTotal = {-1};
        int[] renderedPreparationCompleted = {0};
        String[] previousStage = {""};
        String[] previousSource = {""};
        long[] phaseStartedAt = {initial.startedAt()};
        action.setOnAction(_ -> {
            var latest = EpgRefreshJobService.get(initial.id());
            if (latest == null || terminal(latest.status())) stage.close();
            else {
                action.setDisable(true);
                EpgRefreshJobService.cancel(initial.id());
            }
        });

        Runnable update = () -> {
            var snapshot = EpgRefreshJobService.get(initial.id());
            if (snapshot == null) return;
            sourceCards.update(snapshot.sources());
            String localizedStage = localizedStage(snapshot.stage());
            phase.setText(localizedStage);
            if (!snapshot.stage().equals(previousStage[0]) || !snapshot.currentSource().equals(previousSource[0])) {
                previousStage[0] = snapshot.stage();
                previousSource[0] = snapshot.currentSource();
                phaseStartedAt[0] = System.currentTimeMillis();
            }
            boolean preparingAccounts = snapshot.total() < 0;
            if (preparingAccounts) {
                count.setText(snapshot.preparationTotal() > 0
                        ? I18n.tr("epgRefreshAccountsChecked", snapshot.preparationCompleted(), snapshot.preparationTotal())
                        : I18n.tr("epgRefreshPreparing"));
                configureProgress(progress, renderedProgressPhase, renderedProgressTotal,
                        snapshot.preparationTotal() > 0 ? "accounts" : "waiting",
                        snapshot.preparationTotal() > 0 ? snapshot.preparationTotal() : 1);
                if (snapshot.preparationTotal() > 0) {
                    for (int i = renderedPreparationCompleted[0]; i < snapshot.preparationCompleted(); i++) {
                        progress.updateSegment(i, SegmentStatus.SUCCESS);
                    }
                    renderedPreparationCompleted[0] = snapshot.preparationCompleted();
                }
                current.setText(snapshot.currentAccount().isBlank() ? ""
                        : I18n.tr("epgRefreshCurrentAccount", snapshot.currentAccount()));
                preparing.setText(snapshot.currentAccount().isBlank()
                        ? localizedStage(snapshot.stage()) : I18n.tr("epgRefreshCurrentAccount", snapshot.currentAccount()));
            } else {
                count.setText(I18n.tr("epgRefreshSourceCount", snapshot.completed(), snapshot.total()));
                configureProgress(progress, renderedProgressPhase, renderedProgressTotal, "sources", snapshot.total());
                for (int index = 0; index < snapshot.sources().size(); index++) {
                    EpgRefreshJobService.SourceProgress source = snapshot.sources().get(index);
                    String previous = renderedStatuses.put(source.id(), source.status());
                    if (!source.status().equals(previous)) {
                        if ("Completed".equals(source.status())) progress.updateSegment(index, SegmentStatus.SUCCESS);
                        else if ("No matches".equals(source.status())) progress.updateSegment(index, SegmentStatus.WARNING);
                        else if ("Failed".equals(source.status())) progress.updateSegment(index, SegmentStatus.FAILURE);
                        else if (!"Pending".equals(source.status())) progress.updateSegment(index, SegmentStatus.PROCESSING);
                    }
                }
            current.setText(terminal(snapshot.status()) ? "" : !snapshot.currentAccount().isBlank()
                        ? I18n.tr("epgRefreshCurrentAccount", snapshot.currentAccount())
                        : snapshot.currentSource().isBlank() ? ""
                        : I18n.tr("epgRefreshCurrentSource", snapshot.currentSource()));
                preparing.setText("");
            }
            long elapsedMillis = Math.max(0, (snapshot.finishedAt() == 0
                    ? System.currentTimeMillis() : snapshot.finishedAt()) - snapshot.startedAt());
            elapsed.setText(I18n.tr("epgRefreshElapsed", formatElapsed(elapsedMillis)));
            phaseElapsed.setText(terminal(snapshot.status()) ? ""
                    : localizedStage + " · " + formatElapsed(System.currentTimeMillis() - phaseStartedAt[0]));
            activity.setVisible(!terminal(snapshot.status()));
            activity.setManaged(!terminal(snapshot.status()));
            message.setText(snapshot.message());
            message.setVisible(!snapshot.message().isBlank());
            message.setManaged(!snapshot.message().isBlank());
            boolean showSources = !preparingAccounts && !snapshot.sources().isEmpty();
            sources.setVisible(showSources);
            sources.setManaged(showSources);
            boolean showSummary = !preparingAccounts && terminal(snapshot.status()) && !snapshot.sources().isEmpty();
            summaryCompleted.setText(I18n.tr("reloadSummaryCompleted", snapshot.completed(), snapshot.total()));
            summaryGood.setText(I18n.tr("reloadSummaryGood", countSources(snapshot.sources(), "Completed", "Saved")));
            summaryPartial.setText(I18n.tr("reloadSummaryYellow", countSources(snapshot.sources(), "No matches")));
            summaryFailed.setText(I18n.tr("reloadSummaryBad", countSources(snapshot.sources(), "Failed")));
            long totalProgrammes = snapshot.sources().stream().mapToLong(EpgRefreshJobService.SourceProgress::programmes).sum();
            summaryProgrammes.setText(I18n.tr("epgRefreshProcessedProgrammesDetail", I18n.formatNumber(Long.toString(totalProgrammes))));
            summaryCard.setVisible(showSummary);
            summaryCard.setManaged(showSummary);
            summarySeparator.setVisible(showSummary);
            summarySeparator.setManaged(showSummary);
            boolean showPreparing = !showSources;
            if (showPreparing && !preparingAccounts) preparing.setText(localizedStage);
            preparing.setVisible(showPreparing);
            preparing.setManaged(showPreparing);
            if (terminal(snapshot.status())) {
                action.setDisable(false);
                action.setText(I18n.tr("commonClose"));
                if (finished != null && completionReported.compareAndSet(false, true)) finished.run();
            }
        };

        Timeline poll = new Timeline(new KeyFrame(Duration.millis(300), _ -> update.run()));
        poll.setCycleCount(Timeline.INDEFINITE);
        stage.setOnHidden(_ -> {
            poll.stop();
            if (finished != null && completionReported.compareAndSet(false, true)) finished.run();
        });
        update.run();
        poll.play();
        stage.show();
    }

    static VBox createProgressCard(Region progressRow, Region progressDetails) {
        VBox card = new VBox(12, progressRow, progressDetails);
        card.getStyleClass().addAll("management-popup-card", "epg-refresh-progress-card", "verification-progress-card");
        card.setFillWidth(true);
        card.setMinHeight(Region.USE_PREF_SIZE);
        return card;
    }

    static VBox createSummaryCard(Label title, Label... statistics) {
        FlowPane stats = new FlowPane(24, 8, statistics);
        VBox card = new VBox(10, title, stats);
        card.getStyleClass().addAll("reload-summary-box", "epg-refresh-run-summary");
        card.setPadding(new Insets(10));
        card.setMinHeight(Region.USE_PREF_SIZE);
        return card;
    }

    static BorderPane createLayout(VBox header, VBox progressCard, VBox sourcesCard, HBox footer) {
        header.setMinHeight(Region.USE_PREF_SIZE);
        VBox main = new VBox(14, header, progressCard, sourcesCard);
        main.setMinSize(0, 0);
        VBox.setVgrow(sourcesCard, Priority.ALWAYS);
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("management-popup-root", "reload-cache-popup", "verification-progress-root");
        root.setPadding(new Insets(18));
        root.setCenter(main);
        BorderPane.setMargin(footer, new Insets(14, 0, 0, 0));
        root.setBottom(footer);
        root.setMinSize(0, 0);
        return root;
    }

    private static boolean configureProgress(SegmentedProgressBar progress, String[] currentPhase,
                                             int[] currentTotal, String phase, int total) {
        if (!phase.equals(currentPhase[0]) || total != currentTotal[0]) {
            progress.setTotal(total);
            currentPhase[0] = phase;
            currentTotal[0] = total;
            return true;
        }
        return false;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static Label summaryLine() {
        Label label = new Label();
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("epg-refresh-summary-line");
        return label;
    }

    private static long countSources(java.util.List<EpgRefreshJobService.SourceProgress> sources, String... statuses) {
        java.util.Set<String> accepted = java.util.Set.of(statuses);
        return sources.stream().filter(source -> accepted.contains(source.status())).count();
    }

    private static boolean terminal(String status) {
        return !"PREPARING".equals(status) && !"RUNNING".equals(status);
    }

    static boolean hasPublishedGuide(EpgRefreshJobService.Snapshot snapshot) {
        return snapshot != null
                && ("COMPLETED".equals(snapshot.status()) || "COMPLETED_WITH_WARNINGS".equals(snapshot.status()))
                && snapshot.sources().stream().anyMatch(source ->
                "Completed".equals(source.status()) || "Saved".equals(source.status()));
    }

    private static void notifyWhenGuidePublished(String jobId, Runnable guidesRefreshed,
                                                 AtomicBoolean refreshReported) {
        if (guidesRefreshed == null) return;
        Thread.ofVirtual().name("epg-refresh-card-update-" + jobId).start(() -> {
            EpgRefreshJobService.Snapshot snapshot;
            do {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
                snapshot = EpgRefreshJobService.get(jobId);
            } while (snapshot != null && !terminal(snapshot.status()));
            if (hasPublishedGuide(snapshot) && refreshReported.compareAndSet(false, true)) {
                try {
                    Platform.runLater(guidesRefreshed);
                } catch (IllegalStateException _) {
                    // The application can be shutting down after the refresh has completed.
                }
            }
        });
    }

    private static boolean isActiveSourceStatus(String status) {
        return switch (status) {
            case "Pending", "Completed", "Saved", "No matches", "Failed", "Skipped" -> false;
            default -> status != null && !status.isBlank();
        };
    }

    private static String localizedStage(String stage) {
        return switch (stage) {
            case "Waiting for another EPG refresh to stop" -> I18n.tr("epgRefreshWaiting");
            case "Preparing", "Reading accounts", "Checking account sources", "Checking global sources",
                    "Preparing sources", "Ready" -> I18n.tr("epgRefreshPreparing");
            case "Downloading" -> I18n.tr("epgRefreshDownloading");
            case "Parsing" -> I18n.tr("epgRefreshParsing");
            case "Matching", "Linking channels" -> I18n.tr("epgRefreshMatching");
            case "Loading cached channels" -> I18n.tr("epgRefreshLoadingCachedChannels");
            case "Saving" -> I18n.tr("epgRefreshSaving");
            case "Staging" -> I18n.tr("epgRefreshStaging");
            case "Staged" -> I18n.tr("epgRefreshStaged");
            case "Publishing" -> I18n.tr("epgRefreshPublishing");
            case "Source unavailable" -> I18n.tr("epgSourceUnavailable");
            case "Completed" -> I18n.tr("epgRefreshCompleted");
            case "Warnings" -> I18n.tr("epgRefreshWarnings");
            case "Saved" -> I18n.tr("reloadSaved");
            case "No sources configured" -> I18n.tr("epgRefreshNoSources");
            case "No matches" -> I18n.tr("epgRefreshNoMatches");
            case "Cancelled" -> I18n.tr("epgRefreshCancelled");
            case "Skipped" -> I18n.tr("epgRefreshSkipped");
            case "Failed" -> I18n.tr("autoFailed2");
            default -> stage;
        };
    }

    private static String localizedSourceStatus(String status) {
        return switch (status) {
            case "Pending" -> I18n.tr("epgRefreshPending");
            case "Completed" -> I18n.tr("epgRefreshCompleted");
            case "Saved" -> I18n.tr("reloadSaved");
            case "No matches" -> I18n.tr("epgRefreshNoMatches");
            case "Failed" -> I18n.tr("autoFailed2");
            default -> localizedStage(status);
        };
    }

    private static String localizedSourceDetail(String detail) {
        if (detail == null || detail.isBlank()) return "";
        return switch (detail) {
            case "epgSourceTooLarge", "epgSourceProgrammeLimit", "epgSourceInvalid", "epgSourceUnavailable",
                    "epgSourceXmlLimit" -> I18n.tr(detail);
            default -> detail;
        };
    }

    private static String formatElapsed(long millis) {
        long seconds = millis / 1_000;
        long hours = seconds / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remainder = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, remainder)
                : "%02d:%02d".formatted(minutes, remainder);
    }

    /** Stable source identities: polling never replaces nodes or changes a clicked card. */
    static final class SourceCards extends VBox {
        final Map<String, SourceCard> cards = new java.util.LinkedHashMap<>();

        SourceCards() {
            super(8);
            setFillWidth(true);
            setMinWidth(0);
        }

        void update(java.util.List<EpgRefreshJobService.SourceProgress> sources) {
            for (var source : sources) {
                SourceCard card = cards.computeIfAbsent(source.id(), id -> {
                    SourceCard created = new SourceCard(id);
                    getChildren().add(created);
                    return created;
                });
                card.update(source);
            }
        }
    }

    static final class SourceCard extends VBox {
        final String sourceId;
        final HBox header = new HBox(8);
        final Label name = new Label();
        final Label status = new Label();
        final Label arrow = new Label("▸");
        final VBox body = new VBox(6);
        private final Map<String, SourceStep> steps = new java.util.LinkedHashMap<>();
        private Boolean userExpanded;
        private boolean started;

        SourceCard(String sourceId) {
            super(6);
            this.sourceId = sourceId;
            getStyleClass().addAll("reload-log-root", "epg-refresh-source-card");
            setMinHeight(Region.USE_PREF_SIZE);
            header.getStyleClass().add("reload-log-header");
            header.setAlignment(Pos.CENTER_LEFT);
            header.setFocusTraversable(true);
            header.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
            name.getStyleClass().add("reload-account-title");
            name.setMinWidth(0);
            name.setWrapText(true);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            status.setMinWidth(Region.USE_PREF_SIZE);
            status.getStyleClass().add("reload-status-label");
            arrow.setMinWidth(18);
            header.getChildren().setAll(name, status, arrow);
            header.setOnMouseClicked(event -> toggle());
            header.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER
                        || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                    toggle();
                    event.consume();
                }
            });
            body.getStyleClass().addAll("reload-log-body", "epg-refresh-source-body");
            body.setMinHeight(Region.USE_PREF_SIZE);
            getChildren().setAll(header, body);
            renderExpansion();
        }

        void toggle() {
            userExpanded = !body.isManaged();
            renderExpansion();
        }

        private void renderExpansion() {
            boolean expanded = userExpanded == null ? started : userExpanded;
            body.setVisible(expanded);
            body.setManaged(expanded);
            arrow.setText(expanded ? "▾" : "▸");
        }

        void update(EpgRefreshJobService.SourceProgress item) {
            name.setText(item.fileName().isBlank() ? item.name() : item.name() + " · " + item.fileName());
            status.setText(localizedSourceStatus(item.status()));
            boolean completed = "Completed".equals(item.status());
            boolean failed = "Failed".equals(item.status());
            boolean warning = "No matches".equals(item.status());
            status.getStyleClass().setAll("reload-status-label", completed ? "reload-status-done"
                    : failed ? "reload-status-bad" : warning ? "reload-status-yellow"
                    : isActiveSourceStatus(item.status()) ? "reload-status-running" : "reload-status-queued");
            if (!"Pending".equals(item.status())) started = true;
            String detail = localizedSourceDetail(item.detail());
            if (detail.isBlank() && (completed || warning)) {
                detail = warning ? item.programmes() == 0
                        ? I18n.tr("epgRefreshNoCurrentProgrammesDetail")
                        : I18n.tr("epgRefreshNoMatchesDetail", I18n.formatNumber(Long.toString(item.programmes())))
                        : I18n.tr("epgRefreshImportedDetail", I18n.formatNumber(Long.toString(item.programmes())),
                                I18n.formatNumber(Integer.toString(item.matchedChannels())));
            }
            if (!"Pending".equals(item.status())) {
                String stepKey = "Saved".equals(item.status()) ? "Saving" : "Staged".equals(item.status()) ? "Staging" : item.status();
                SourceStep step = steps.computeIfAbsent(stepKey, key -> {
                    SourceStep line = new SourceStep();
                    body.getChildren().add(line);
                    return line;
                });
                step.stage.setText(localizedSourceStatus(item.status()));
                step.detail.setText(detail);
            }
            header.setAccessibleText(name.getText() + ", " + status.getText());
            renderExpansion();
        }
    }

    static final class SourceStep extends VBox {
        final Label stage = new Label();
        final Label detail = new Label();

        SourceStep() {
            super(2);
            getStyleClass().add("epg-refresh-step");
            setMinWidth(0);
            setMinHeight(Region.USE_PREF_SIZE);
            setMaxWidth(Double.MAX_VALUE);
            stage.getStyleClass().add("epg-refresh-step-stage");
            for (Label label : java.util.List.of(stage, detail)) {
                label.setWrapText(true);
                label.setMinWidth(0);
                label.setMinHeight(Region.USE_PREF_SIZE);
                label.setMaxWidth(Double.MAX_VALUE);
            }
            detail.getStyleClass().add("epg-refresh-source-summary");
            getChildren().setAll(stage, detail);
        }
    }
}
