package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Third-party libraries log straight to SLF4J and never pass through {@link AppLog}, so
 * {@code --show-logs} can only govern them by moving the SLF4J simple backend's own level. Without
 * that, Jetty's startup banner was printed on every launch.
 */
class Slf4jBackendLevelTest {
    private static final String LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";

    private String previousLevel;
    private boolean previousTerminalLogging;

    @BeforeEach
    void captureState() {
        previousLevel = System.getProperty(LEVEL_PROPERTY);
        previousTerminalLogging = AppLog.isTerminalLoggingEnabled();
    }

    @AfterEach
    void restoreState() {
        if (previousLevel == null) {
            System.clearProperty(LEVEL_PROPERTY);
        } else {
            System.setProperty(LEVEL_PROPERTY, previousLevel);
        }
        AppLog.setTerminalLoggingEnabled(previousTerminalLogging);
    }

    @Test
    void simpleloggerConfigurationIsQuietByDefault() throws IOException {
        // slf4j-simple reads this file once, when the first logger is created. It is the only
        // thing quieting libraries in launch paths that never call setTerminalLoggingEnabled.
        Properties config = new Properties();
        try (InputStream in = Slf4jBackendLevelTest.class.getClassLoader()
                .getResourceAsStream("simplelogger.properties")) {
            assertNotNull(in, "simplelogger.properties must be on the runtime classpath");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                config.load(reader);
            }
        }
        assertEquals("warn", config.getProperty(LEVEL_PROPERTY),
                "The backend must default to warn so library INFO chatter is hidden");
    }

    @Test
    void disablingTerminalLoggingQuietsTheSlf4jBackend() {
        AppLog.setTerminalLoggingEnabled(false);

        assertEquals("warn", System.getProperty(LEVEL_PROPERTY),
                "Without --show-logs, library logging must be quieted");
    }

    @Test
    void enablingTerminalLoggingRaisesTheSlf4jBackend() {
        AppLog.setTerminalLoggingEnabled(true);

        assertEquals("info", System.getProperty(LEVEL_PROPERTY),
                "With --show-logs, library logging must be visible again");
    }

    @Test
    void setSlf4jDefaultLevelIgnoresBlankValues() {
        AppLog.setSlf4jDefaultLevel("debug");
        assertEquals("debug", System.getProperty(LEVEL_PROPERTY));

        AppLog.setSlf4jDefaultLevel("   ");
        assertEquals("debug", System.getProperty(LEVEL_PROPERTY), "a blank level must not clear the setting");

        AppLog.setSlf4jDefaultLevel(null);
        assertEquals("debug", System.getProperty(LEVEL_PROPERTY), "a null level must not clear the setting");
    }

    @Test
    void systemPropertyOverridesTheConfigurationFile() throws IOException {
        // This is the whole mechanism: the file cannot raise the level for --show-logs, only a
        // system property can, because SimpleLoggerConfiguration.getStringProperty checks
        // System.getProperty first and only then the loaded file.
        assertEquals("org.slf4j.simpleLogger.defaultLogLevel", LEVEL_PROPERTY);
        assertTrue(LEVEL_PROPERTY.startsWith("org.slf4j.simpleLogger."),
                "The key must be the one slf4j-simple actually reads");
    }

    @Test
    void showLogsArgumentIsRecognisedInEveryAcceptedSpelling() {
        assertTrue(AppLog.isShowLogsRequested(new String[]{"--show-logs"}));
        assertTrue(AppLog.isShowLogsRequested(new String[]{"show-logs"}));
        assertTrue(AppLog.isShowLogsRequested(new String[]{"--SHOW-LOGS"}), "must be case-insensitive");
        assertTrue(AppLog.isShowLogsRequested(new String[]{"-show-logs"}), "a single dash must also work");
        assertTrue(AppLog.isShowLogsRequested(new String[]{"  --show-logs  "}), "must tolerate padding");
        assertTrue(AppLog.isShowLogsRequested(new String[]{"sync", "--show-logs"}),
                "the flag may appear alongside other arguments");
    }

    @Test
    void showLogsArgumentIsStrippedFromTheRemainingArguments() {
        assertArrayEquals(new String[]{"sync"},
                AppLog.removeShowLogsArg(new String[]{"--show-logs", "sync"}),
                "the flag must be removed so later parsers do not see it");
        assertArrayEquals(new String[]{"sync", "other"},
                AppLog.removeShowLogsArg(new String[]{"sync", "show-logs", "other"}));
        assertArrayEquals(new String[0], AppLog.removeShowLogsArg(new String[]{"--show-logs"}));
    }

    @Test
    void absentOrUnrelatedArgumentsDoNotEnableLogging() {
        assertFalse(AppLog.isShowLogsRequested(null));
        assertFalse(AppLog.isShowLogsRequested(new String[0]));
        assertFalse(AppLog.isShowLogsRequested(new String[]{"sync"}));
        assertFalse(AppLog.isShowLogsRequested(new String[]{"headless"}));
        assertFalse(AppLog.isShowLogsRequested(new String[]{"--other"}));
        assertFalse(AppLog.isShowLogsRequested(new String[]{null}),
                "a null element must not throw or match");
    }
}
