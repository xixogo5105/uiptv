package com.uiptv.model;


import com.uiptv.api.JsonCompliant;

import java.io.Serializable;
import java.util.Objects;

import static com.uiptv.util.StringUtils.safeJson;

public class Bookmark implements Serializable, JsonCompliant {
    private String dbId, accountName, categoryTitle, channelId, channelName, cmd, serverPortalUrl;

    public Bookmark(String accountName, String categoryTitle, String channelId, String channelName, String cmd, String serverPortalUrl) {
        this.accountName = accountName;
        this.categoryTitle = categoryTitle;
        this.channelId = channelId;
        this.channelName = channelName;
        this.cmd = cmd;
        this.serverPortalUrl = serverPortalUrl;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getCategoryTitle() {
        return categoryTitle;
    }

    public void setCategoryTitle(String categoryTitle) {
        this.categoryTitle = categoryTitle;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getServerPortalUrl() {
        return serverPortalUrl;
    }

    public void setServerPortalUrl(String serverPortalUrl) {
        this.serverPortalUrl = serverPortalUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bookmark bookmark = (Bookmark) o;
        return Objects.equals(dbId, bookmark.dbId) && Objects.equals(accountName, bookmark.accountName) && Objects.equals(categoryTitle, bookmark.categoryTitle) && Objects.equals(channelId, bookmark.channelId) && Objects.equals(channelName, bookmark.channelName) && Objects.equals(cmd, bookmark.cmd) && Objects.equals(serverPortalUrl, bookmark.serverPortalUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbId, accountName, categoryTitle, channelId, channelName, cmd, serverPortalUrl);
    }

    @Override
    public String toString() {
        return "Bookmark{" +
                "dbId='" + dbId + '\'' +
                ", accountName='" + accountName + '\'' +
                ", categoryTitle='" + categoryTitle + '\'' +
                ", channelId='" + channelId + '\'' +
                ", channelName='" + channelName + '\'' +
                ", cmd='" + cmd + '\'' +
                ", serverPortalUrl='" + serverPortalUrl + '\'' +
                '}';
    }

    @Override
    public String toJson() {
        return "{" +
                "   \"dbId\": \"" + dbId + "\"" +
                ",  \"accountName\": \"" + safeJson(accountName) + "\"" +
                ",  \"categoryTitle\":\"" + safeJson(categoryTitle) + "\"" +
                ",  \"channelId\":\"" + safeJson(channelId) + "\"" +
                ",  \"channelName\":\"" + safeJson(channelName) + "\"" +
                ",  \"cmd\":\"" + safeJson(cmd) + "\"" +
                ",  \"serverPortalUrl\":\"" + safeJson(serverPortalUrl) + "\"" +
                "}";
    }
}

