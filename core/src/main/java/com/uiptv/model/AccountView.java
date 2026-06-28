package com.uiptv.model;

import com.uiptv.util.AccountType;

/**
 * Immutable account snapshot for read-only UI and playback routing.
 * Mutable {@link Account} remains the persistence/editing model.
 */
public record AccountView(
        String serverPortalUrl,
        String accountName,
        String username,
        String password,
        String xtremeCredentialsJson,
        String url,
        String macAddress,
        String macAddressList,
        String serialNumber,
        String deviceId1,
        String deviceId2,
        String signature,
        String epg,
        String m3u8Path,
        String dbId,
        String token,
        boolean pinToTop,
        boolean resolveChainAndDeepRedirects,
        AccountType type,
        String httpMethod,
        String timezone
) {
    public static AccountView from(Account account) {
        if (account == null) {
            return null;
        }
        return new AccountView(
                account.getServerPortalUrl(),
                account.getAccountName(),
                account.getUsername(),
                account.getPassword(),
                account.getXtremeCredentialsJson(),
                account.getUrl(),
                account.getMacAddress(),
                account.getMacAddressList(),
                account.getSerialNumber(),
                account.getDeviceId1(),
                account.getDeviceId2(),
                account.getSignature(),
                account.getEpg(),
                account.getM3u8Path(),
                account.getDbId(),
                account.getToken(),
                account.isPinToTop(),
                account.isResolveChainAndDeepRedirects(),
                account.getType(),
                account.getHttpMethod(),
                account.getTimezone()
        );
    }

    public Account toAccount(Account.AccountAction action) {
        Account mutable = new Account(
                accountName,
                username,
                password,
                url,
                macAddress,
                macAddressList,
                serialNumber,
                deviceId1,
                deviceId2,
                signature,
                type == null ? AccountType.STALKER_PORTAL : type,
                epg,
                m3u8Path,
                pinToTop
        );
        mutable.setServerPortalUrl(serverPortalUrl);
        mutable.setXtremeCredentialsJson(xtremeCredentialsJson);
        mutable.setDbId(dbId);
        mutable.setToken(token);
        mutable.setResolveChainAndDeepRedirects(resolveChainAndDeepRedirects);
        mutable.setHttpMethod(httpMethod == null || httpMethod.isBlank() ? "GET" : httpMethod);
        mutable.setTimezone(timezone == null || timezone.isBlank() ? "Europe/London" : timezone);
        mutable.setAction(action == null ? Account.AccountAction.itv : action);
        return mutable;
    }

    public AccountView withServerPortalUrl(String value) {
        return new AccountView(
                value,
                accountName,
                username,
                password,
                xtremeCredentialsJson,
                url,
                macAddress,
                macAddressList,
                serialNumber,
                deviceId1,
                deviceId2,
                signature,
                epg,
                m3u8Path,
                dbId,
                token,
                pinToTop,
                resolveChainAndDeepRedirects,
                type,
                httpMethod,
                timezone
        );
    }
}