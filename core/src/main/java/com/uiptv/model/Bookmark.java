package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Bookmark extends BaseJson {
    private String dbId;
    private String accountName;
    private String categoryTitle;
    private String channelId;
    private String channelName;
    private String logo;
    private String cmd;
    private String serverPortalUrl;
    private String categoryId;
    private Account.AccountAction accountAction; // Added field
    private String drmType;
    private String drmLicenseUrl;
    private String clearKeysJson;
    private String inputstreamaddon;
    private String manifestType;
    private String categoryJson;
    private String channelJson;
    private String vodJson;
    private String seriesJson;

    public Bookmark(String accountName, String categoryTitle, String channelId, String channelName, String cmd, String serverPortalUrl, String categoryId) {
        this.accountName = accountName;
        this.categoryTitle = categoryTitle;
        this.channelId = channelId;
        this.channelName = channelName;
        this.cmd = cmd;
        this.serverPortalUrl = serverPortalUrl;
        this.categoryId = categoryId;
    }

    public static Bookmark fromJson(String value) {
        org.json.JSONObject json = new org.json.JSONObject(value);
        Bookmark bookmark = new Bookmark();
        bookmark.accountName = json.optString("accountName", "");
        bookmark.categoryTitle = json.optString("categoryTitle", "");
        bookmark.channelId = json.optString("channelId", "");
        bookmark.channelName = json.optString("channelName", "");
        bookmark.logo = json.optString("logo", "");
        bookmark.cmd = json.optString("cmd", "");
        bookmark.serverPortalUrl = json.optString("serverPortalUrl", "");
        bookmark.categoryId = json.optString("categoryId", "");
        bookmark.drmType = json.optString("drmType", "");
        bookmark.drmLicenseUrl = json.optString("drmLicenseUrl", "");
        bookmark.clearKeysJson = json.optString("clearKeysJson", "");
        bookmark.inputstreamaddon = json.optString("inputstreamaddon", "");
        bookmark.manifestType = json.optString("manifestType", "");
        bookmark.categoryJson = json.optString("categoryJson", "");
        bookmark.channelJson = json.optString("channelJson", "");
        bookmark.vodJson = json.optString("vodJson", "");
        bookmark.seriesJson = json.optString("seriesJson", "");
        String action = json.optString("accountAction", "itv");
        bookmark.accountAction = action.isBlank() ? Account.AccountAction.itv : Account.AccountAction.valueOf(action);
        return bookmark;
    }

    public void setFromChannel(Channel channel) {
        this.setLogo(channel.getLogo());
        this.setDrmType(channel.getDrmType());
        this.setDrmLicenseUrl(channel.getDrmLicenseUrl());
        this.setClearKeysJson(channel.getClearKeysJson());
        this.setManifestType(channel.getManifestType());
        this.setInputstreamaddon(channel.getInputstreamaddon());
    }
}
