package com.uiptv.model;


import com.uiptv.api.JsonCompliant;
import com.uiptv.util.AccountType;
import com.uiptv.util.StringUtils;
import org.json.JSONPropertyIgnore;

import java.io.Serializable;
import java.util.*;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.*;

public class Account implements Serializable, JsonCompliant {


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
    private boolean pauseCaching;
    private AccountType type = STALKER_PORTAL;

    public Account(String accountName, String username, String password, String url, String macAddress, String macAddressList, String serialNumber, String deviceId1, String deviceId2, String signature, AccountType type, String epg, String m3u8Path, boolean pauseCaching) {
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
    }

    public boolean isPauseCaching() {
        return pauseCaching;
    }

    public void setPauseCaching(boolean pauseCaching) {
        this.pauseCaching = pauseCaching;
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

    public String getServerPortalUrl() {
        return serverPortalUrl;
    }

    public void setServerPortalUrl(String serverPortalUrl) {
        this.serverPortalUrl = serverPortalUrl;
    }

    public AccountAction getAction() {
        return action;
    }

    public void setAction(AccountAction action) {
        this.action = action;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getMacAddressList() {
        return macAddressList;
    }

    public void setMacAddressList(String macAddressList) {
        this.macAddressList = macAddressList;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getDeviceId1() {
        return deviceId1;
    }

    public void setDeviceId1(String deviceId1) {
        this.deviceId1 = deviceId1;
    }

    public String getDeviceId2() {
        return deviceId2;
    }

    public void setDeviceId2(String deviceId2) {
        this.deviceId2 = deviceId2;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getEpg() {
        return epg;
    }

    public void setEpg(String epg) {
        this.epg = epg;
    }

    public String getM3u8Path() {
        return m3u8Path;
    }

    public void setM3u8Path(String m3u8Path) {
        this.m3u8Path = m3u8Path;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return pauseCaching == account.pauseCaching && Objects.equals(serverPortalUrl, account.serverPortalUrl) && action == account.action && Objects.equals(accountName, account.accountName) && Objects.equals(username, account.username) && Objects.equals(password, account.password) && Objects.equals(url, account.url) && Objects.equals(macAddress, account.macAddress) && Objects.equals(macAddressList, account.macAddressList) && Objects.equals(serialNumber, account.serialNumber) && Objects.equals(deviceId1, account.deviceId1) && Objects.equals(deviceId2, account.deviceId2) && Objects.equals(signature, account.signature) && Objects.equals(epg, account.epg) && Objects.equals(m3u8Path, account.m3u8Path) && Objects.equals(dbId, account.dbId) && Objects.equals(token, account.token) && type == account.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverPortalUrl, action, accountName, username, password, url, macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature, epg, m3u8Path, dbId, token, pauseCaching, type);
    }

    @Override
    public String toString() {
        return "Account{" +
                "serverPortalUrl='" + serverPortalUrl + '\'' +
                ", action=" + action +
                ", accountName='" + accountName + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", url='" + url + '\'' +
                ", macAddress='" + macAddress + '\'' +
                ", macAddressList='" + macAddressList + '\'' +
                ", serialNumber='" + serialNumber + '\'' +
                ", deviceId1='" + deviceId1 + '\'' +
                ", deviceId2='" + deviceId2 + '\'' +
                ", signature='" + signature + '\'' +
                ", epg='" + epg + '\'' +
                ", m3u8Path='" + m3u8Path + '\'' +
                ", dbId='" + dbId + '\'' +
                ", token='" + token + '\'' +
                ", pauseCaching='" + (isPauseCaching() ? "1" : "0") + '\'' +
                ", type='" + type.name() + '\'' +
                '}';
    }

    @Override
    public String toJson() {
        return "{" +
                "        \"dbId\":\"" + dbId + "\"" +
                ",        \"serverPortalUrl\":\"" + safeJson(serverPortalUrl) + "\"" +
                ",        \"action\":\"" + safeJson(action.name()) + "\"" +
                ",         \"token\":\"" + safeJson(token) + "\"" +
                ",         \"accountName\":\"" + safeJson(accountName) + "\"" +
                ",         \"username\":\"" + safeJson(username) + "\"" +
                ",         \"password\":\"" + safeJson(password) + "\"" +
                ",         \"url\":\"" + safeJson(url) + "\"" +
                ",         \"macAddress\":\"" + safeJson(macAddress) + "\"" +
                ",         \"macAddressList\":\"" + safeJson(macAddressList) + "\"" +
                ",         \"serialNumber\":\"" + safeJson(serialNumber) + "\"" +
                ",         \"deviceId1\":\"" + safeJson(deviceId1) + "\"" +
                ",         \"deviceId2\":\"" + safeJson(deviceId2) + "\"" +
                ",         \"signature\":\"" + safeJson(signature) + "\"" +
                ",         \"accountType\":\"" + safeJson(type.name()) + "\"" +
                ",         \"type\":\"" + safeJson(type.name()) + "\"" +
                ",         \"epg\":\"" + safeJson(epg) + "\"" +
                ",         \"m3u8Path\":\"" + safeJson(m3u8Path) + "\"" +
                ",         \"pauseCaching\":\"" + (isPauseCaching() ? "1" : "0") + "\"" +
                "}";
    }
}
