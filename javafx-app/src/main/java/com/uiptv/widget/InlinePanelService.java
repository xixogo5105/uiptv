package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class InlinePanelService {
    public static final String FILL_HEIGHT_STYLE_CLASS = "uiptv-inline-fill-height";

    private static final ArrayDeque<List<Node>> viewStack = new ArrayDeque<>();
    private static StackPane host;

    private InlinePanelService() {
    }

    public static StackPane createHost(Node initialContent) {
        StackPane inlineHost = new StackPane();
        inlineHost.getStyleClass().add("uiptv-inline-host");
        inlineHost.setMinSize(0, 0);
        inlineHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (initialContent != null) {
            inlineHost.getChildren().setAll(initialContent);
        }
        return inlineHost;
    }

    public static void install(StackPane inlineHost) {
        if (inlineHost == null) {
            return;
        }
        Runnable installer = () -> {
            host = inlineHost;
            viewStack.clear();
        };
        if (Platform.isFxApplicationThread()) {
            installer.run();
        } else {
            Platform.runLater(installer);
        }
    }

    public static boolean hasOpenPanel() {
        return !viewStack.isEmpty();
    }

    public static Optional<InlinePanelHandle> open(String title, Node content) {
        return open(title, content, I18n.tr("commonClose"), null);
    }

    public static Optional<InlinePanelHandle> open(String title, Node content, String closeCaption, Runnable onClose) {
        return open(title, content, closeCaption, onClose, null);
    }

    public static Optional<InlinePanelHandle> open(String title,
                                                   Node content,
                                                   String closeCaption,
                                                   Runnable onClose,
                                                   Consumer<InlinePanelHandle> closeRequestHandler) {
        if (host == null || content == null) {
            return Optional.empty();
        }
        if (!Platform.isFxApplicationThread()) {
            AtomicReference<Optional<InlinePanelHandle>> result = new AtomicReference<>(Optional.empty());
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                result.set(open(title, content, closeCaption, onClose, closeRequestHandler));
                latch.countDown();
            });
            awaitLatch(latch);
            return result.get();
        }
        return Optional.of(openNow(title, content, closeCaption, onClose, closeRequestHandler));
    }

    public static Optional<ButtonType> showConfirmation(String title,
                                                       String message,
                                                       ButtonType primaryButton,
                                                       ButtonType secondaryButton) {
        ButtonType primary = primaryButton == null
                ? new ButtonType(I18n.tr("commonOk"), ButtonBar.ButtonData.OK_DONE)
                : primaryButton;
        ButtonType secondary = secondaryButton == null
                ? new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE)
                : secondaryButton;
        return showChoice(title, message, List.of(secondary, primary), secondary);
    }

    public static Optional<ButtonType> showChoice(String title,
                                                  String message,
                                                  List<ButtonType> buttons,
                                                  ButtonType fallbackButton) {
        Label messageLabel = new Label(Objects.toString(message, ""));
        messageLabel.getStyleClass().add("uiptv-inline-confirm-message");
        messageLabel.setWrapText(true);
        messageLabel.setMinWidth(0);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        return showChoice(title, messageLabel, buttons, fallbackButton);
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
        if (host == null) {
            return Optional.empty();
        }
        ButtonType fallback = fallbackButton == null
                ? new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE)
                : fallbackButton;
        List<ButtonType> normalizedButtons = buttons == null || buttons.isEmpty()
                ? List.of(fallback)
                : buttons.stream().filter(Objects::nonNull).toList();
        final List<ButtonType> safeButtons = normalizedButtons.isEmpty() ? List.of(fallback) : normalizedButtons;
        if (!Platform.isFxApplicationThread()) {
            AtomicReference<Optional<ButtonType>> result = new AtomicReference<>(Optional.of(fallback));
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                result.set(showChoice(title, content, safeButtons, fallback, buttonConfigurer));
                latch.countDown();
            });
            awaitLatch(latch);
            return result.get();
        }
        return Optional.ofNullable(showChoiceNow(title, content, safeButtons, fallback, buttonConfigurer));
    }

    private static InlinePanelHandle openNow(String title,
                                             Node content,
                                             String closeCaption,
                                             Runnable onClose,
                                             Consumer<InlinePanelHandle> closeRequestHandler) {
        detachFromParent(content);
        viewStack.push(new ArrayList<>(host.getChildren()));

        AtomicBoolean closed = new AtomicBoolean(false);
        InlinePanelHandle handle = new InlinePanelHandle(() -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            restorePreviousView();
            if (onClose != null) {
                onClose.run();
            }
        });
        Runnable closeAction = closeRequestHandler == null
                ? handle::close
                : () -> closeRequestHandler.accept(handle);
        host.getChildren().setAll(createInlineFrame(title, content, closeCaption, closeAction));
        return handle;
    }

    private static ButtonType showChoiceNow(String title,
                                            Node content,
                                            List<ButtonType> buttons,
                                            ButtonType fallbackButton,
                                            Consumer<Map<ButtonType, Button>> buttonConfigurer) {
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<ButtonType> selected = new AtomicReference<>(fallbackButton);
        AtomicReference<InlinePanelHandle> handleRef = new AtomicReference<>();
        Object loopKey = new Object();

        Node safeContent = content == null ? new Label("") : content;

        HBox actions = new HBox(10);
        actions.getStyleClass().add("uiptv-inline-confirm-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(16, safeContent, actions);
        card.getStyleClass().add("uiptv-inline-confirm-card");
        card.setMaxWidth(720);

        Map<ButtonType, Button> renderedButtons = new LinkedHashMap<>();
        for (ButtonType buttonType : buttons) {
            Button button = createConfirmButton(buttonType, confirmButtonStyle(buttonType));
            button.setOnAction(event -> completeInlineConfirmation(finished, selected, buttonType, handleRef.get(), loopKey));
            renderedButtons.put(buttonType, button);
            actions.getChildren().add(button);
        }
        if (buttonConfigurer != null) {
            buttonConfigurer.accept(Map.copyOf(renderedButtons));
        }

        Optional<InlinePanelHandle> handle = open(title, card, I18n.tr("commonClose"), () -> {
            if (finished.compareAndSet(false, true)) {
                selected.set(fallbackButton);
                Platform.exitNestedEventLoop(loopKey, fallbackButton);
            }
        });
        if (handle.isEmpty()) {
            return null;
        }
        handleRef.set(handle.get());

        Object nestedResult = Platform.enterNestedEventLoop(loopKey);
        if (nestedResult instanceof ButtonType buttonType) {
            return buttonType;
        }
        return selected.get();
    }

    private static void completeInlineConfirmation(AtomicBoolean finished,
                                                   AtomicReference<ButtonType> selected,
                                                   ButtonType result,
                                                   InlinePanelHandle handle,
                                                   Object loopKey) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        selected.set(result);
        if (handle != null) {
            handle.close();
        }
        Platform.exitNestedEventLoop(loopKey, result);
    }

    private static Button createConfirmButton(ButtonType buttonType, String styleClass) {
        Button button = new Button(buttonText(buttonType));
        button.getStyleClass().add(styleClass);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private static String confirmButtonStyle(ButtonType buttonType) {
        ButtonBar.ButtonData data = buttonType == null ? null : buttonType.getButtonData();
        return data != null && data.isDefaultButton()
                ? "uiptv-inline-primary-button"
                : "uiptv-inline-secondary-button";
    }

    private static String buttonText(ButtonType buttonType) {
        if (buttonType == null || buttonType.getText() == null || buttonType.getText().isBlank()) {
            return I18n.tr("commonClose");
        }
        return buttonType.getText();
    }

    private static VBox createInlineFrame(String title, Node content, String closeCaption, Runnable closeAction) {
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("uiptv-inline-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button closeButton = new Button(closeCaption == null || closeCaption.isBlank()
                ? I18n.tr("commonClose")
                : closeCaption);
        closeButton.getStyleClass().add("uiptv-inline-close-button");
        closeButton.setOnAction(event -> closeAction.run());

        HBox header = new HBox(12, titleLabel, closeButton);
        header.getStyleClass().add("uiptv-inline-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinWidth(0);
        header.setMaxWidth(Double.MAX_VALUE);

        boolean fillHeight = content.getStyleClass().contains(FILL_HEIGHT_STYLE_CLASS);
        if (fillHeight && content instanceof Region region) {
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        Node scrollContent = fillHeight ? content : createCenteredContentShell(content);
        ScrollPane scroller = new ScrollPane(scrollContent);
        scroller.getStyleClass().addAll("transparent-scroll-pane", "uiptv-inline-scroll-pane");
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(fillHeight);
        scroller.setMinSize(0, 0);
        scroller.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox frame = new VBox(14, header, scroller);
        frame.getStyleClass().add("uiptv-inline-panel");
        frame.setPadding(new Insets(16));
        frame.setMinSize(0, 0);
        frame.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return frame;
    }

    private static StackPane createCenteredContentShell(Node content) {
        StackPane shell = new StackPane(content);
        shell.getStyleClass().add("uiptv-inline-content-shell");
        shell.setAlignment(Pos.TOP_CENTER);
        shell.setMinSize(0, 0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (content instanceof Region region) {
            region.setMinWidth(0);
        }
        StackPane.setAlignment(content, Pos.TOP_CENTER);
        return shell;
    }

    private static void restorePreviousView() {
        if (host == null || viewStack.isEmpty()) {
            return;
        }
        host.getChildren().setAll(viewStack.pop());
    }

    private static void detachFromParent(Node node) {
        if (node == null || node.getParent() == null) {
            return;
        }
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        } else if (node.getParent() instanceof ScrollPane scrollPane && scrollPane.getContent() == node) {
            scrollPane.setContent(null);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class InlinePanelHandle {
        private final Runnable closeAction;

        private InlinePanelHandle(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        public void close() {
            closeAction.run();
        }
    }
}
