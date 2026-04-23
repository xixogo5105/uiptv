package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Account;

import java.io.IOException;

public interface AccountCacheReloader {
    void reloadCache(Account account, LoggerCallback logger) throws IOException;
}
