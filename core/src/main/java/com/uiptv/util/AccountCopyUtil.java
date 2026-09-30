package com.uiptv.util;

import com.uiptv.model.Account;

public final class AccountCopyUtil {
    private AccountCopyUtil() {
    }

    /**
     * Creates a detached copy of the supplied account.
     * <p>
     * {@link Account} is a mutable bean and the cache reload path mutates it in place
     * ({@code action}, {@code token}, {@code serverPortalUrl}). Those instances are shared by
     * reference with the desktop UI, the embedded HTTP server and change listeners, so mutating
     * one during a reload leaks state into unrelated threads. Reloaders must therefore work on a
     * private copy and publish any durable results back explicitly.
     */
    public static Account detachedCopy(Account source) {
        if (source == null) {
            return null;
        }
        Account copy = new Account();
        copy.setServerPortalUrl(source.getServerPortalUrl());
        copy.setAction(source.getAction());
        copy.setAccountName(source.getAccountName());
        copy.setUsername(source.getUsername());
        copy.setPassword(source.getPassword());
        copy.setXtremeCredentialsJson(source.getXtremeCredentialsJson());
        copy.setUrl(source.getUrl());
        copy.setMacAddress(source.getMacAddress());
        copy.setMacAddressList(source.getMacAddressList());
        copy.setSerialNumber(source.getSerialNumber());
        copy.setDeviceId1(source.getDeviceId1());
        copy.setDeviceId2(source.getDeviceId2());
        copy.setSignature(source.getSignature());
        copy.setEpg(source.getEpg());
        copy.setM3u8Path(source.getM3u8Path());
        copy.setDbId(source.getDbId());
        copy.setToken(source.getToken());
        copy.setPinToTop(source.isPinToTop());
        copy.setResolveChainAndDeepRedirects(source.isResolveChainAndDeepRedirects());
        copy.setType(source.getType());
        copy.setHttpMethod(source.getHttpMethod());
        copy.setTimezone(source.getTimezone());
        copy.setParentalLock(source.isParentalLock());
        return copy;
    }

    public static Account copyForMac(Account source, String macAddress) {
        if (source == null) {
            return null;
        }
        Account copy = new Account(
                source.getAccountName(),
                source.getUsername(),
                source.getPassword(),
                source.getUrl(),
                macAddress,
                source.getMacAddressList(),
                source.getSerialNumber(),
                source.getDeviceId1(),
                source.getDeviceId2(),
                source.getSignature(),
                source.getType(),
                source.getEpg(),
                source.getM3u8Path(),
                source.isPinToTop()
        );
        copy.setAction(source.getAction());
        copy.setHttpMethod(source.getHttpMethod());
        copy.setTimezone(source.getTimezone());
        copy.setResolveChainAndDeepRedirects(source.isResolveChainAndDeepRedirects());
        copy.setServerPortalUrl(source.getServerPortalUrl());
        return copy;
    }
}
