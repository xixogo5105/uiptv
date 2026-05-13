package com.uiptv.util;


import java.io.File;

import static com.uiptv.util.SystemUtils.IS_OS_MAC_OSX;
import static com.uiptv.util.SystemUtils.IS_OS_WINDOWS;

public class Platform {

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
        File webDir = new File(baseDir, "web");
        if (webDir.exists()) {
            return webDir.getAbsolutePath();
        }
        File resourcesWebDir = new File(baseDir, "src" + File.separator + "main" + File.separator + "resources" + File.separator + "web");
        if (resourcesWebDir.exists()) {
            return resourcesWebDir.getAbsolutePath();
        }
        File serverResourcesWebDir = new File(baseDir, "server" + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "web");
        if (serverResourcesWebDir.exists()) {
            return serverResourcesWebDir.getAbsolutePath();
        }
        File serverWebDir = new File(baseDir, "server" + File.separator + "web");
        if (serverWebDir.exists()) {
            return serverWebDir.getAbsolutePath();
        }
        return resourcesWebDir.getAbsolutePath();
    }

}
