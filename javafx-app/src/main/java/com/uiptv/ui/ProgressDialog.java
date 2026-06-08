package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressDialog extends Stage {
    private static final double SCENE_WIDTH = 1180;
    private static final double SCENE_HEIGHT = 720;

    private final ProgressInline progressInline = new ProgressInline();

    public ProgressDialog(Stage owner) {
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }
        setTitle(I18n.tr("autoVerifyingMacAddresses"));
        progressInline.setExternalCloseHandler(this::close);

        Scene scene = new Scene(progressInline, SCENE_WIDTH, SCENE_HEIGHT);
        UiI18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        setScene(scene);
        setOnCloseRequest(event -> {
            event.consume();
            progressInline.requestClose();
        });
    }

    public long getSelectedDelayMillis() {
        return progressInline.getSelectedDelayMillis();
    }

    public void setDefaultMacAddress(String defaultMacAddress) {
        progressInline.setDefaultMacAddress(defaultMacAddress);
    }

    public void setTotal(int total) {
        progressInline.setTotal(total);
    }

    public void addResult(boolean isValid) {
        progressInline.addResult(isValid);
    }

    public void addVerificationHeader(String mac, int index, int total) {
        progressInline.addVerificationHeader(mac, index, total);
    }

    public void addProgressText(String text) {
        progressInline.addProgressText(text);
    }

    public void setPauseStatus(int secondsRemaining, int totalSeconds) {
        progressInline.setPauseStatus(secondsRemaining, totalSeconds);
    }

    public void setOnClose(Runnable action) {
        progressInline.setOnClose(action);
    }

    public void setOnStop(Runnable action) {
        progressInline.setOnStop(action);
    }

    public void markCompleted() {
        progressInline.markCompleted();
    }
}
