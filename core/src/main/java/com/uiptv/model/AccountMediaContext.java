package com.uiptv.model;

/**
 * Immutable account plus media mode. This keeps ITV/VOD/series routing out of
 * long-lived mutable {@link Account} objects in the UI.
 */
public record AccountMediaContext(AccountView account, Account.AccountAction action) {
    public AccountMediaContext {
        action = action == null ? Account.AccountAction.itv : action;
    }

    public static AccountMediaContext from(Account account) {
        return from(account, account == null ? Account.AccountAction.itv : account.getAction());
    }

    public static AccountMediaContext from(Account account, Account.AccountAction action) {
        AccountView view = AccountView.from(account);
        return view == null ? null : new AccountMediaContext(view, action);
    }

    public AccountMediaContext withAction(Account.AccountAction nextAction) {
        return new AccountMediaContext(account, nextAction);
    }

    public AccountMediaContext withServerPortalUrl(String serverPortalUrl) {
        return new AccountMediaContext(
                account == null ? null : account.withServerPortalUrl(serverPortalUrl),
                action
        );
    }

    public Account toAccount() {
        return account == null ? null : account.toAccount(action);
    }

    public String dbId() {
        return account == null ? "" : safe(account.dbId());
    }

    public String accountName() {
        return account == null ? "" : safe(account.accountName());
    }

    public com.uiptv.util.AccountType type() {
        return account == null ? null : account.type();
    }

    public String serverPortalUrl() {
        return account == null ? "" : safe(account.serverPortalUrl());
    }

    public String url() {
        return account == null ? "" : safe(account.url());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
