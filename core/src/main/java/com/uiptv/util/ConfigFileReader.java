package com.uiptv.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static com.uiptv.util.Platform.getUserHomeDirPath;

public class ConfigFileReader {
    private static String configFilePath;

    static {
        configFilePath = getUserHomeDirPath() + File.separator + "uiptv.ini";
    }

    private ConfigFileReader() {
    }

    private static String configFilePath() {
        return configFilePath;
    }

    public static String getDbPathFromConfigFile() {
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(configFilePath())) {
            properties.load(inputStream);
            return properties.getProperty("db.path");
        } catch (IOException _) {
            return null;
        }
    }

    public static String getThumbnailTmpCacheDir() {
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(configFilePath())) {
            properties.load(inputStream);
            String dir = properties.getProperty("thumbnail.tmp.cache.dir");
            if (dir == null || dir.isBlank()) {
                return null;
            }
            File path = new File(dir.trim());
            if (!path.isAbsolute()) {
                return null;
            }
            return path.getAbsolutePath();
        } catch (IOException _) {
            return null;
        }
    }

    public static int getThumbnailCacheTtlHours() {
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(configFilePath())) {
            properties.load(inputStream);
            String value = properties.getProperty("thumbnail.cache.ttl");
            if (value != null && !value.isBlank()) {
                return parseThumbnailTtlHours(value.trim());
            }
        } catch (IOException | NumberFormatException _) {
            // fall through to default
        }
        return 7 * 24;
    }

    private static int parseThumbnailTtlHours(String value) {
        if (value.isEmpty()) {
            return 7 * 24;
        }
        boolean hours = Character.toUpperCase(value.charAt(value.length() - 1)) == 'H';
        String numeric = sanitizeNumeric(value);
        if (numeric.isEmpty()) {
            return 7 * 24;
        }
        double parsed = Double.parseDouble(numeric);
        int result = (int) Math.round(parsed);
        if (hours) {
            return Math.max(1, result);
        }
        return Math.max(24, result * 24);
    }

    private static String sanitizeNumeric(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
