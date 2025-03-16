package com.uiptv.util;

import com.uiptv.ui.LogDisplayUI;

public class LinuxPlatform {
    public static void executeCommand(String command, String args) {
        try {
            Runtime.getRuntime().exec(new String[]{command, args});
            Runtime.getRuntime().gc();
        } catch (Exception e) {
            LogDisplayUI.addLog("Error occured while executing Linux commands. Error Description: " + e.getMessage());
        }
        Runtime.getRuntime().gc();
    }
}
