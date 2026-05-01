package com.uiptv.service;

@FunctionalInterface
public interface AccountChangeListener {
    void onAccountsChanged(long revision);
}
