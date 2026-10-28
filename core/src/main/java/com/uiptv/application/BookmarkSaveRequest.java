package com.uiptv.application;

public record BookmarkSaveRequest(
        String accountId,
        String categoryId,
        CatalogMode mode,
        String channelId,
        String channelName,
        String cmd,
        String logo,
        String drmType,
        String drmLicenseUrl,
        String clearKeysJson,
        String inputstreamaddon,
        String manifestType,
        String bookmarkCategoryId
) {
    public BookmarkSaveRequest(
            String accountId,
            String categoryId,
            CatalogMode mode,
            String channelId,
            String channelName,
            String cmd,
            String logo,
            String drmType,
            String drmLicenseUrl,
            String clearKeysJson,
            String inputstreamaddon,
            String manifestType
    ) {
        this(accountId, categoryId, mode, channelId, channelName, cmd, logo, drmType,
                drmLicenseUrl, clearKeysJson, inputstreamaddon, manifestType, "");
    }
}
