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
        return System.getProperty("user.dir") + File.separator + "web";
    }

}
