package com.uiptv.application;

import com.uiptv.model.Channel;

public record WatchingNowVodRow(
        String accountId,
        String accountName,
        String accountType,
        String categoryId,
        String vodId,
        String vodName,
        String vodLogo,
        String plot,
        String releaseDate,
        String rating,
        String duration,
        long updatedAt,
        Channel playItem
) {
}
