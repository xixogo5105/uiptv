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

/** Adjacent to the video, never over its heavyweight native surface. */
public class PlayerEpgUI extends VBox {
    private final Label heading = new Label(), nowTitle = new Label(), nowTime = new Label(), nowDescription = new Label(),
            nextTitle = new Label(), nextTime = new Label(), nextDescription = new Label();
    private final FlowPane cards = new FlowPane(10, 10);
    private final VBox nowCard = new VBox(6), nextCard = new VBox(6);
    private final ProgressBar progress = new ProgressBar();
    private final Hyperlink nowDetails = new Hyperlink(I18n.tr("epgProgrammeDetails")), nextDetails = new Hyperlink(I18n.tr("epgProgrammeDetails"));
    private boolean compact;
    private PlayerEpgApplicationService.Result displayed;
    private final AtomicInteger generation = new AtomicInteger();
    private final ChangeListener<MediaPlayerFactory.GuideContext> listener = (_, _, context) -> refresh(context, true);
    private final javafx.animation.Timeline clock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(1),
            _ -> refresh(MediaPlayerFactory.guideContextProperty().get(), false)));

    public PlayerEpgUI() {
        getStyleClass().add("player-epg-panel");
        setSpacing(10); setMinWidth(0); setMinHeight(Region.USE_PREF_SIZE); setMaxWidth(Double.MAX_VALUE);
        for (Label label : new Label[]{heading, nowTitle, nowTime, nowDescription, nextTitle, nextTime, nextDescription}) {
            label.setWrapText(true); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE);
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        heading.getStyleClass().add("epg-player-heading");
        nowTitle.getStyleClass().add("epg-programme-title"); nextTitle.getStyleClass().add("epg-programme-title");
        nowDescription.getStyleClass().add("epg-programme-description"); nextDescription.getStyleClass().add("epg-programme-description");
        nowTime.getStyleClass().add("epg-programme-time"); nextTime.getStyleClass().add("epg-programme-time");
        Label nowBadge = new Label(I18n.tr("epgNow")), nextBadge = new Label(I18n.tr("epgNext"));
        nowBadge.getStyleClass().addAll("epg-programme-badge", "epg-now-badge"); nextBadge.getStyleClass().add("epg-programme-badge");
        progress.getStyleClass().add("epg-programme-progress"); progress.setMaxWidth(Double.MAX_VALUE);
        nowCard.getStyleClass().addAll("epg-programme-card", "epg-current-card"); nextCard.getStyleClass().add("epg-programme-card");
        nowCard.getChildren().addAll(nowBadge, nowTitle, nowTime, progress, nowDescription, nowDetails);
        nextCard.getChildren().addAll(nextBadge, nextTitle, nextTime, nextDescription, nextDetails);
        nowDetails.setOnAction(_ -> showDetails(displayed == null ? null : displayed.now()));
        nextDetails.setOnAction(_ -> showDetails(displayed == null ? null : displayed.next()));
        cards.getChildren().addAll(nowCard, nextCard); cards.setMinWidth(0);
        nowCard.setPrefWidth(300); nextCard.setPrefWidth(300);
        cards.prefWrapLengthProperty().bind(javafx.beans.binding.Bindings.max(300, widthProperty().subtract(24)));
        widthProperty().addListener((_, _, _) -> sizeCards());
        getChildren().addAll(heading, cards); setVisible(false); setManaged(false);
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) { MediaPlayerFactory.guideContextProperty().removeListener(listener); clock.stop(); generation.incrementAndGet(); }
            if (newScene != null) { MediaPlayerFactory.guideContextProperty().addListener(listener); refresh(MediaPlayerFactory.guideContextProperty().get(), true); clock.play(); }
        });
    }

    private void refresh(MediaPlayerFactory.GuideContext context, boolean clear) {
        int request = generation.incrementAndGet();
        if (clear) { setVisible(false); setManaged(false); }
        if (context == null || context.account() == null || context.account().action() != com.uiptv.model.Account.AccountAction.itv) return;
        Thread.ofVirtual().start(() -> {
            PlayerEpgApplicationService.Result result;
            try { result = new PlayerEpgApplicationService().current(context.account().toAccount(), context.channel()); }
            catch (Exception e) { return; }
            Platform.runLater(() -> { if (request == generation.get()) display(result); });
        });
    }

    void display(PlayerEpgApplicationService.Result result) {
        boolean available = result != null && (result.now() != null || result.next() != null);
        setVisible(available); setManaged(available);
        if (!available) return;
        displayed = result;
        heading.setText(result.channelName());
        nowCard.setVisible(result.now() != null); nowCard.setManaged(result.now() != null);
        nextCard.setVisible(result.next() != null); nextCard.setManaged(result.next() != null);
        programme(result.now(), I18n.tr("epgNow"), nowTitle, nowTime, nowDescription);
        programme(result.next(), I18n.tr("epgNext"), nextTitle, nextTime, nextDescription);
        if (result.now() != null) progress.setProgress(Math.clamp((System.currentTimeMillis() - result.now().start()) / (double) (result.now().stop() - result.now().start()), 0, 1));
        updateDescriptions(); sizeCards();
    }

    private void programme(EpgProgramme programme, String prefix, Label title, Label time, Label description) {
        title.setText(programme == null ? "" : programme.title());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());
        time.setText(programme == null ? "" : format.format(Instant.ofEpochMilli(programme.start())) + " – " + format.format(Instant.ofEpochMilli(programme.stop())));
        description.setText(programme == null ? "" : programme.description());
        for (Label label : new Label[]{title, time, description}) { label.setVisible(!label.getText().isBlank()); label.setManaged(label.isVisible()); }
    }
    public void setCompact(boolean compact) {
        this.compact = compact;
        getStyleClass().remove("compact"); if (compact) getStyleClass().add("compact");
        updateDescriptions(); sizeCards();
    }

    private void sizeCards() {
        double available = Math.max(0, getWidth() - 24);
        if (available == 0) return;
        boolean paired = available >= (compact ? 300 : 680) && nowCard.isManaged() && nextCard.isManaged();
        double width = paired ? (available - 10) / 2 : available;
        for (VBox card : new VBox[]{nowCard, nextCard}) { card.setMinWidth(0); card.setPrefWidth(width); card.setMaxWidth(width); }
    }

    private void updateDescriptions() {
        for (Label description : new Label[]{nowDescription, nextDescription}) {
            boolean visible = !compact && !description.getText().isBlank() && description.getText().length() <= 220;
            description.setVisible(visible); description.setManaged(visible);
        }
        for (Hyperlink link : new Hyperlink[]{nowDetails, nextDetails}) { link.setVisible(true); link.setManaged(true); }
        for (Label title : new Label[]{nowTitle, nextTitle}) {
            Tooltip tooltip = new Tooltip(title.getText()); tooltip.setWrapText(true); tooltip.setMaxWidth(460); title.setTooltip(tooltip);
        }
    }

    private void showDetails(EpgProgramme programme) {
        if (programme == null) return;
        Alert details = new Alert(Alert.AlertType.INFORMATION);
        if (getScene() != null) details.initOwner(getScene().getWindow());
        details.setTitle(I18n.tr("epgProgrammeDetails"));
        Label title = new Label(programme.title()); title.setWrapText(true); title.getStyleClass().add("epg-programme-title");
        details.getDialogPane().setHeader(title);
        Label description = new Label(programme.description()); description.setWrapText(true); description.setMaxWidth(520);
        ScrollPane body = new ScrollPane(description); body.setFitToWidth(true); body.setPrefViewportHeight(300);
        details.getDialogPane().setContent(body); details.getDialogPane().setPrefWidth(560);
        if (getScene() != null) details.getDialogPane().getStylesheets().setAll(getScene().getStylesheets());
        details.show();
    }

}
