package com.uiptv.shared.account;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum AccountType {
    STALKER_PORTAL("Stalker Portal"),
    XTREME_API("Xtreme API"),
    M3U8_URL("M3U8 URL"),
    M3U8_LOCAL("M3U8 Local"),
    RSS_FEED("RSS Feed");

    public static final Set<AccountType> CACHE_REFRESH_SUPPORTED = immutableSet(
            STALKER_PORTAL,
            XTREME_API,
            M3U8_URL,
            M3U8_LOCAL
    );
    public static final Set<AccountType> VOD_AND_SERIES_SUPPORTED = immutableSet(STALKER_PORTAL, XTREME_API);
    public static final Set<AccountType> PREDEFINED_URL_SUPPORTED = immutableSet(RSS_FEED, M3U8_URL, M3U8_LOCAL);

    private final String display;

    AccountType(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    private static Set<AccountType> immutableSet(AccountType first, AccountType... rest) {
        EnumSet<AccountType> values = EnumSet.of(first, rest);
        return Collections.unmodifiableSet(values);
    }
}
