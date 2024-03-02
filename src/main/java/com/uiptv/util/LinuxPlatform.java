package com.uiptv.util;

public class LinuxPlatform {
    public static void executeCommand(String command, String args) {
        try {
            Runtime.getRuntime().exec(new String[]{command, args});
            Runtime.getRuntime().gc();
        } catch (Exception e) {
            System.out.println("Error occured while executing Linux commands. Error Description: " + e.getMessage());
        }
        Runtime.getRuntime().gc();
    }
}
