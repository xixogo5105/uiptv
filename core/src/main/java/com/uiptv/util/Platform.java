package com.uiptv.util;


import java.io.File;

import static com.uiptv.util.SystemUtils.IS_OS_MAC_OSX;
import static com.uiptv.util.SystemUtils.IS_OS_WINDOWS;

public class Platform {
    private static final String RESOURCES_DIRECTORY = "resources";

    private Platform() {
        super();
    }

    public static void executeCommand(String command, String args) {
        try {
            new CmdLineRunner().exec(command + " " + args);
        } catch (CmdLineRunner.CmdLineException e) {
            com.uiptv.util.AppLog.addErrorLog(Platform.class, "Error occurred while executing commands. Error Description: " + e.getMessage());
        }
    }

    public static String getUserHomeDirPath() {
        //System.getProperty("user.dir")
        if (IS_OS_WINDOWS || IS_OS_MAC_OSX) {
            return System.getProperty("user.home") + File.separator + "uiptv";
        }
        return System.getProperty("user.home") + File.separator + ".config" + File.separator + "uiptv";
    }
    public static String getWebServerRootPath() {
        String baseDir = System.getProperty("user.dir");
        File resourcesWebDir = webRoot(baseDir, "src", "main", RESOURCES_DIRECTORY, "web");
        File[] candidates = {
                webRoot(baseDir, "web"),
                resourcesWebDir,
                webRoot(baseDir, "target", "classes", "web"),
                webRoot(baseDir, "api-server", "src", "main", RESOURCES_DIRECTORY, "web"),
                webRoot(baseDir, "api-server", "target", "classes", "web"),
                webRoot(baseDir, "server", "src", "main", RESOURCES_DIRECTORY, "web"),
                webRoot(baseDir, "server", "web")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
        }
        return resourcesWebDir.getAbsolutePath();
    }

    private static File webRoot(String baseDir, String first, String... more) {
        File path = new File(baseDir, first);
        for (String segment : more) {
            path = new File(path, segment);
        }
        return path;
    }

}
