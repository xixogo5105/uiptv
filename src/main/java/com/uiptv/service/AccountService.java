package com.uiptv.service;

import com.uiptv.db.AccountDb;
import com.uiptv.db.BookmarkDb;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.util.PingStalkerPortal;
import com.uiptv.util.ServerUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.isNotBlank;

public class AccountService {
    private static AccountService instance;

    private AccountService() {
    }

    public static synchronized AccountService getInstance() {
        if (instance == null) {
            instance = new AccountService();
        }
        return instance;
    }

    public void save(Account account) {
        if ((account.getType() == STALKER_PORTAL) && !account.getUrl().endsWith("/")) {
            account.setUrl(account.getUrl() + "/");
        }
        AccountDb.get().save(account);
    }

    public void delete(final String accountId) {
        Account account = AccountDb.get().getAccountById(accountId);
        if (account != null) {
            BookmarkDb.get().deleteByAccountName(account.getAccountName());
        }
        ChannelDb.get().deleteByAccount(accountId);
        CategoryDb.get().deleteByAccount(AccountDb.get().getAccountById(accountId));
        AccountDb.get().delete(accountId);
    }

    public void deleteAll() {
        AccountDb.get().getAccounts().forEach(account -> AccountDb.get().delete(account.getDbId()));
    }

    public LinkedHashMap<String, Account> getAll() {
        LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
        AccountDb.get().getAccounts().forEach(a -> accounts.put(a.getAccountName(), a));
        return accounts;
    }

    public Account getById(String dbId) {
        return AccountDb.get().getAccountById(dbId);
    }

    public Account getByName(String accountName) {
        return AccountDb.get().getAccountByName(accountName);
    }

    public String readToJson() {
        return ServerUtils.objectToJson(new ArrayList<>(getAll().values()));
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
}
