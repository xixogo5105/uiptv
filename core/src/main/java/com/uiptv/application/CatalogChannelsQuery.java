package com.uiptv.application;

public record CatalogChannelsQuery(String accountId, CatalogMode mode, String categoryId, String movieId) {
}
