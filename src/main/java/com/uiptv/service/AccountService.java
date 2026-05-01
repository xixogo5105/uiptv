package com.uiptv.service;

import com.uiptv.db.*;
import com.uiptv.model.Account;
import com.uiptv.util.PingStalkerPortal;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.XtremeCredentialsJson;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class AccountService {
    private final AtomicLong changeRevision = new AtomicLong(1);
    private final Set<AccountChangeListener> changeListeners = new CopyOnWriteArraySet<>();
    private final Map<String, String> sessionTokenByAccountKey = new ConcurrentHashMap<>();

    private AccountService() {
    }

    public static AccountService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public void save(Account account) {
        sanitizeAccountFields(account);
        if ((account.getType() == STALKER_PORTAL) && !account.getUrl().endsWith("/")) {
            account.setUrl(account.getUrl() + "/");
        }
        sessionTokenByAccountKey.remove(getSessionAccountKey(account));
        AccountDb.get().save(account);
        touchChange();
    }

    public void delete(final String accountId) {
        Account account = AccountDb.get().getAccountById(accountId);
        deleteAccountData(accountId, account);
    }

    public void deleteAll() {
        sessionTokenByAccountKey.clear();
        AccountDb.get().getAccounts().forEach(account -> deleteAccountData(account.getDbId(), account));
        touchChange();
    }

    public void refreshFromDatabase() {
        sessionTokenByAccountKey.clear();
        touchChange();
    }

    public void addChangeListener(AccountChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(AccountChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    public Map<String, Account> getAll() {
        LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
        AccountDb.get().getAccounts().forEach(a -> {
            applySessionToken(a);
            accounts.put(a.getAccountName(), a);
        });
        return accounts;
    }

    public Account getById(String dbId) {
        Account account = AccountDb.get().getAccountById(dbId);
        applySessionToken(account);
        return account;
    }

    public Account getByName(String accountName) {
        Account account = AccountDb.get().getAccountByName(accountName);
        applySessionToken(account);
        return account;
    }

    public String readToJson() {
        return ServerUtils.objectToJson(new AccountResolver().resolveAccounts());
    }

    /**
     * Resolve and persist serverPortalUrl once, so subsequent calls can reuse it.
     * Returns the current/resolved endpoint (possibly blank if resolution failed).
     */
    public String ensureServerPortalUrl(Account account) {
        if (account == null) {
            return "";
        }
        if (isNotBlank(account.getServerPortalUrl())) {
            return account.getServerPortalUrl();
        }
        String resolved = PingStalkerPortal.ping(account);
        if (isNotBlank(resolved)) {
            account.setServerPortalUrl(resolved);
            AccountDb.get().saveServerPortalUrl(account);
        }
        return account.getServerPortalUrl();
    }

    public void syncSessionToken(Account account) {
        if (account == null) {
            return;
        }
        String key = getSessionAccountKey(account);
        if (isBlank(key)) {
            return;
        }
        if (isNotBlank(account.getToken())) {
            sessionTokenByAccountKey.put(key, account.getToken());
        } else {
            sessionTokenByAccountKey.remove(key);
        }
    }

    private void applySessionToken(Account account) {
        if (account == null || isNotBlank(account.getToken())) {
            return;
        }
        String key = getSessionAccountKey(account);
        if (isBlank(key)) {
            return;
        }
        String cachedToken = sessionTokenByAccountKey.get(key);
        if (isNotBlank(cachedToken)) {
            account.setToken(cachedToken);
        }
    }

    private String getSessionAccountKey(Account account) {
        if (account == null) {
            return "";
        }
        if (isNotBlank(account.getDbId())) {
            return account.getDbId().trim();
        }
        if (isNotBlank(account.getAccountName())) {
            return account.getAccountName().trim().toLowerCase();
        }
        return "";
    }

    private void deleteAccountData(String accountId, Account account) {
        if (account != null) {
            BookmarkService.getInstance().removeByAccountName(account.getAccountName());
            sessionTokenByAccountKey.remove(getSessionAccountKey(account));
            AccountInfoService.getInstance().deleteByAccountId(account.getDbId());
            PublishedM3uSelectionDb.get().deleteByAccountId(account.getDbId());
            PublishedM3uCategorySelectionDb.get().deleteByAccountId(account.getDbId());
            PublishedM3uChannelSelectionDb.get().deleteByAccountId(account.getDbId());
        }
        SeriesWatchStateDb.get().deleteByAccount(accountId);
        VodWatchStateDb.get().deleteByAccount(accountId);
        ChannelDb.get().deleteByAccount(accountId);
        CategoryDb.get().deleteByAccount(account);
        AccountDb.get().delete(accountId);
        touchChange();
    }

    private void touchChange() {
        long revision = changeRevision.incrementAndGet();
        for (AccountChangeListener listener : changeListeners) {
            try {
                listener.onAccountsChanged(revision);
            } catch (Exception _) {
                // Listener failures must never break account updates.
            }
        }
    }

    private void sanitizeAccountFields(Account account) {
        if (account == null) {
            return;
        }
        sanitizeStalkerMacAddresses(account);
        sanitizeXtremeCredentials(account);
    }

    private void sanitizeStalkerMacAddresses(Account account) {
        if (account.getType() != STALKER_PORTAL) {
            return;
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        String primaryMac = account.getMacAddress();
        if (isNotBlank(primaryMac)) {
            ordered.add(primaryMac.replace(" ", ""));
        }
        String macList = account.getMacAddressList();
        if (isNotBlank(macList)) {
            for (String mac : macList.split(",")) {
                String trimmed = mac.replace(" ", "");
                if (isNotBlank(trimmed)) {
                    ordered.add(trimmed);
                }
            }
        }
        if (ordered.isEmpty()) {
            return;
        }
        String normalizedList = String.join(",", ordered);
        account.setMacAddressList(normalizedList);
        if (isBlank(primaryMac) || !ordered.contains(primaryMac.replace(" ", ""))) {
            account.setMacAddress(ordered.iterator().next());
        }
    }

    private void sanitizeXtremeCredentials(Account account) {
        if (account.getType() != XTREME_API) {
            return;
        }
        String username = account.getUsername();
        String password = account.getPassword();
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(account.getXtremeCredentialsJson());
        if (entries.isEmpty() && isNotBlank(username) && isNotBlank(password)) {
            entries = List.of(new XtremeCredentialsJson.Entry(username, password, true));
        }
        if (entries.isEmpty()) {
            return;
        }
        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(entries, username);
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(normalized);
        if (defaultEntry != null) {
            account.setUsername(defaultEntry.username());
            account.setPassword(defaultEntry.password());
        }
        account.setXtremeCredentialsJson(XtremeCredentialsJson.toJson(normalized));
    }

    private static class SingletonHelper {
        private static final AccountService INSTANCE = new AccountService();
    }
}
