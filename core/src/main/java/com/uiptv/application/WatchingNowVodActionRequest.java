package com.uiptv.application;

public record WatchingNowVodActionRequest(
        String accountId,
        String categoryId,
        String vodId,
        String vodName,
        String vodCmd,
        String vodLogo
) {
}
