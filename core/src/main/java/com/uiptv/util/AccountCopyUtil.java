package com.uiptv.util;

import com.uiptv.model.Account;

public final class AccountCopyUtil {
    private AccountCopyUtil() {
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
