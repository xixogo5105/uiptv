package com.uiptv.model;


import com.uiptv.shared.BaseJson;
import com.uiptv.util.AccountType;
import com.uiptv.util.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.json.JSONPropertyIgnore;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.StringUtils.SPACE;
import static com.uiptv.util.StringUtils.isNotBlank;

@Data
@NoArgsConstructor()
@EqualsAndHashCode(callSuper = false)
public class Account extends BaseJson {

    @SuppressWarnings("java:S115")
    public enum AccountAction {
        itv,
        vod,
        series
    }

    public static final Set<AccountAction> NOT_LIVE_TV_CHANNELS = Collections.unmodifiableSet(EnumSet.of(AccountAction.vod, AccountAction.series));
    public static final Set<AccountType> VOD_AND_SERIES_SUPPORTED = Collections.unmodifiableSet(EnumSet.of(STALKER_PORTAL, XTREME_API));
    public static final Set<AccountType> CACHE_SUPPORTED = Collections.unmodifiableSet(EnumSet.of(STALKER_PORTAL, XTREME_API, M3U8_URL, M3U8_LOCAL));
    public static final Set<AccountType> PRE_DEFINED_URLS = Collections.unmodifiableSet(EnumSet.of(RSS_FEED, M3U8_URL, M3U8_LOCAL));
    public static final String LINE_SEPARATOR = "\n\r";
    private String serverPortalUrl;
    private AccountAction action = itv;
    private String accountName;
    private String username;
    private String password;
    private String xtremeCredentialsJson;
    private String url;
    private String macAddress;
    private String macAddressList;
    private String serialNumber;
    private String deviceId1;
    private String deviceId2;
    private String signature;
    private String epg;
    private String m3u8Path;
    private String dbId;
    private String token;
    private boolean pinToTop;
    private boolean resolveChainAndDeepRedirects;
    private AccountType type = STALKER_PORTAL;
    private String httpMethod = "GET";
    private String timezone = "Europe/London";

    @SuppressWarnings("java:S107")
    public Account(String accountName, String username, String password, String url, String macAddress, String macAddressList, String serialNumber, String deviceId1, String deviceId2, String signature, AccountType type, String epg, String m3u8Path, boolean pinToTop) {
        this.accountName = accountName;
        this.username = username;
        this.password = password;
        this.url = url;
        this.macAddress = macAddress;
        this.serialNumber = serialNumber;
        this.deviceId1 = deviceId1;
        this.deviceId2 = deviceId2;
        this.signature = signature;
        this.type = type;
        this.epg = epg;
        this.m3u8Path = m3u8Path;
        Map<String, String> macMap = new HashMap<>();
        if (isNotBlank(macAddress)) macMap.put(macAddress.toLowerCase(), macAddress.trim());
        if (isNotBlank(macAddressList)) {
            Arrays.stream(macAddressList.split(","))
                    .filter(StringUtils::isNotBlank)
                    .forEach(m -> macMap.put(m.toLowerCase(), m.replace(SPACE, "")));
        }
        this.macAddressList = String.join(",", macMap.values());
        this.pinToTop = pinToTop;
    }

    @JSONPropertyIgnore
    public boolean isConnected() {
        if (type != STALKER_PORTAL) return true;
        return isNotBlank(token);
    }

    @JSONPropertyIgnore
    public boolean isNotConnected() {
        return !isConnected();
    }
}
