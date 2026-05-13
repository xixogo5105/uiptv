package com.uiptv.application;

public record PlaybackPlaylistRequest(
        String accountId,
        String categoryId,
        String channelId
) {
}
