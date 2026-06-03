package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Optional;

public final class ThemedDialogSupport {
    private static final String FOCUS_BRIDGE_KEY = ThemedDialogSupport.class.getName() + ".focusBridge";
    private static final double DEFAULT_DIALOG_WIDTH = 560;
    private static final double DEFAULT_CONTENT_WIDTH = 492;

    private ThemedDialogSupport() {
    }

    public static void prepare(Dialog<?> dialog, Window ownerWindow, String styleClass) {
        dialog.setHeaderText(null);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }
        dialog.getDialogPane().getStyleClass().add("uiptv-modal-dialog");
        if (styleClass != null && !styleClass.isBlank()) {
            dialog.getDialogPane().getStyleClass().add(styleClass);
        }
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
