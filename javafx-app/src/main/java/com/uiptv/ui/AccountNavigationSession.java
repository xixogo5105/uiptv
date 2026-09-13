package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;

public final class AccountNavigationSession {

    public enum Level {
        ACCOUNTS,
        CATEGORIES,
        CHANNELS
    }

    private static volatile Level level = Level.ACCOUNTS;
    private static volatile Account account = null;
    private static volatile Account.AccountAction action = Account.AccountAction.itv;
    private static volatile String categoryId = null;
    private static volatile Channel channel = null;

    private AccountNavigationSession() {
    }

    public static Level getLevel() {
        return level;
    }

    public static Account getAccount() {
        return account;
    }

    public static Account.AccountAction getAction() {
        return action;
    }

    public static String getCategoryId() {
        return categoryId;
    }

    public static Channel getChannel() {
        return channel;
    }

    public static void setAtAccounts() {
        level = Level.ACCOUNTS;
        account = null;
        categoryId = null;
        channel = null;
    }

    public static void setAtCategories(Account acc, Account.AccountAction act) {
        setAtCategories(acc, act, null);
    }

    public static void setAtCategories(Account acc, Account.AccountAction act, String catId) {
        if (acc == null) {
            setAtAccounts();
            return;
        }
        level = Level.CATEGORIES;
        account = acc;
        action = act != null ? act : (acc.getAction() != null ? acc.getAction() : Account.AccountAction.itv);
        categoryId = catId;
        channel = null;
    }

    public static void setCategoryId(String catId) {
        categoryId = catId;
    }

    public static void setAtChannels(Account acc, Account.AccountAction act, String catId, Channel ch) {
        if (acc == null) {
            setAtAccounts();
            return;
        }
        level = Level.CHANNELS;
        account = acc;
        action = act != null ? act : (acc.getAction() != null ? acc.getAction() : Account.AccountAction.itv);
        categoryId = catId;
        channel = ch;
    }

    public static void setChannel(Channel ch) {
        channel = ch;
    }
}
