package com.uiptv.service;

@FunctionalInterface
public interface VodWatchStateChangeListener {
    void onChanged(String accountId, String vodId);
}
