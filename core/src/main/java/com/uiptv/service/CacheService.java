package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Account;

import java.io.IOException;

public interface CacheService {

    default void clearAllCache() {
        ConfigurationService.getInstance().clearAllCache();
    }
    default void clearCache(Account account) {
        ConfigurationService.getInstance().clearCache(account);
    }

    void reloadCache(Account account, LoggerCallback logger) throws IOException;

    /**
     * Reloads an account's cache, reporting each progress message together with the reload phase
     * (live TV, VOD, series) that produced it.
     * <p>
     * Deliberately a distinct method rather than an overload of
     * {@link #reloadCache(Account, LoggerCallback)}: overloading on a second functional interface
     * makes method-reference call sites such as {@code logs::add} ambiguous, because
     * {@code List.add} also matches the two-argument shape.
     */
    void reloadCacheWithPhases(Account account, ReloadPhaseLoggerCallback logger) throws IOException;

    boolean verifyMacAddress(Account account, String macAddress);

    int getChannelCountForAccount(String accountId);
}
