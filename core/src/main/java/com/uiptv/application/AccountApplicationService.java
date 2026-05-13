package com.uiptv.application;

import com.uiptv.service.AccountResolver;

import java.util.List;

public class AccountApplicationService {
    private final AccountResolver accountResolver = new AccountResolver();

    private AccountApplicationService() {
    }

    public static AccountApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public List<AccountResolver.AccountRow> listAccounts() {
        return accountResolver.resolveAccounts();
    }

    private static class SingletonHelper {
        private static final AccountApplicationService INSTANCE = new AccountApplicationService();
    }
}
