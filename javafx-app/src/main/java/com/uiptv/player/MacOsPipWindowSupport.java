package com.uiptv.player;

import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

final class MacOsPipWindowSupport {
    private static final Logger LOGGER = Logger.getLogger(MacOsPipWindowSupport.class.getName());
    private static final AtomicBoolean NATIVE_ACCESS_NOTICE_SHOWN = new AtomicBoolean();
    private static final long CAN_JOIN_ALL_SPACES = 1L;
    private static final long FULL_SCREEN_AUXILIARY = 1L << 8;
    private static final long FLOATING_WINDOW_LEVEL = 3L;

    private MacOsPipWindowSupport() {
    }

    static void configureForFullscreenSpaces(Stage stage) {
        if (stage == null || !isMacOs()) {
            return;
        }

        Runnable configure = () -> {
            if (!stage.isShowing()) {
                return;
            }
            if (!NativeLibrary.class.getModule().isNativeAccessEnabled()) {
                showNativeAccessNotice();
                return;
            }
            try {
                Platform.runLater(() -> {
                    if (stage.isShowing()) {
                        try {
                            applyFullscreenSpacesBehavior(stage);
                        } catch (LinkageError | RuntimeException exception) {
                            LOGGER.log(Level.WARNING, "Could not enable PiP above macOS fullscreen Spaces", exception);
                        }
                    }
                });
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Could not enable PiP above macOS fullscreen Spaces", exception);
            }
        };
        if (Platform.isFxApplicationThread()) {
            configure.run();
        } else {
            Platform.runLater(configure);
        }
    }

    static long addFullscreenSpaceBehaviors(long behavior) {
        return behavior | CAN_JOIN_ALL_SPACES | FULL_SCREEN_AUXILIARY;
    }

    static long floatingWindowLevel() {
        return FLOATING_WINDOW_LEVEL;
    }

    static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    private static void applyFullscreenSpacesBehavior(Stage stage) {
        String title = stage.getTitle();
        Pointer appClass = objcGetClass("NSApplication");
        Pointer app = sendPointer(appClass, "sharedApplication");
        Pointer windows = sendPointer(app, "windows");
        long count = sendLong(windows, "count");
        for (long index = 0; index < count; index++) {
            Pointer window = sendPointer(windows, "objectAtIndex:", new NativeLong(index));
            Pointer nativeTitle = sendPointer(window, "title");
            Pointer utf8Title = sendPointer(nativeTitle, "UTF8String");
            if (utf8Title == null || !title.equals(utf8Title.getString(0))) {
                continue;
            }

            long behavior = sendLong(window, "collectionBehavior");
            sendVoid(window, "setCollectionBehavior:", new NativeLong(addFullscreenSpaceBehaviors(behavior)));
            sendVoid(window, "setLevel:", new NativeLong(FLOATING_WINDOW_LEVEL));
            sendVoid(window, "orderFrontRegardless");
            return;
        }
        LOGGER.warning("Could not find the native PiP window for macOS fullscreen Spaces");
    }

    private static void showNativeAccessNotice() {
        if (!NATIVE_ACCESS_NOTICE_SHOWN.compareAndSet(false, true)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.tr("commonInfo"));
        alert.setContentText(I18n.tr("pipNativeAccessMessage"));
        alert.show();
    }

    private static Pointer objcGetClass(String className) {
        return objcLibrary().getFunction("objc_getClass").invokePointer(new Object[]{className});
    }

    private static Pointer sendPointer(Pointer receiver, String selector, Object... arguments) {
        Object[] nativeArguments = nativeArguments(receiver, selector, arguments);
        return objcLibrary().getFunction("objc_msgSend").invokePointer(nativeArguments);
    }

    private static long sendLong(Pointer receiver, String selector, Object... arguments) {
        Object[] nativeArguments = nativeArguments(receiver, selector, arguments);
        return objcLibrary().getFunction("objc_msgSend").invokeLong(nativeArguments);
    }

    private static void sendVoid(Pointer receiver, String selector, Object... arguments) {
        Object[] nativeArguments = nativeArguments(receiver, selector, arguments);
        objcLibrary().getFunction("objc_msgSend").invokeVoid(nativeArguments);
    }

    private static Object[] nativeArguments(Pointer receiver, String selector, Object[] arguments) {
        Pointer selectorPointer = objcLibrary().getFunction("sel_registerName")
                .invokePointer(new Object[]{selector});
        Object[] nativeArguments = new Object[arguments.length + 2];
        nativeArguments[0] = receiver;
        nativeArguments[1] = selectorPointer;
        System.arraycopy(arguments, 0, nativeArguments, 2, arguments.length);
        return nativeArguments;
    }

    private static NativeLibrary objcLibrary() {
        return NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib");
    }

}
