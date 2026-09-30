package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.service.cache.AccountCacheReloaderFactory;
import com.uiptv.util.AccountCopyUtil;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.util.StringUtils.isNotBlank;

public class CacheServiceImpl implements CacheService {
    private final AccountCacheReloaderFactory reloaderFactory = new AccountCacheReloaderFactory();

    private static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", System.currentTimeMillis() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    @Override
    public void reloadCache(Account account, LoggerCallback logger) throws IOException {
        reloadCacheWithPhases(account, (message, phase) -> logger.log(message));
    }

    @Override
    public void reloadCacheWithPhases(Account account, ReloadPhaseLoggerCallback logger) throws IOException {
        if (account == null) {
            return;
        }
        // Reloaders mutate the account in place (action, token, serverPortalUrl, macAddress).
        // The caller-supplied instance is shared by reference with the desktop UI, the embedded
        // HTTP server and account change listeners, so a bulk refresh would publish transient
        // state to unrelated threads - and, for Stalker portals, a concurrent reader observing a
        // nulled token starts a second handshake that invalidates the in-flight session.
        // Operate on a detached copy and publish only durable results back to the caller.
        Account reloadAccount = AccountCopyUtil.detachedCopy(account);
        // The reload copy is the authority on the current phase: reloaders flip its action as
        // they walk live -> VOD -> series. Reporting it here keeps callers from having to infer
        // the phase by reading a shared, mutable account.
        LoggerCallback phaseAwareAdapter = message -> logger.log(message, reloadAccount.getAction());
        reloaderFactory.get(reloadAccount.getType()).reloadCache(reloadAccount, phaseAwareAdapter);
        publishReloadOutcome(account, reloadAccount);
    }

    /**
     * Copies back the fields a reload is allowed to change. {@code action} is deliberately
     * excluded: it is transient reload state, not a durable account change.
     */
    private void publishReloadOutcome(Account target, Account reloadAccount) {
        if (target == null || reloadAccount == null) {
            return;
        }
        String resolvedPortalUrl = reloadAccount.getServerPortalUrl();
        if (isNotBlank(resolvedPortalUrl) && !resolvedPortalUrl.equals(target.getServerPortalUrl())) {
            target.setServerPortalUrl(resolvedPortalUrl);
        }
        String resolvedToken = reloadAccount.getToken();
        if (isNotBlank(resolvedToken) && !resolvedToken.equals(target.getToken())) {
            target.setToken(resolvedToken);
        }
        String resolvedMac = reloadAccount.getMacAddress();
        if (isNotBlank(resolvedMac) && !resolvedMac.equals(target.getMacAddress())) {
            target.setMacAddress(resolvedMac);
        }
    }

    @Override
    public boolean verifyMacAddress(Account account, String macAddress) {
        if (account == null || account.getType() != AccountType.STALKER_PORTAL) {
            return false;
        }

        String originalMac = account.getMacAddress();
        Account verificationAccount = AccountCopyUtil.copyForMac(account, macAddress);
        if (verificationAccount == null) {
            return false;
        }
        try {
            HandshakeService.getInstance().connect(verificationAccount);

            if (verificationAccount.isNotConnected()) {
                return false;
            }

            account.setMacAddress(macAddress);
            account.setToken(verificationAccount.getToken());
            if (account.getServerPortalUrl() == null || account.getServerPortalUrl().isBlank()) {
                account.setServerPortalUrl(verificationAccount.getServerPortalUrl());
            }
            String jsonCategories = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
            return !CategoryService.getInstance().parseCategories(jsonCategories, false).isEmpty();
        } catch (Exception _) {
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
