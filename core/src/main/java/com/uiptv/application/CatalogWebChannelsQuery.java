package com.uiptv.application;

public record CatalogWebChannelsQuery(
        String accountId,
        CatalogMode mode,
        String categoryId,
        String movieId,
        int page,
        int pageSize,
        int prefetchPages,
        int apiOffset
) {
}
