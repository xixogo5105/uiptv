package com.uiptv.service;

import com.uiptv.db.AccountInfoDb;
import com.uiptv.model.AccountInfo;

import static com.uiptv.util.StringUtils.isBlank;

public class AccountInfoService {
    private AccountInfoService() {
    }

    private static class SingletonHelper {
        private static final AccountInfoService INSTANCE = new AccountInfoService();
    }

    public static AccountInfoService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public AccountInfo getByAccountId(String accountId) {
        if (isBlank(accountId)) {
            return null;
        }
        return AccountInfoDb.get().getByAccountId(accountId);
    }

    public void save(AccountInfo info) {
        AccountInfoDb.get().save(info);
    }

    public void deleteByAccountId(String accountId) {
        AccountInfoDb.get().deleteByAccountId(accountId);
    }
}
