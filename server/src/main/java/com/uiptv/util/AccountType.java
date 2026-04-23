package com.uiptv.util;

import java.util.Arrays;

public enum AccountType {

    STALKER_PORTAL("Stalker Portal"),

    M3U8_LOCAL("M3U8 Local"),

    M3U8_URL("M3U8 URL"),

    XTREME_API("Xtreme API"),
    RSS_FEED("RSS Feed");

    private final String display;

    AccountType(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public static AccountType getAccountTypeByDisplay(String display) {
        if (display == null || display.trim().isEmpty()) {
            throw new IllegalArgumentException("Account type display cannot be blank");
        }
        String normalizedDisplay = display.trim();
        return Arrays.stream(AccountType.values())
                .filter(a -> a.getDisplay().equalsIgnoreCase(normalizedDisplay))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown account type display: " + display));
    }
}
