package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.service.cache.AccountCacheReloaderFactory;
import com.uiptv.util.FetchAPI;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;

public class CacheServiceImpl implements CacheService {
    private final AccountCacheReloaderFactory reloaderFactory = new AccountCacheReloaderFactory();

    private static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    @Override
    public void reloadCache(Account account, LoggerCallback logger) throws IOException {
        reloaderFactory.get(account.getType()).reloadCache(account, logger);
    }

    @Override
    public boolean verifyMacAddress(Account account, String macAddress) {
        if (account == null) {
            return false;
        }

        String originalMac = account.getMacAddress();
        try {
            account.setMacAddress(macAddress);
            HandshakeService.getInstance().connect(account);

            if (account.isNotConnected()) {
                return false;
            }

            String jsonCategories = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
            return !CategoryService.getInstance().parseCategories(jsonCategories, false).isEmpty();
        } catch (Exception e) {
            return false;
        } finally {
            account.setMacAddress(originalMac);
        }
    }

    @Override
    public int getChannelCountForAccount(String accountId) {
        return ChannelDb.get().getChannelCountForAccount(accountId);
    }
}
