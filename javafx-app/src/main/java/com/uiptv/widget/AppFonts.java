package com.uiptv.widget;

import com.uiptv.util.AppLog;
import javafx.scene.text.Font;

import java.io.InputStream;

public final class AppFonts {
    public static final String UI_FONT_FAMILY = "Roboto";
    private static final String[] FONT_RESOURCES = {
            "/fonts/roboto/Roboto-Regular.ttf",
            "/fonts/roboto/Roboto-Medium.ttf",
            "/fonts/roboto/Roboto-SemiBold.ttf",
            "/fonts/roboto/Roboto-Bold.ttf"
    };
    private static volatile boolean loaded;

    private AppFonts() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        synchronized (AppFonts.class) {
            if (loaded) {
                return;
            }
            for (String resource : FONT_RESOURCES) {
                loadFont(resource);
            }
            loaded = true;
        }
    }

    private static void loadFont(String resource) {
        try (InputStream inputStream = AppFonts.class.getResourceAsStream(resource)) {
            if (inputStream == null) {
                AppLog.addWarningLog(AppFonts.class, "Missing embedded font: " + resource);
                return;
            }
            Font font = Font.loadFont(inputStream, 13);
            if (font == null) {
                AppLog.addWarningLog(AppFonts.class, "Unable to load embedded font: " + resource);
            }
        } catch (Exception e) {
            AppLog.addWarningLog(AppFonts.class, "Failed to load embedded font " + resource + ": " + e.getMessage());
        }
    }
}
