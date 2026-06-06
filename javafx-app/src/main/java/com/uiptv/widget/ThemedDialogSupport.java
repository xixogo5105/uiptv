package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class ThemedDialogSupport {
    private static final String FOCUS_BRIDGE_KEY = ThemedDialogSupport.class.getName() + ".focusBridge";
    private static final double DEFAULT_DIALOG_WIDTH = 560;
    private static final double DEFAULT_CONTENT_WIDTH = 492;

    private ThemedDialogSupport() {
    }

    public static void prepare(Dialog<?> dialog, Window ownerWindow, String styleClass) {
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }
        dialog.getDialogPane().getStyleClass().add("uiptv-modal-dialog");
        if (styleClass != null && !styleClass.isBlank()) {
            dialog.getDialogPane().getStyleClass().add(styleClass);
        }
        decorateAlert(dialog);
        styleDialogButtons(dialog);
        String theme = RootApplication.getCurrentTheme();
        if (theme != null) {
            dialog.getDialogPane().getStylesheets().add(theme);
        }
        dialog.getDialogPane().setNodeOrientation(
                I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT
        );
        dialog.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        dialog.getDialogPane().setPrefWidth(DEFAULT_DIALOG_WIDTH);
        dialog.setOnShown(_ -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
            wrapDialogText(dialog);
            installFocusBridge(dialog, ownerWindow);
            focusDialogRepeatedly(dialog);
        });
        dialog.setOnHidden(_ -> uninstallFocusBridge(dialog));
    }

    public static void styleDialogButtons(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
            if (button == null) {
                continue;
            }
            ButtonBar.ButtonData data = buttonType.getButtonData();
            addStyleClass(button, data != null && data.isDefaultButton()
                    ? "uiptv-dialog-primary-button"
                    : "uiptv-dialog-secondary-button");
        }
    }

    private static void addStyleClass(Node node, String styleClass) {
        if (node != null && styleClass != null && !node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    private static void decorateAlert(Dialog<?> dialog) {
        if (!(dialog instanceof Alert alert)) {
            return;
        }
        Alert.AlertType alertType = alert.getAlertType();
        String styleClass = alertStyleClass(alertType);
        if (styleClass == null) {
            return;
        }
        dialog.getDialogPane().getStyleClass().add(styleClass);
        if (alert.getHeaderText() == null || alert.getHeaderText().isBlank()) {
            alert.setHeaderText(defaultAlertHeader(alertType));
        }
        alert.setGraphic(createAlertGraphic(alertType));
    }

    private static String alertStyleClass(Alert.AlertType alertType) {
        return switch (alertType) {
            case CONFIRMATION -> "uiptv-confirm-dialog";
            case ERROR -> "uiptv-error-dialog";
            case WARNING -> "uiptv-warning-dialog";
            default -> null;
        };
    }

    private static String defaultAlertHeader(Alert.AlertType alertType) {
        return switch (alertType) {
            case CONFIRMATION -> I18n.tr("commonConfirm");
            case ERROR -> I18n.tr("commonError");
            case WARNING -> I18n.tr("commonInfo");
            default -> "";
        };
    }

    private static StackPane createAlertGraphic(Alert.AlertType alertType) {
        Label glyph = new Label(alertType == Alert.AlertType.CONFIRMATION ? "?" : "!");
        glyph.getStyleClass().add("uiptv-dialog-glyph-text");
        StackPane shell = new StackPane(glyph);
        shell.getStyleClass().addAll("uiptv-dialog-glyph", alertStyleClass(alertType));
        shell.setMinSize(44, 44);
        shell.setPrefSize(44, 44);
        shell.setMaxSize(44, 44);
        return shell;
    }

    public static <T> Optional<T> showAndWait(Dialog<T> dialog, Window ownerWindow) {
        Runnable removeOwnerDimming = applyOwnerDimming(ownerWindow);
        try {
            Platform.runLater(() -> focusDialogRepeatedly(dialog));
            return dialog.showAndWait();
        } finally {
            removeOwnerDimming.run();
            uninstallFocusBridge(dialog);
        }
    }

    public static Optional<ButtonType> showChoice(String title,
                                                  Node content,
                                                  List<ButtonType> buttons,
                                                  ButtonType fallbackButton) {
        return showChoice(title, content, buttons, fallbackButton, null);
    }

    public static Optional<ButtonType> showChoice(String title,
                                                  Node content,
                                                  List<ButtonType> buttons,
                                                  ButtonType fallbackButton,
                                                  Consumer<Map<ButtonType, Button>> buttonConfigurer) {
        return showChoice(title, content, buttons, fallbackButton, buttonConfigurer, primaryOwnerWindow(), "uiptv-alert-dialog");
    }

    public static Optional<ButtonType> showChoice(String title,
                                                  Node content,
                                                  List<ButtonType> buttons,
                                                  ButtonType fallbackButton,
                                                  Consumer<Map<ButtonType, Button>> buttonConfigurer,
                                                  Window ownerWindow,
                                                  String styleClass) {
        ButtonType fallback = fallbackButton == null
                ? new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE)
                : fallbackButton;
        List<ButtonType> safeButtons = buttons == null || buttons.isEmpty()
                ? List.of(fallback)
                : buttons.stream().filter(Objects::nonNull).toList();
        if (safeButtons.isEmpty()) {
            safeButtons = List.of(fallback);
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(Objects.toString(title, ""));
        dialog.setHeaderText(Objects.toString(title, ""));
        Node safeContent = content == null ? new Label("") : content;
        dialog.getDialogPane().setContent(safeContent);
        dialog.getDialogPane().getButtonTypes().setAll(safeButtons);
        dialog.setResultConverter(buttonType -> buttonType == null ? fallback : buttonType);
        prepare(dialog, ownerWindow, styleClass);
        applyContentPreferredSize(dialog, safeContent);

        if (buttonConfigurer != null) {
            buttonConfigurer.accept(dialogButtons(dialog));
        }

        return showAndWait(dialog, ownerWindow).or(() -> Optional.of(fallback));
    }

    private static Map<ButtonType, Button> dialogButtons(Dialog<?> dialog) {
        Map<ButtonType, Button> renderedButtons = new LinkedHashMap<>();
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
            if (button != null) {
                renderedButtons.put(buttonType, button);
            }
        }
        return Map.copyOf(renderedButtons);
    }

    private static void applyContentPreferredSize(Dialog<?> dialog, Node content) {
        if (!(content instanceof Region region)) {
            return;
        }
        double prefWidth = region.getPrefWidth();
        if (prefWidth > 0 && prefWidth != Region.USE_COMPUTED_SIZE) {
            dialog.getDialogPane().setPrefWidth(Math.max(DEFAULT_DIALOG_WIDTH, prefWidth));
        }
        double prefHeight = region.getPrefHeight();
        if (prefHeight > 0 && prefHeight != Region.USE_COMPUTED_SIZE) {
            dialog.getDialogPane().setPrefHeight(prefHeight);
        }
    }

    public static Window primaryOwnerWindow() {
        return RootApplication.getPrimaryStage();
    }

    public static Window ownerWindowOf(javafx.scene.Node owner) {
        if (owner != null && owner.getScene() != null) {
            return owner.getScene().getWindow();
        }
        return primaryOwnerWindow();
    }

    private static Runnable applyOwnerDimming(Window ownerWindow) {
        if (ownerWindow == null || ownerWindow.getScene() == null || ownerWindow.getScene().getRoot() == null) {
            return () -> {
            };
        }
        Node ownerRoot = ownerWindow.getScene().getRoot();
        Effect previousEffect = ownerRoot.getEffect();
        ColorAdjust dimEffect = new ColorAdjust();
        dimEffect.setBrightness(-0.30);
        dimEffect.setSaturation(-0.18);
        ownerRoot.setEffect(dimEffect);
        return () -> ownerRoot.setEffect(previousEffect);
    }

    private static void installFocusBridge(Dialog<?> dialog, Window ownerWindow) {
        Scene scene = dialog.getDialogPane().getScene();
        if (scene == null || scene.getWindow() == null) {
            return;
        }
        Window dialogWindow = scene.getWindow();
        uninstallFocusBridge(dialog);

        ChangeListener<Boolean> ownerFocusListener = (_, _, focused) -> {
            if (Boolean.TRUE.equals(focused) && dialog.isShowing()) {
                focusDialogRepeatedly(dialog);
            }
            updateDialogAlwaysOnTop(dialog);
        };
        ChangeListener<Boolean> dialogFocusListener = (_, _, focused) -> {
            if (Boolean.FALSE.equals(focused) && dialog.isShowing() && isAnyApplicationWindowFocused()) {
                focusDialogRepeatedly(dialog);
            }
            updateDialogAlwaysOnTop(dialog);
        };

        if (ownerWindow != null) {
            ownerWindow.focusedProperty().addListener(ownerFocusListener);
        }
        dialogWindow.focusedProperty().addListener(dialogFocusListener);
        if (dialogWindow instanceof Stage stage) {
            stage.setAlwaysOnTop(true);
        }
        dialog.getDialogPane().getProperties().put(
                FOCUS_BRIDGE_KEY,
                new FocusBridge(ownerWindow, dialogWindow, ownerFocusListener, dialogFocusListener)
        );
        Platform.runLater(() -> updateDialogAlwaysOnTop(dialog));
    }

    private static void uninstallFocusBridge(Dialog<?> dialog) {
        Object bridge = dialog.getDialogPane().getProperties().remove(FOCUS_BRIDGE_KEY);
        if (bridge instanceof FocusBridge focusBridge) {
            focusBridge.uninstall();
        }
    }

    private static void updateDialogAlwaysOnTop(Dialog<?> dialog) {
        Scene scene = dialog.getDialogPane().getScene();
        if (scene == null || !(scene.getWindow() instanceof Stage stage)) {
            return;
        }
        stage.setAlwaysOnTop(dialog.isShowing() && isAnyApplicationWindowFocused());
    }

    private static boolean isAnyApplicationWindowFocused() {
        return Window.getWindows().stream().anyMatch(Window::isFocused);
    }

    private static void focusDialogRepeatedly(Dialog<?> dialog) {
        focusDialog(dialog);
        Platform.runLater(() -> {
            focusDialog(dialog);
            Platform.runLater(() -> focusDialog(dialog));
        });
    }

    private static void focusDialog(Dialog<?> dialog) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        Scene scene = dialog.getDialogPane().getScene();
        if (scene == null || scene.getWindow() == null) {
            return;
        }
        Window window = scene.getWindow();
        window.requestFocus();
        if (window instanceof Stage stage) {
            stage.toFront();
            stage.requestFocus();
        }
        Node focusTarget = findFocusTarget(dialog);
        if (focusTarget != null) {
            Platform.runLater(focusTarget::requestFocus);
        }
    }

    private static void wrapDialogText(Dialog<?> dialog) {
        wrapLabel(dialog.getDialogPane().lookup(".content.label"));
        wrapLabel(dialog.getDialogPane().lookup(".header-panel .label"));
    }

    private static void wrapLabel(Node node) {
        if (node instanceof Label label) {
            label.setWrapText(true);
            label.setMinWidth(0);
            label.setPrefWidth(DEFAULT_CONTENT_WIDTH);
            label.setMaxWidth(DEFAULT_CONTENT_WIDTH);
        }
    }

    private static Node findFocusTarget(Dialog<?> dialog) {
        Node textInput = dialog.getDialogPane().lookup(".text-input");
        if (isFocusable(textInput)) {
            return textInput;
        }
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
                if (isFocusable(button)) {
                    return button;
                }
            }
        }
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
            if (isFocusable(button)) {
                return button;
            }
        }
        return dialog.getDialogPane();
    }

    private static boolean isFocusable(Node node) {
        return node != null && node.isVisible() && !node.isDisabled();
    }

    private record FocusBridge(Window ownerWindow,
                               Window dialogWindow,
                               ChangeListener<Boolean> ownerFocusListener,
                               ChangeListener<Boolean> dialogFocusListener) {
        private void uninstall() {
            if (ownerWindow != null) {
                ownerWindow.focusedProperty().removeListener(ownerFocusListener);
            }
            dialogWindow.focusedProperty().removeListener(dialogFocusListener);
            if (dialogWindow instanceof Stage stage) {
                stage.setAlwaysOnTop(false);
            }
        }
    }
}
