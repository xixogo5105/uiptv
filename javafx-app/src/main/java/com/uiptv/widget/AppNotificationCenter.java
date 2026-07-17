package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppNotificationCenter {
    private static final Duration DISPLAY_DURATION = Duration.seconds(18);
    private static final Duration FADE_IN_DURATION = Duration.millis(180);
    private static final Duration FADE_OUT_DURATION = Duration.millis(260);
    private static final int MAX_VISIBLE_NOTIFICATIONS = 3;

    private static volatile VBox host;

    private AppNotificationCenter() {
    }

    public static VBox createHost() {
        VBox notificationHost = new VBox(8);
        notificationHost.getStyleClass().add("uiptv-notification-host");
        notificationHost.setFillWidth(true);
        notificationHost.setMaxWidth(Double.MAX_VALUE);
        notificationHost.setVisible(false);
        notificationHost.setManaged(false);
        return notificationHost;
    }

    public static void install(VBox notificationHost) {
        if (notificationHost == null) {
            return;
        }
        Runnable installer = () -> {
            host = notificationHost;
            updateHostVisibility(notificationHost);
        };
        if (Platform.isFxApplicationThread()) {
            installer.run();
        } else {
            Platform.runLater(installer);
        }
    }

    public static boolean showInfo(String message, Runnable onDismiss) {
        return show(NotificationType.INFO, message, onDismiss);
    }

    public static boolean showError(String message, Runnable onDismiss) {
        return show(NotificationType.ERROR, message, onDismiss);
    }

    private static boolean show(NotificationType type, String message, Runnable onDismiss) {
        VBox currentHost = host;
        if (currentHost == null) {
            return false;
        }
        Runnable renderer = () -> showNow(currentHost, type, message, onDismiss);
        if (Platform.isFxApplicationThread()) {
            renderer.run();
        } else {
            Platform.runLater(renderer);
        }
        return true;
    }

    private static void showNow(VBox currentHost, NotificationType type, String message, Runnable onDismiss) {
        if (currentHost.getScene() == null && host != currentHost) {
            runCleanup(onDismiss);
            return;
        }
        while (currentHost.getChildren().size() >= MAX_VISIBLE_NOTIFICATIONS) {
            Node oldest = currentHost.getChildren().getFirst();
            dismiss(oldest);
            currentHost.getChildren().remove(oldest);
        }
        updateHostVisibility(currentHost);

        StackPane card = createNotificationCard(type, message);
        currentHost.getChildren().add(card);
        updateHostVisibility(currentHost);

        AtomicBoolean dismissed = new AtomicBoolean(false);
        PauseTransition timer = new PauseTransition(DISPLAY_DURATION);
        Runnable dismissAction = () -> {
            if (!dismissed.compareAndSet(false, true)) {
                return;
            }
            timer.stop();
            fadeOutAndRemove(card, onDismiss);
        };
        card.getProperties().put(DismissAction.class, dismissAction);

        Button closeButton = (Button) card.getProperties().get(CloseButton.class);
        if (closeButton != null) {
            closeButton.setOnAction(event -> dismissAction.run());
        }

        card.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(FADE_IN_DURATION, card);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        timer.setOnFinished(event -> dismissAction.run());
        timer.play();
    }

    private static StackPane createNotificationCard(NotificationType type, String message) {
        Label icon = new Label(type.symbol);
        icon.getStyleClass().add("uiptv-notification-icon");

        StackPane iconShell = new StackPane(icon);
        iconShell.getStyleClass().addAll("uiptv-notification-icon-shell", type.styleClass);
        iconShell.setMinSize(34, 34);
        iconShell.setPrefSize(34, 34);
        iconShell.setMaxSize(34, 34);

        Label title = new Label(type.title());
        title.getStyleClass().add("uiptv-notification-title");

        Label body = new Label(Objects.toString(message, ""));
        body.getStyleClass().add("uiptv-notification-message");
        body.setWrapText(true);
        body.setMinWidth(0);
        body.setMaxWidth(Double.MAX_VALUE);

        VBox textBox = new VBox(2, title, body);
        textBox.setMinWidth(0);
        textBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Button closeButton = new Button("x");
        closeButton.getStyleClass().add("uiptv-notification-close");
        closeButton.setFocusTraversable(false);

        HBox content = new HBox(10, iconShell, textBox, closeButton);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);

        StackPane card = new StackPane(content);
        card.getStyleClass().addAll("uiptv-notification-card", type.styleClass);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getProperties().put(CloseButton.class, closeButton);
        return card;
    }

    private static void dismiss(Node node) {
        Object action = node == null ? null : node.getProperties().get(DismissAction.class);
        if (action instanceof Runnable runnable) {
            runnable.run();
        }
    }

    private static void fadeOutAndRemove(Node node, Runnable onDismiss) {
        FadeTransition fadeOut = new FadeTransition(FADE_OUT_DURATION, node);
        fadeOut.setFromValue(node.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            if (node.getParent() instanceof VBox parent) {
                parent.getChildren().remove(node);
                updateHostVisibility(parent);
            }
            runCleanup(onDismiss);
        });
        fadeOut.play();
    }

    private static void updateHostVisibility(VBox notificationHost) {
        boolean visible = notificationHost != null && !notificationHost.getChildren().isEmpty();
        if (notificationHost != null) {
            notificationHost.setVisible(visible);
            notificationHost.setManaged(visible);
        }
    }

    private static void runCleanup(Runnable cleanup) {
        if (cleanup != null) {
            cleanup.run();
        }
    }

    private enum NotificationType {
        INFO("i", "info"),
        ERROR("!", "error");

        private final String symbol;
        private final String styleClass;

        NotificationType(String symbol, String styleClass) {
            this.symbol = symbol;
            this.styleClass = styleClass;
        }

        private String title() {
            return switch (this) {
                case INFO -> I18n.tr("commonInfo");
                case ERROR -> I18n.tr("commonError");
            };
        }
    }

    private static final class DismissAction {
    }

    private static final class CloseButton {
    }
}
