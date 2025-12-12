package com.uiptv.model;


import com.uiptv.shared.BaseJson;
import com.uiptv.util.AccountType;
import com.uiptv.util.StringUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.json.JSONPropertyIgnore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.*;

@Data
@ToString(exclude = {"password", "token", "signature"})
@NoArgsConstructor()
public class Account extends BaseJson {


    public enum AccountAction {
        itv,
        vod,
        series
    }

    public static final String LINE_SEPARATOR = "\n\r";
    private String serverPortalUrl;
    private AccountAction action = itv;
    private String accountName, username, password, url, macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature, epg, m3u8Path;
    private String dbId, token;
    private boolean pauseCaching, pinToTop;
    private AccountType type = STALKER_PORTAL;

    public Account(String accountName, String username, String password, String url, String macAddress, String macAddressList, String serialNumber, String deviceId1, String deviceId2, String signature, AccountType type, String epg, String m3u8Path, boolean pauseCaching, boolean pinToTop) {
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
        this.pauseCaching = pauseCaching;
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
