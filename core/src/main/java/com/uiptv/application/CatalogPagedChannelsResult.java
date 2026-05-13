package com.uiptv.application;

import com.uiptv.model.Channel;

import java.util.List;

public record CatalogPagedChannelsResult(List<Channel> items, int nextPage, boolean hasMore, int apiOffset) {
}
