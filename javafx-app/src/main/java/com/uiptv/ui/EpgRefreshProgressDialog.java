package com.uiptv.ui;

import com.uiptv.application.EpgRefreshJobService;
import com.uiptv.util.I18n;
import com.uiptv.widget.SegmentedProgressBar;
import com.uiptv.widget.SegmentedProgressBar.SegmentStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
import java.util.function.Consumer;

/** Shows account preparation and source-by-source work; closing leaves the refresh running. */
final class EpgRefreshProgressDialog {
    private static final double SCENE_WIDTH = 1368;
    private static final double SCENE_HEIGHT = 720;

    private EpgRefreshProgressDialog() { }

    static void open(javafx.scene.control.Control owner, Runnable finished) {
        var initial = EpgRefreshJobService.start();
        var completionReported = new AtomicBoolean();
        Stage stage = new Stage();
        Window ownerWindow = owner.getScene() == null ? null : owner.getScene().getWindow();
        if (ownerWindow != null) stage.initOwner(ownerWindow);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(I18n.tr("epgRefreshGuide"));
        Label title = new Label(I18n.tr("epgRefreshGuide"));
        title.getStyleClass().add("management-popup-title");
        Label phase = new Label();
        phase.getStyleClass().add("management-popup-subtitle");
        VBox header = new VBox(3, title, phase);
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
        VBox progressCard = new VBox(12, progressRow, progressDetails);
        progressCard.getStyleClass().addAll("management-popup-card", "reload-progress-card", "verification-progress-card");
        progressCard.setFillWidth(true);

        Label sourceHeading = new Label(I18n.tr("epgRefreshSourcesHeading"));
        sourceHeading.getStyleClass().add("management-popup-section-title");
        Label message = new Label();
        message.getStyleClass().add("epg-refresh-message");
        message.setWrapText(true);
        Label preparing = new Label();
        preparing.getStyleClass().add("epg-refresh-preparing");
        preparing.setWrapText(true);

        ListView<EpgRefreshJobService.SourceProgress> sources = new ListView<>();
        sources.getStyleClass().add("epg-refresh-source-list");
        sources.setMinHeight(0);
        sources.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        String[] expandedSourceId = {activeSourceId(initial.sources())};
        String[] previousActiveSourceId = {null};
        boolean[] previousTerminal = {terminal(initial.status())};
        if (previousTerminal[0]) expandedSourceId[0] = null;
        sources.setCellFactory(_ -> new SourceCell(expandedSourceId, sourceId -> {
            expandedSourceId[0] = Objects.equals(expandedSourceId[0], sourceId) ? null : sourceId;
            sources.refresh();
        }));
        VBox.setVgrow(sources, Priority.ALWAYS);
        StackPane sourceContent = new StackPane(sources, preparing);
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
            int activeIndexToScroll = -1;
            boolean snapshotTerminal = terminal(snapshot.status());
            String activeId = activeSourceId(snapshot.sources());
            if (!snapshotTerminal && !Objects.equals(activeId, previousActiveSourceId[0])) {
                previousActiveSourceId[0] = activeId;
                if (activeId != null) {
                    expandedSourceId[0] = activeId;
                    activeIndexToScroll = sourceIndex(snapshot.sources(), activeId);
                }
            } else if (snapshotTerminal && !previousTerminal[0]) {
                previousActiveSourceId[0] = null;
                expandedSourceId[0] = null;
            }
            previousTerminal[0] = snapshotTerminal;
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
                    }
                }
                current.setText(snapshot.currentSource().isBlank() ? ""
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
            if (!sources.getItems().equals(snapshot.sources())) sources.getItems().setAll(snapshot.sources());
            boolean showSources = !preparingAccounts && !snapshot.sources().isEmpty();
            sources.setVisible(showSources);
            sources.setManaged(showSources);
            boolean showPreparing = !showSources;
            if (showPreparing && !preparingAccounts) preparing.setText(localizedStage);
            preparing.setVisible(showPreparing);
            preparing.setManaged(showPreparing);
            if (activeIndexToScroll >= 0) sources.scrollTo(activeIndexToScroll);
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

    private static boolean terminal(String status) {
        return !"PREPARING".equals(status) && !"RUNNING".equals(status);
    }

    private static String activeSourceId(java.util.List<EpgRefreshJobService.SourceProgress> sources) {
        return sources.stream().filter(source -> isActiveSourceStatus(source.status()))
                .map(EpgRefreshJobService.SourceProgress::id).findFirst().orElse(null);
    }

    private static int sourceIndex(java.util.List<EpgRefreshJobService.SourceProgress> sources, String id) {
        for (int index = 0; index < sources.size(); index++) {
            if (Objects.equals(sources.get(index).id(), id)) return index;
        }
        return -1;
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
            case "Source unavailable" -> I18n.tr("epgSourceUnavailable");
            case "Completed", "Saved" -> I18n.tr("epgRefreshCompleted");
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
            case "Completed", "Saved" -> I18n.tr("epgRefreshCompleted");
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

    private static final class SourceCell extends ListCell<EpgRefreshJobService.SourceProgress> {
        private final String[] expandedSourceId;
        private final Consumer<String> expansionRequested;
        private final VBox card = new VBox();
        private final Button header = new Button();
        private final HBox headerRow = new HBox(10);
        private final Label icon = new Label();
        private final VBox titleBox = new VBox(2);
        private final Label name = new Label();
        private final Label fileName = new Label();
        private final Label summary = new Label();
        private final Label status = new Label();
        private final Label arrow = new Label();
        private final Label detail = new Label();
        private String sourceId;

        private SourceCell(String[] expandedSourceId, Consumer<String> expansionRequested) {
            this.expandedSourceId = expandedSourceId;
            this.expansionRequested = expansionRequested;
            name.getStyleClass().addAll("epg-refresh-source-name", "reload-account-title");
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            fileName.getStyleClass().add("epg-refresh-source-filename");
            fileName.setWrapText(true);
            fileName.setMinWidth(0);
            fileName.setMaxWidth(Double.MAX_VALUE);
            summary.getStyleClass().add("epg-refresh-source-summary");
            summary.setWrapText(true);
            summary.setMinWidth(0);
            summary.setMaxWidth(Double.MAX_VALUE);
            titleBox.getChildren().setAll(name, fileName, summary);
            titleBox.setMinWidth(0);
            titleBox.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleBox, Priority.ALWAYS);

            icon.getStyleClass().add("epg-refresh-source-icon");
            icon.setMinWidth(24);
            icon.setAlignment(Pos.CENTER);
            status.getStyleClass().add("epg-refresh-source-status");
            status.setWrapText(true);
            status.setMinWidth(0);
            status.setMaxWidth(320);
            arrow.getStyleClass().add("epg-refresh-source-chevron");
            arrow.setMinWidth(18);
            arrow.setAlignment(Pos.CENTER);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            headerRow.setMaxWidth(Double.MAX_VALUE);
            headerRow.getChildren().setAll(icon, titleBox, status, arrow);

            header.getStyleClass().add("epg-refresh-source-header");
            header.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            header.setGraphic(headerRow);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMaxWidth(Double.MAX_VALUE);
            header.setFocusTraversable(true);
            header.setOnAction(_ -> {
                if (sourceId != null) expansionRequested.accept(sourceId);
            });

            detail.getStyleClass().addAll("management-popup-subtitle", "epg-refresh-source-detail");
            detail.setWrapText(true);
            detail.setMinWidth(0);
            detail.setMaxWidth(Double.MAX_VALUE);
            VBox body = new VBox(detail);
            body.getStyleClass().add("epg-refresh-source-body");
            body.setMinWidth(0);
            body.setMaxWidth(Double.MAX_VALUE);
            card.getStyleClass().addAll("reload-log-root", "epg-refresh-source-card");
            card.getChildren().setAll(header, body);
            card.setFillWidth(true);
            card.setMinWidth(0);
            card.setMaxWidth(Double.MAX_VALUE);
            card.prefWidthProperty().bind(widthProperty().subtract(20));
        }

        @Override
        protected void updateItem(EpgRefreshJobService.SourceProgress item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                sourceId = null;
                return;
            }
            sourceId = item.id();
            name.setText(item.name());
            fileName.setText(item.fileName());
            fileName.setVisible(!item.fileName().isBlank());
            fileName.setManaged(!item.fileName().isBlank());
            boolean completed = "Completed".equals(item.status()) || "Saved".equals(item.status());
            boolean failed = "Failed".equals(item.status());
            boolean warning = "No matches".equals(item.status());
            boolean active = isActiveSourceStatus(item.status());
            icon.setText(completed ? "✓" : failed ? "✕" : warning ? "!" : active ? "◌" : "•");
            icon.getStyleClass().setAll("epg-refresh-source-icon",
                    completed ? "success" : failed ? "failure" : warning ? "warning" : active ? "active" : "pending");
            status.setText(localizedSourceStatus(item.status()));
            status.getStyleClass().setAll("epg-refresh-source-status");
            if (completed) status.getStyleClass().add("success");
            else if (failed) status.getStyleClass().add("failure");
            else if (warning) status.getStyleClass().add("warning");
            else if (active) status.getStyleClass().add("active");
            boolean expanded = Objects.equals(expandedSourceId[0], item.id());
            arrow.setText(expanded ? "▾" : "▸");
            String detailText = localizedSourceDetail(item.detail());
            detail.setText(detailText.isBlank() ? localizedSourceStatus(item.status()) : detailText);
            boolean showCollapsedSummary = !expanded && !active && !detailText.isBlank();
            summary.setText(detailText);
            summary.setVisible(showCollapsedSummary);
            summary.setManaged(showCollapsedSummary);
            header.setAccessibleText(item.name() + (item.fileName().isBlank() ? "" : " " + item.fileName())
                    + ", " + localizedSourceStatus(item.status()));
            header.setAccessibleHelp(detail.getText());
            detail.setVisible(expanded);
            detail.setManaged(expanded);
            setGraphic(card);
            setText(null);
        }
    }
}
