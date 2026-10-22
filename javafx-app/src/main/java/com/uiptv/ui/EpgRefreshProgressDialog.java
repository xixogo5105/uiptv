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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows account preparation and source-by-source work; closing leaves the refresh running. */
final class EpgRefreshProgressDialog {
    private static final double SCENE_WIDTH = 1180;
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
        SegmentedProgressBar progress = new SegmentedProgressBar();
        progress.setMinWidth(0);
        HBox.setHgrow(progress, Priority.ALWAYS);
        HBox progressRow = new HBox(14, count, progress);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.getStyleClass().add("verification-progress-row");
        Label current = new Label();
        current.getStyleClass().add("management-popup-subtitle");
        current.setWrapText(true);
        Label elapsed = new Label();
        elapsed.getStyleClass().add("management-popup-subtitle");
        HBox progressDetails = new HBox(12, current, spacer(), elapsed);
        progressDetails.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(current, Priority.ALWAYS);
        VBox progressCard = new VBox(12, progressRow, progressDetails);
        progressCard.getStyleClass().addAll("management-popup-card", "verification-progress-card");
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
        sources.setCellFactory(_ -> new SourceCell());
        VBox.setVgrow(sources, Priority.ALWAYS);
        StackPane sourceContent = new StackPane(sources, preparing);
        sourceContent.setAlignment(Pos.CENTER);
        sourceContent.setMinHeight(0);
        VBox.setVgrow(sourceContent, Priority.ALWAYS);
        VBox sourcesCard = new VBox(10, sourceHeading, message, sourceContent);
        sourcesCard.getStyleClass().addAll("management-popup-card", "verification-log-card", "epg-refresh-sources-card");
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
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("verification-progress-card-shell");
        shell.setPadding(new Insets(18));
        shell.setCenter(main);
        BorderPane.setMargin(footer, new Insets(14, 0, 0, 0));
        shell.setBottom(footer);
        shell.setMinSize(0, 0);

        BorderPane root = new BorderPane(shell);
        root.getStyleClass().addAll("management-popup-root", "verification-progress-root");
        root.setPadding(new Insets(18));
        Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
        RootApplication.applyThemeToPopupScene(scene);
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(540);

        Map<String, String> renderedStatuses = new HashMap<>();
        String[] renderedProgressPhase = {""};
        int[] renderedProgressTotal = {-1};
        int[] renderedPreparationCompleted = {0};
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
            phase.setText(snapshot.stage());
            boolean preparingAccounts = snapshot.total() < 0;
            if (preparingAccounts) {
                count.setText(snapshot.preparationTotal() > 0
                        ? I18n.tr("epgRefreshAccountsChecked", snapshot.preparationCompleted(), snapshot.preparationTotal())
                        : I18n.tr("epgRefreshPreparing"));
                boolean progressPhaseChanged = configureProgress(progress, renderedProgressPhase, renderedProgressTotal,
                        snapshot.preparationTotal() > 0 ? "accounts" : "waiting",
                        snapshot.preparationTotal() > 0 ? snapshot.preparationTotal() : 1);
                if (snapshot.preparationTotal() > 0) {
                    for (int i = renderedPreparationCompleted[0]; i < snapshot.preparationCompleted(); i++) {
                        progress.updateSegment(i, SegmentStatus.SUCCESS);
                    }
                    renderedPreparationCompleted[0] = snapshot.preparationCompleted();
                } else if (progressPhaseChanged) {
                    progress.updateSegment(0, SegmentStatus.WARNING);
                }
                current.setText(snapshot.currentAccount().isBlank() ? ""
                        : I18n.tr("epgRefreshCurrentAccount", snapshot.currentAccount()));
                preparing.setText(snapshot.currentAccount().isBlank()
                        ? snapshot.stage() : I18n.tr("epgRefreshCurrentAccount", snapshot.currentAccount()));
            } else {
                count.setText(I18n.tr("epgRefreshSourceCount", snapshot.completed(), snapshot.total()));
                configureProgress(progress, renderedProgressPhase, renderedProgressTotal, "sources", snapshot.total());
                for (int index = 0; index < snapshot.sources().size(); index++) {
                    EpgRefreshJobService.SourceProgress source = snapshot.sources().get(index);
                    String previous = renderedStatuses.put(source.id(), source.status());
                    if (!source.status().equals(previous)) {
                        if ("Completed".equals(source.status())) progress.updateSegment(index, SegmentStatus.SUCCESS);
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
            message.setText(snapshot.message());
            message.setVisible(!snapshot.message().isBlank());
            message.setManaged(!snapshot.message().isBlank());
            if (!sources.getItems().equals(snapshot.sources())) sources.getItems().setAll(snapshot.sources());
            boolean showSources = !preparingAccounts && !snapshot.sources().isEmpty();
            sources.setVisible(showSources);
            sources.setManaged(showSources);
            boolean showPreparing = !showSources;
            if (showPreparing && !preparingAccounts) preparing.setText(snapshot.stage());
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

    private static String formatElapsed(long millis) {
        long seconds = millis / 1_000;
        long hours = seconds / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remainder = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, remainder)
                : "%02d:%02d".formatted(minutes, remainder);
    }

    private static final class SourceCell extends ListCell<EpgRefreshJobService.SourceProgress> {
        @Override
        protected void updateItem(EpgRefreshJobService.SourceProgress item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label name = new Label(item.name());
            name.getStyleClass().add("epg-refresh-source-name");
            name.setMinWidth(230);
            name.setPrefWidth(300);
            name.setMaxWidth(Double.MAX_VALUE);
            name.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

            Label status = new Label(item.status());
            status.getStyleClass().add("epg-refresh-source-status");
            if ("Completed".equals(item.status())) status.getStyleClass().add("success");
            else if ("Failed".equals(item.status())) status.getStyleClass().add("failure");
            status.setMinWidth(125);
            status.setPrefWidth(150);

            Label detail = new Label(item.detail());
            detail.getStyleClass().add("management-popup-subtitle");
            detail.setWrapText(true);
            detail.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(14, name, status, detail);
            row.getStyleClass().add("epg-refresh-source-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setFillHeight(true);
            HBox.setHgrow(detail, Priority.ALWAYS);
            setGraphic(row);
            setText(null);
        }
    }
}
