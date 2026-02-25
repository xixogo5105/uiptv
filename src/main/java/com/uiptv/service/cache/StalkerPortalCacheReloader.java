package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.HandshakeService;
import com.uiptv.shared.Pagination;
import com.uiptv.util.FetchAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class StalkerPortalCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) {
            log(logger, "Handshake failed for: " + account.getAccountName());
            return;
        }

        if (account.getAction() == itv) {
            reloadLive(account, logger);
            cacheVodAndSeriesCategoriesOnly(account, logger);
            return;
        }

        if (account.getAction() == vod || account.getAction() == series) {
            List<Category> categories = CategoryService.getInstance().get(account, false, logger);
            saveVodOrSeriesCategories(account, categories);
            log(logger, "Found Categories " + categories.size());
            log(logger, categories.size() + " Categories & 0 Channels saved Successfully \u2713");
        }
    }

    private void reloadLive(Account account, LoggerCallback logger) {
        String jsonCategories = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
        List<Category> officialCategories = CategoryService.getInstance().parseCategories(jsonCategories, false)
                .stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .collect(Collectors.toList());
        Map<String, Category> officialCategoryMap = officialCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));

        if (officialCategories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return;
        }
        log(logger, "Found Categories " + officialCategories.size());

        String jsonChannels = fetchAllStalkerChannelsJson(account);
        List<Channel> allChannels = Collections.emptyList();
        if (isNotBlank(jsonChannels)) {
            try {
                allChannels = ChannelService.getInstance().parseItvChannels(jsonChannels, false);
            } catch (Exception e) {
                log(logger, "Failed to parse channels from get_all_channels: " + e.getMessage());
            }
        }

        if (allChannels.isEmpty()) {
            log(logger, "No channels returned by get_all_channels. Trying last-resort category-by-category fetch.");
            allChannels = fetchAllChannelsByCategoryLastResort(account, officialCategories, logger);
            if (allChannels.isEmpty()) {
                log(logger, "No channels found. Keeping existing cache.");
                return;
            }
            log(logger, "Last-resort fetch succeeded. Collected " + allChannels.size() + " channels.");
        }

        List<Channel> orphanedChannels = new ArrayList<>();
        Map<String, List<Channel>> matchedChannelsByCatId = new HashMap<>();
        for (Channel channel : allChannels) {
            if (isNotBlank(channel.getCategoryId()) && officialCategoryMap.containsKey(channel.getCategoryId())) {
                matchedChannelsByCatId.computeIfAbsent(channel.getCategoryId(), k -> new ArrayList<>()).add(channel);
            } else {
                orphanedChannels.add(channel);
            }
        }
        log(logger, "Found Channels " + allChannels.size() + ". Found " + orphanedChannels.size() + " Orphaned channels.");

        clearCache(account);

        List<Category> categoriesToSave = new ArrayList<>(officialCategories);
        if (!orphanedChannels.isEmpty()) {
            boolean uncategorizedExists = officialCategories.stream()
                    .anyMatch(c -> UNCATEGORIZED_ID.equals(c.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(c.getTitle()));
            if (!uncategorizedExists) {
                categoriesToSave.add(new Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0));
            }
        }

        CategoryDb.get().saveAll(categoriesToSave, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        Map<String, Category> savedCategoryMap = savedCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));

        for (Map.Entry<String, List<Channel>> entry : matchedChannelsByCatId.entrySet()) {
            Category category = savedCategoryMap.get(entry.getKey());
            if (category != null && category.getDbId() != null) {
                ChannelDb.get().saveAll(entry.getValue(), category.getDbId(), account);
            }
        }

        if (!orphanedChannels.isEmpty()) {
            Category uncategorizedCategory = savedCategoryMap.values().stream()
                    .filter(c -> UNCATEGORIZED_ID.equals(c.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(c.getTitle()))
                    .findFirst()
                    .orElse(null);
            if (uncategorizedCategory != null && uncategorizedCategory.getDbId() != null) {
                ChannelDb.get().saveAll(orphanedChannels, uncategorizedCategory.getDbId(), account);
            }
        }

        log(logger, savedCategories.size() + " Categories & " + allChannels.size() + " Channels saved Successfully \u2713");
    }

    private String fetchAllStalkerChannelsJson(Account account) {
        List<Map<String, String>> attempts = List.of(
                getAllChannelsParams(null, null),
                getAllChannelsParams(0, 99999),
                getAllChannelsParams(1, 99999)
        );

        for (Map<String, String> params : attempts) {
            String json = FetchAPI.fetch(params, account);
            if (isBlank(json)) {
                continue;
            }
            try {
                List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);
                if (!channels.isEmpty()) {
                    return json;
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private List<Channel> fetchAllChannelsByCategoryLastResort(Account account, List<Category> categories, LoggerCallback logger) {
        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category == null || isBlank(category.getCategoryId())) {
                continue;
            }

            List<Channel> channelsForCategory = fetchStalkerCategoryChannelsLastResort(account, category.getCategoryId(), logger);
            for (Channel channel : channelsForCategory) {
                if (channel == null || isBlank(channel.getChannelId())) {
                    continue;
                }
                if (isBlank(channel.getCategoryId())) {
                    channel.setCategoryId(category.getCategoryId());
                }
                uniqueChannels.putIfAbsent(channel.getChannelId(), channel);
            }
        }
        return new ArrayList<>(uniqueChannels.values());
    }

    private List<Channel> fetchStalkerCategoryChannelsLastResort(Account account, String categoryId, LoggerCallback logger) {
        List<Channel> channels = fetchStalkerCategoryChannelsFromPage(account, categoryId, 0, logger);
        if (!channels.isEmpty()) {
            return channels;
        }
        return fetchStalkerCategoryChannelsFromPage(account, categoryId, 1, logger);
    }

    private List<Channel> fetchStalkerCategoryChannelsFromPage(Account account, String categoryId, int startPage, LoggerCallback logger) {
        List<Channel> aggregated = new ArrayList<>();
        int maxAdditionalPages = 2;

        for (int page = startPage; page <= startPage + maxAdditionalPages; page++) {
            String json = FetchAPI.fetch(ChannelService.getChannelOrSeriesParams(categoryId, page, itv, null, null), account);
            if (isBlank(json)) {
                break;
            }

            try {
                if (page == startPage) {
                    Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
                    if (pagination != null) {
                        maxAdditionalPages = Math.max(pagination.getPageCount() + 1, 2);
                    }
                }

                List<Channel> pageChannels = ChannelService.getInstance().parseItvChannels(json, false);
                if (pageChannels.isEmpty()) {
                    break;
                }
                aggregated.addAll(pageChannels);
            } catch (Exception e) {
                log(logger, "Last-resort fetch failed for category " + categoryId + " at page " + page + ": " + e.getMessage());
                break;
            }
        }

        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        for (Channel channel : aggregated) {
            if (channel == null || isBlank(channel.getChannelId())) {
                continue;
            }
            uniqueChannels.putIfAbsent(channel.getChannelId(), channel);
        }
        return new ArrayList<>(uniqueChannels.values());
    }
}
