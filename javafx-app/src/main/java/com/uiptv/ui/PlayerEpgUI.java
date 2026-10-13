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
    private final AtomicInteger generation = new AtomicInteger();
    private final ChangeListener<MediaPlayerFactory.GuideContext> listener = (_, _, context) -> refresh(context, true);
    private final javafx.animation.Timeline clock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(1),
            _ -> refresh(MediaPlayerFactory.guideContextProperty().get(), false)));

    public PlayerEpgUI() {
        getStyleClass().add("player-epg-panel");
        setSpacing(8); setMinWidth(0); setMaxWidth(Double.MAX_VALUE);
        VBox details = new VBox(6, heading, nowTitle, nowTime, nowDescription, new Separator(), nextTitle, nextTime, nextDescription);
        for (Label label : new Label[]{heading, nowTitle, nowTime, nowDescription, nextTitle, nextTime, nextDescription}) { label.setWrapText(true); label.setMaxWidth(Double.MAX_VALUE); }
        heading.getStyleClass().add("strong-label"); nowTitle.getStyleClass().add("strong-label"); nextTitle.getStyleClass().add("strong-label");
        ScrollPane scroll = new ScrollPane(details); scroll.setFitToWidth(true); scroll.setMaxHeight(360); scroll.setPrefHeight(280);
        getChildren().add(scroll); setVisible(false); setManaged(false);
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
        heading.setText(result.channelName() + " · " + I18n.tr("epgNow") + " & " + I18n.tr("epgNext"));
        programme(result.now(), I18n.tr("epgNow"), nowTitle, nowTime, nowDescription);
        programme(result.next(), I18n.tr("epgNext"), nextTitle, nextTime, nextDescription);
    }

    private void programme(EpgProgramme programme, String prefix, Label title, Label time, Label description) {
        title.setText(programme == null ? "" : prefix + ": " + programme.title());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());
        time.setText(programme == null ? "" : format.format(Instant.ofEpochMilli(programme.start())) + " – " + format.format(Instant.ofEpochMilli(programme.stop())));
        description.setText(programme == null ? "" : programme.description());
        for (Label label : new Label[]{title, time, description}) { label.setVisible(!label.getText().isBlank()); label.setManaged(label.isVisible()); }
    }
}
