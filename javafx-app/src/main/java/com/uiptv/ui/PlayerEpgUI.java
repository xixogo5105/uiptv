package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Objects;

/** Adjacent to the video, never over its heavyweight native surface. */
public class PlayerEpgUI extends VBox {
    private static final String PROGRAMME_TITLE_STYLE = "epg-programme-title";
    private static final String PROGRAMME_DESCRIPTION_STYLE = "epg-programme-description";
    private static final String PROGRAMME_CARD_STYLE = "epg-programme-card";
    private static final String PROGRAMME_TIME_STYLE = "epg-programme-time";
    private final Label heading = new Label();
    private final Label sourceStatus = new Label();
    private final Label nowTitle = new Label();
    private final Label nowTime = new Label();
    private final Label nowDescription = new Label();
    private final Label nextTitle = new Label();
    private final Label nextTime = new Label();
    private final Label nextDescription = new Label();
    private final FlowPane cards = new FlowPane(10, 10);
    private final VBox nowCard = new VBox(6);
    private final VBox nextCard = new VBox(6);
    private final ProgressBar progress = new ProgressBar();
    private final Hyperlink nowDetails = new Hyperlink(I18n.tr("epgProgrammeDetails"));
    private final Hyperlink nextDetails = new Hyperlink(I18n.tr("epgProgrammeDetails"));
    private final VBox expandedDetails = new VBox(8);
    private final Label expandedTitle = new Label();
    private final Label expandedDescription = new Label();
    private boolean compact;
    private PlayerEpgApplicationService.Result displayed;
    private Thread guideWorker;
    private final AtomicInteger generation = new AtomicInteger();
    private javafx.animation.Timeline programmeEndTimer;
    private final ChangeListener<MediaPlayerFactory.GuideContext> listener = (_, _, context) -> refresh(context);
    private final javafx.animation.Timeline clock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(1),
            _ -> updateProgress()));

    public PlayerEpgUI() {
        getStyleClass().add("player-epg-panel");
        setSpacing(10); setMinWidth(0); setMinHeight(Region.USE_PREF_SIZE); setMaxWidth(Double.MAX_VALUE);
        for (Label label : new Label[]{heading, nowTitle, nowTime, nowDescription, nextTitle, nextTime, nextDescription}) {
            label.setWrapText(true); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE);
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        heading.getStyleClass().add("epg-player-heading");
        sourceStatus.getStyleClass().add(PROGRAMME_TIME_STYLE);
        nowTitle.getStyleClass().add(PROGRAMME_TITLE_STYLE); nextTitle.getStyleClass().add(PROGRAMME_TITLE_STYLE);
        nowDescription.getStyleClass().add(PROGRAMME_DESCRIPTION_STYLE); nextDescription.getStyleClass().add(PROGRAMME_DESCRIPTION_STYLE);
        nowTime.getStyleClass().add(PROGRAMME_TIME_STYLE); nextTime.getStyleClass().add(PROGRAMME_TIME_STYLE);
        Label nowBadge = new Label(I18n.tr("epgNow"));
        Label nextBadge = new Label(I18n.tr("epgNext"));
        nowBadge.getStyleClass().addAll("epg-programme-badge", "epg-now-badge"); nextBadge.getStyleClass().add("epg-programme-badge");
        progress.getStyleClass().add("epg-programme-progress"); progress.setMaxWidth(Double.MAX_VALUE);
        nowCard.getStyleClass().addAll(PROGRAMME_CARD_STYLE, "epg-current-card"); nextCard.getStyleClass().add(PROGRAMME_CARD_STYLE);
        nowCard.getChildren().addAll(nowBadge, nowTitle, nowTime, progress, nowDescription, nowDetails);
        nextCard.getChildren().addAll(nextBadge, nextTitle, nextTime, nextDescription, nextDetails);
        nowDetails.setOnAction(_ -> showDetails(displayed == null ? null : displayed.now()));
        nextDetails.setOnAction(_ -> showDetails(displayed == null ? null : displayed.next()));
        cards.getChildren().addAll(nowCard, nextCard); cards.setMinWidth(0);
        nowCard.setPrefWidth(300); nextCard.setPrefWidth(300);
        cards.prefWrapLengthProperty().bind(javafx.beans.binding.Bindings.max(300, widthProperty()));
        widthProperty().addListener((_, _, _) -> sizeCards());
        expandedTitle.setMinWidth(0); expandedTitle.setWrapText(true); expandedTitle.setMaxWidth(Double.MAX_VALUE); expandedTitle.getStyleClass().add(PROGRAMME_TITLE_STYLE);
        expandedDescription.setMinWidth(0); expandedDescription.setWrapText(true); expandedDescription.setMaxWidth(Double.MAX_VALUE); expandedDescription.getStyleClass().add(PROGRAMME_DESCRIPTION_STYLE);
        var close = new com.uiptv.widget.CloseIconButton(I18n.tr("autoClose")); close.setOnAction(_ -> closeDetails());
        HBox detailsHeader = new HBox(8, expandedTitle, close); HBox.setHgrow(expandedTitle, Priority.ALWAYS);
        expandedDetails.getStyleClass().addAll(PROGRAMME_CARD_STYLE, "epg-inline-details");
        expandedDetails.getChildren().add(detailsHeader); closeDetails();
        getChildren().addAll(heading, sourceStatus, cards, expandedDetails); setVisible(false); setManaged(false);
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) {
                MediaPlayerFactory.guideContextProperty().removeListener(listener);
                generation.incrementAndGet();
                if (guideWorker != null) { guideWorker.interrupt(); guideWorker = null; }
                cancelProgrammeEndTimer();
                clock.stop(); display(null);
            }
            if (newScene != null) {
                MediaPlayerFactory.guideContextProperty().addListener(listener);
                clock.play();
            }
        });
        EpgModeUI.observe(this, () -> refresh(MediaPlayerFactory.guideContextProperty().get()));
    }

    private void refresh(MediaPlayerFactory.GuideContext context) {
        int request = generation.incrementAndGet();
        cancelProgrammeEndTimer();
        if (guideWorker != null) { guideWorker.interrupt(); guideWorker = null; }
        MediaPlayerFactory.publishGuideSummary(null); display(null);
        if (!EpgModeUI.isFeatureEnabled() || context == null || context.account() == null || context.channel() == null
                || context.account().action() != com.uiptv.model.Account.AccountAction.itv) return;
        guideWorker = Thread.ofVirtual().start(() -> {
            PlayerEpgApplicationService.Result result;
            try { result = new PlayerEpgApplicationService().currentForPlayback(context.account().toAccount(), context.channel()); }
            catch (Exception _) { result = null; }
            var loaded = result;
            Platform.runLater(() -> {
                if (request != generation.get()) return;
                guideWorker = null;
                MediaPlayerFactory.publishGuideSummary(loaded); display(loaded);
                scheduleProgrammeEndRefresh(context, loaded);
            });
        });
    }

    private void scheduleProgrammeEndRefresh(MediaPlayerFactory.GuideContext context,
                                             PlayerEpgApplicationService.Result result) {
        EpgProgramme current = result == null ? null : result.now();
        if (context == null || current == null || current.stop() <= System.currentTimeMillis()) return;
        long delay = current.stop() - System.currentTimeMillis();
        programmeEndTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(Math.max(1, delay)), _ -> {
                    programmeEndTimer = null;
                    if (Objects.equals(context, MediaPlayerFactory.guideContextProperty().get())) refresh(context);
                }));
        programmeEndTimer.setCycleCount(1);
        programmeEndTimer.play();
    }

    private void cancelProgrammeEndTimer() {
        if (programmeEndTimer != null) {
            programmeEndTimer.stop();
            programmeEndTimer = null;
        }
    }

    private void updateProgress() {
        var programme = displayed == null ? null : displayed.now();
        progress.setProgress(programme == null || programme.stop() <= programme.start() ? 0
                : Math.clamp((System.currentTimeMillis() - programme.start()) / (double) (programme.stop() - programme.start()), 0, 1));
    }

    void display(PlayerEpgApplicationService.Result result) {
        if (Objects.equals(displayed, result)) { updateProgress(); return; }
        boolean hasGuide = result != null && (result.now() != null || result.next() != null);
        // A channel without a current or upcoming programme has no adjacent
        // player content to show. In particular, do not leave its channel name
        // or an empty panel below the video for NO_MATCH/UNAVAILABLE states.
        boolean available = hasGuide;
        setVisible(available); setManaged(available);
        if (!available) {

            displayed = null; heading.setText("");
            sourceStatus.setText(""); sourceStatus.setManaged(false); sourceStatus.setVisible(false);
            for (Label label : new Label[]{nowTitle, nowTime, nowDescription, nextTitle, nextTime, nextDescription}) label.setText("");
            progress.setProgress(0); closeDetails(); return;
        }
        if (displayed != null && !displayed.equals(result)) closeDetails();
        displayed = result;
        heading.setText(result.channelName());
        String statusText = switch (Objects.toString(result.guideStatus(), "")) {
            case "STALE" -> I18n.tr("epgSourceStale");
            case "UNAVAILABLE" -> I18n.tr("epgSourceUnavailable");
            // Keep the player detail area clean when a channel simply has no matching guide entry.
            case "NO_MATCH" -> "";
            default -> "";
        };
        sourceStatus.setText(statusText);
        sourceStatus.setManaged(!statusText.isBlank()); sourceStatus.setVisible(!statusText.isBlank());
        nowCard.setVisible(result.now() != null); nowCard.setManaged(result.now() != null);
        nextCard.setVisible(result.next() != null); nextCard.setManaged(result.next() != null);
        programme(result.now(), nowTitle, nowTime, nowDescription);
        programme(result.next(), nextTitle, nextTime, nextDescription);
        updateProgress();
        updateDescriptions(); sizeCards();
    }

    private void programme(EpgProgramme programme, Label title, Label time, Label description) {
        title.setText(programme == null ? "" : programme.title());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());
        time.setText(programme == null ? "" : format.format(Instant.ofEpochMilli(programme.start())) + " – " + format.format(Instant.ofEpochMilli(programme.stop())));
        description.setText(programme == null ? "" : programme.description());
        for (Label label : new Label[]{title, time, description}) { label.setVisible(!label.getText().isBlank()); label.setManaged(label.isVisible()); }
    }
    public void setCompact(boolean compact) {
        if (this.compact == compact) return;
        this.compact = compact;
        getStyleClass().remove("compact"); if (compact) getStyleClass().add("compact");
        updateDescriptions(); sizeCards();
    }

    private void sizeCards() {
        double available = Math.max(0, getWidth());
        if (available == 0) return;
        boolean paired = available >= (compact ? 300 : 680) && nowCard.isManaged() && nextCard.isManaged();
        double width = paired ? (available - 10) / 2 : available;
        for (VBox card : new VBox[]{nowCard, nextCard}) { card.setMinWidth(0); card.setPrefWidth(width); card.setMaxWidth(width); }
    }

    private void updateDescriptions() {
        for (Label description : new Label[]{nowDescription, nextDescription}) {
            boolean visible = !description.getText().isBlank();
            description.setVisible(visible); description.setManaged(visible);
        }
        updateDetailsLink(nowDetails, nowDescription);
        updateDetailsLink(nextDetails, nextDescription);
        for (Label title : new Label[]{nowTitle, nextTitle}) {
            Tooltip tooltip = new Tooltip(title.getText()); tooltip.setWrapText(true); tooltip.setMaxWidth(460); title.setTooltip(tooltip);
        }
    }

    private void updateDetailsLink(Hyperlink link, Label description) {
        boolean hasMore = !description.getText().isBlank() && !description.isManaged()
                && !(expandedDetails.isManaged() && description.getText().equals(expandedDescription.getText()));
        link.setVisible(hasMore); link.setManaged(hasMore);
    }

    private void closeDetails() {
        expandedDetails.setVisible(false); expandedDetails.setManaged(false);
        expandedTitle.setText(""); expandedDescription.setText("");
        if (expandedDetails.getChildren().size() > 1) expandedDetails.getChildren().remove(1, expandedDetails.getChildren().size());
        updateDescriptions();
    }

    private void showDetails(EpgProgramme programme) {
        if (programme == null || programme.description().isBlank()) return;
        expandedTitle.setText(programme.title()); expandedDescription.setText(programme.description());
        if (expandedDetails.getChildren().size() == 1) {
            ScrollPane body = new ScrollPane(expandedDescription); body.setFitToWidth(true); body.setPrefViewportHeight(180); body.setMaxHeight(220);
            body.getStyleClass().add("epg-inline-details-scroll"); expandedDetails.getChildren().add(body);
        }
        expandedDetails.setVisible(true); expandedDetails.setManaged(true);
        updateDescriptions();
    }
}
