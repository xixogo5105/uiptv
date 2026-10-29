package com.uiptv.service;

import com.uiptv.db.*;
import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import com.uiptv.util.AccountCopyUtil;
import com.uiptv.util.AppLog;
import com.uiptv.util.PingStalkerPortal;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.XtremeCredentialsJson;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        Objects.requireNonNull(account, "account");
        sanitizeAccountFields(account);
        AccountType type = account.getType();
        if (type != null && type == STALKER_PORTAL && !isBlank(account.getUrl()) && !account.getUrl().endsWith("/")) {
            account.setUrl(account.getUrl() + "/");
        }
        Account previous = findSavedAccount(account);
        preserveResolvedServerPortalUrl(account, previous);
        if (sessionConfigurationChanged(previous, account)) {
            sessionTokenByAccountKey.remove(getSessionAccountKey(account));
        }
        AccountDb.get().save(account);
        Account persisted = AccountDb.get().getAccountByName(account.getAccountName());
        if (persisted == null || isBlank(persisted.getDbId())) {
            throw new IllegalStateException("Account save could not be confirmed from the database");
        }
        if (!Objects.equals(account.getType(), persisted.getType())
                || !Objects.equals(account.getUrl(), persisted.getUrl())
                || !Objects.equals(account.getUsername(), persisted.getUsername())
                || !Objects.equals(account.getPassword(), persisted.getPassword())
                || !Objects.equals(account.getXtremeCredentialsJson(), persisted.getXtremeCredentialsJson())) {
            throw new IllegalStateException("Saved account values did not match the database record");
        }
        account.setDbId(persisted.getDbId());
        touchChange();
        if (previous != null && previous.isCacheEnabled() && !persisted.isCacheEnabled()) {
            clearCatalogCacheInBackground(AccountCopyUtil.detachedCopy(persisted));
        }
    }

    private void clearCatalogCacheInBackground(Account account) {
        Thread.ofVirtual().name("uiptv-account-catalog-cache-clear").start(() -> {
            try {
                ConfigurationDb.get().clearCatalogCache(account);
            } catch (Exception error) {
                AppLog.addErrorLog(AccountService.class,
                        "Unable to clear catalogue cache after disabling account caching: " + error.getMessage());
            }
        });
    }

    private Account findSavedAccount(Account account) {
        if (isNotBlank(account.getDbId())) {
            Account byId = AccountDb.get().getAccountById(account.getDbId());
            if (byId != null) {
                return byId;
            }
        }
        return AccountDb.get().getAccountByName(account.getAccountName());
    }

    private void preserveResolvedServerPortalUrl(Account account, Account previous) {
        if (previous != null
                && isBlank(account.getServerPortalUrl())
                && Objects.equals(account.getType(), previous.getType())
                && Objects.equals(account.getUrl(), previous.getUrl())
                && isNotBlank(previous.getServerPortalUrl())) {
            // The account editor does not expose this cached endpoint. Keep it when the base
            // portal is unchanged, so saving a display preference cannot discard it.
            account.setServerPortalUrl(previous.getServerPortalUrl());
        }
    }

    private boolean sessionConfigurationChanged(Account previous, Account account) {
        if (previous == null
                || !Objects.equals(previous.getAccountName(), account.getAccountName())
                || !Objects.equals(previous.getType(), account.getType())) {
            return true;
        }
        if (account.getType() != STALKER_PORTAL) {
            return false;
        }
        return !Objects.equals(previous.getUrl(), account.getUrl())
                || !Objects.equals(previous.getServerPortalUrl(), account.getServerPortalUrl())
                || !Objects.equals(previous.getUsername(), account.getUsername())
                || !Objects.equals(previous.getPassword(), account.getPassword())
                || !Objects.equals(previous.getMacAddress(), account.getMacAddress())
                || !Objects.equals(previous.getMacAddressList(), account.getMacAddressList())
                || !Objects.equals(previous.getSerialNumber(), account.getSerialNumber())
                || !Objects.equals(previous.getDeviceId1(), account.getDeviceId1())
                || !Objects.equals(previous.getDeviceId2(), account.getDeviceId2())
                || !Objects.equals(previous.getSignature(), account.getSignature());
    }

    // Account.getType() has a default value of STALKER_PORTAL but could be explicitly set to null.
    // The comparison with STALKER_PORTAL is safe because null == STALKER_PORTAL is false.

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

    /**
     * Returns every account keyed by account name.
     * <p>
     * Name-keyed lookup is genuinely required here: bookmarks and playback requests persist
     * {@code accountName} rather than the primary key, so this map is the join they resolve
     * through. {@code accountName} carries a {@code UNIQUE} constraint, so entries cannot collide.
     * <p>
     * Prefer {@link #getAllById()} for enumeration and bulk work: it is keyed by the primary key
     * and states the account's identity directly, instead of relying on a display name.
     */
    public Map<String, Account> getAll() {
        LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
        for (Account account : AccountDb.get().getAccounts()) {
            applySessionToken(account);
            accounts.put(account.getAccountName(), account);
        }
        return accounts;
    }

    /**
     * Returns every account keyed by its primary key. This is the lossless view and is what bulk
     * enumeration (cache refresh, resolvers, exports) should use, so a batch never has to reason
     * about display-name identity.
     */
    public Map<String, Account> getAllById() {
        LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
        for (Account account : AccountDb.get().getAccounts()) {
            applySessionToken(account);
            if (isNotBlank(account.getDbId())) {
                accounts.put(account.getDbId(), account);
            }
        }
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
        SeriesWatchingNowSnapshotDb.get().deleteByAccount(accountId);
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
