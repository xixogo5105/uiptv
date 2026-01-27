package com.uiptv.model;

import lombok.Data;

@Data
public class PlayerResponse {
    private String url;
    private String drmType;
    private String drmLicenseUrl;
    private String clearKeysJson;
    private String inputstreamaddon;
    private String manifestType;

    public PlayerResponse(String url) {
        this.url = url;
    }

    public void setFromChannel(Channel channel) {
        this.drmType = channel.getDrmType();
        this.drmLicenseUrl = channel.getDrmLicenseUrl();
        this.clearKeysJson = channel.getClearKeysJson();
        this.inputstreamaddon = channel.getInputstreamaddon();
        this.manifestType = channel.getManifestType();
    }

    public void setFromBookmark(Bookmark bookmark) {
        this.drmType = bookmark.getDrmType();
        this.drmLicenseUrl = bookmark.getDrmLicenseUrl();
        this.clearKeysJson = bookmark.getClearKeysJson();
        this.inputstreamaddon = bookmark.getInputstreamaddon();
        this.manifestType = bookmark.getManifestType();
    }
}
