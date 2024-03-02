package com.uiptv.util;

import java.io.File;

import static com.uiptv.util.SystemUtils.*;

public class Platform {
    public static void executeCommand(String command, String args) {
        if (IS_OS_WINDOWS) {
            WindowsPlatform.executeCommand(command, args);
        } else if (IS_OS_LINUX || IS_OS_MAC_OSX) {
            LinuxPlatform.executeCommand(command, args);
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
