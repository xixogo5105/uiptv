package com.uiptv.application;

import com.uiptv.model.Account;

public enum CatalogMode {
    ITV(Account.AccountAction.itv),
    VOD(Account.AccountAction.vod),
    SERIES(Account.AccountAction.series);

    private final Account.AccountAction accountAction;

    CatalogMode(Account.AccountAction accountAction) {
        this.accountAction = accountAction;
    }

    public Account.AccountAction toAccountAction() {
        return accountAction;
    }

    public static CatalogMode fromRequest(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return ITV;
        }
        try {
            return CatalogMode.valueOf(rawMode.trim().toUpperCase());
        } catch (Exception _) {
            return ITV;
        }
    }
}
