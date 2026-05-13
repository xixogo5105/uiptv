package com.uiptv.ui;

public class UpdateInfo {
    private final String version;
    private final String url;
    private final String description;

    public UpdateInfo(String version, String url, String description) {
        this.version = version;
        this.url = url;
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }
}