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

    boolean verifyMacAddress(Account account, String macAddress, LoggerCallback logger);

    int getChannelCountForAccount(String accountId);
}
