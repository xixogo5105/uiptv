package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Bookmark extends BaseJson {
    private String dbId;
    private String accountName;
    private String categoryTitle;
    private String channelId;
    private String channelName;
    private String cmd;
    private String serverPortalUrl;
    private String categoryId;
    private Account.AccountAction accountAction; // Added field
    private String drmType;
    private String drmLicenseUrl;
    private String clearKeysJson;
    private String inputstreamaddon;
    private String manifestType;

    public Bookmark(String accountName, String categoryTitle, String channelId, String channelName, String cmd, String serverPortalUrl, String categoryId) {
        this.accountName = accountName;
        this.categoryTitle = categoryTitle;
        this.channelId = channelId;
        this.channelName = channelName;
        this.cmd = cmd;
        this.serverPortalUrl = serverPortalUrl;
        this.categoryId = categoryId;
    }

    public void setFromChannel(Channel channel) {
        this.setDrmType(channel.getDrmType());
        this.setDrmLicenseUrl(channel.getDrmLicenseUrl());
        this.setClearKeysJson(channel.getClearKeysJson());
        this.setManifestType(channel.getManifestType());
        this.setInputstreamaddon(channel.getInputstreamaddon());
    }
}
