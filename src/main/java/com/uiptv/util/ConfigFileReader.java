package com.uiptv.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static com.uiptv.util.Platform.getUserHomeDirPath;

public class ConfigFileReader {
    private static final String CONFIG_FILE_PATH = getUserHomeDirPath() + File.separator + "uiptv.ini";

    public static String getDbPathFromConfigFile() {
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(CONFIG_FILE_PATH)) {
            properties.load(inputStream);
            return properties.getProperty("db.path");
        } catch (IOException e) {
            // Handle the exception (e.g., log it)
            return null;
        }
    }
}
