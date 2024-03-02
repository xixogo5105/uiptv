package com.uiptv.util;

public class WindowsPlatform {
    public static void executeCommand(String command, String args) {
        try {
            Runtime.getRuntime().exec(new String[]{command, args});
            Runtime.getRuntime().gc();
        } catch (Exception ignored) {
        }
    }
}
