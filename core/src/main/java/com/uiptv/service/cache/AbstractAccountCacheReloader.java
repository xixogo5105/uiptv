package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.AccountDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.ContentFilterService;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.util.AccountType;
import com.uiptv.util.M3U8Parser;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.M3U8Parser.parseChannelPathM3U8;
import static com.uiptv.util.M3U8Parser.parseChannelSourceM3U8;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

abstract class AbstractAccountCacheReloader implements AccountCacheReloader {
    private static final String ALL_CATEGORY = CategoryType.ALL.displayName();
    protected static final String UNCATEGORIZED_ID = CategoryType.UNCATEGORIZED.identifier();
    protected static final String UNCATEGORIZED_NAME = CategoryType.UNCATEGORIZED.displayName();

    protected void clearCache(Account account) {
        String existingPortalUrl = account != null ? account.getServerPortalUrl() : "";
        ConfigurationService.getInstance().clearCache(account);
        // During cache reloads we intentionally keep the discovered Stalker API endpoint.
        // Otherwise clearCache() wipes it from Account table and later calls can fail with host-less requests.
        if (account != null
                && account.getType() == AccountType.STALKER_PORTAL
                && isNotBlank(existingPortalUrl)) {
            account.setServerPortalUrl(existingPortalUrl);
            AccountDb.get().saveServerPortalUrl(account);
        }
    }

    protected void log(LoggerCallback logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    protected boolean shouldApplyCategoryCensoring() {
        Configuration configuration = ConfigurationService.getInstance().read();
        return isFilteringActive(configuration) && isNotBlank(configuration.getFilterCategoriesList());
    }

    protected boolean shouldApplyChannelCensoring() {
        Configuration configuration = ConfigurationService.getInstance().read();
        return isFilteringActive(configuration) && isNotBlank(configuration.getFilterChannelsList());
    }

    private boolean isFilteringActive(Configuration configuration) {
        return configuration != null && !configuration.isPauseFiltering();
    }

    protected List<Category> applyCategoryCensoring(List<Category> categories, boolean applyCensoring) {
        return applyCensoring ? ContentFilterService.getInstance().filterCategories(categories) : categories;
    }

    protected List<Channel> applyChannelCensoring(List<Channel> channels, boolean applyCensoring) {
        return applyCensoring ? ContentFilterService.getInstance().filterChannels(channels) : channels;
    }

    protected static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    protected static Map<String, String> getAllChannelsParams(Integer page, Integer perPage) {
        final Map<String, String> params = new HashMap<>();
        params.put("type", "itv");
        params.put("action", "get_all_channels");
        if (page != null) {
            params.put("p", String.valueOf(page));
        }
        if (perPage != null) {
            params.put("per_page", String.valueOf(perPage));
        }
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    protected CensoringSummary cacheVodAndSeriesCategoriesOnly(Account account, LoggerCallback logger) {
        if (account.getType() != AccountType.STALKER_PORTAL && account.getType() != AccountType.XTREME_API) {
            return CensoringSummary.empty();
        }
        Account.AccountAction original = account.getAction();
        boolean applyCategoryCensoring = shouldApplyCategoryCensoring();
        CensoringSummary summary = CensoringSummary.empty();
        try {
            for (Account.AccountAction mode : List.of(vod, series)) {
                account.setAction(mode);
                try {
                    List<Category> rawCategories = CategoryService.getInstance().get(account, false, logger);
                    if (rawCategories == null) {
                        rawCategories = List.of();
                    }
                    List<Category> categories = applyCategoryCensoring(rawCategories, applyCategoryCensoring);
                    summary = summary.addCategories(censoredItemCount(rawCategories.size(), categories.size(), applyCategoryCensoring));
                    saveVodOrSeriesCategories(account, categories);
                } catch (Exception e) {
                    log(logger, "Global " + mode.name().toUpperCase() + " category list failed: " + shortReason(e));
                }
            }
        } finally {
            account.setAction(original);
        }
        return summary;
    }

    private String shortReason(Exception e) {
        if (e == null) {
            return "unknown error";
        }
        String msg = e.getMessage();
        if (msg == null && e.getCause() != null) {
            msg = e.getCause().getMessage();
        }
        if (isBlank(msg)) {
            return e.getClass().getSimpleName();
        }
        return msg;
    }

    protected void saveVodOrSeriesCategories(Account account, List<Category> categories) {
        List<Category> normalizedCategories = normalizeCategoriesByTitle(categories).categories();
        if (account.getAction() == vod) {
            VodCategoryDb.get().saveAll(normalizedCategories, account);
        } else if (account.getAction() == series) {
            SeriesCategoryDb.get().saveAll(normalizedCategories, account);
        }
    }

    protected CategoryNormalization normalizeCategoriesByTitle(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return new CategoryNormalization(List.of(), Map.of());
        }

        Map<String, Category> canonicalByKey = new LinkedHashMap<>();
        Map<String, String> canonicalCategoryIdByOriginalId = new HashMap<>();
        for (Category category : categories) {
            if (category == null) {
                continue;
            }
            String categoryKey = categoryComparisonKey(category);
            Category canonical = canonicalByKey.computeIfAbsent(categoryKey, ignored -> category);
            if (isNotBlank(category.getCategoryId()) && isNotBlank(canonical.getCategoryId())) {
                canonicalCategoryIdByOriginalId.put(category.getCategoryId(), canonical.getCategoryId());
            }
        }
        return new CategoryNormalization(new ArrayList<>(canonicalByKey.values()), canonicalCategoryIdByOriginalId);
    }

    protected String canonicalCategoryId(String categoryId, Map<String, String> canonicalCategoryIdByOriginalId) {
        if (isBlank(categoryId)) {
            return categoryId;
        }
        return canonicalCategoryIdByOriginalId.getOrDefault(categoryId, categoryId);
    }

    protected List<Channel> mergeChannelsCaseInsensitive(List<Channel> existingChannels, List<Channel> channelsToAdd) {
        if ((existingChannels == null || existingChannels.isEmpty()) && (channelsToAdd == null || channelsToAdd.isEmpty())) {
            return List.of();
        }

        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        addChannelsCaseInsensitive(uniqueChannels, existingChannels);
        addChannelsCaseInsensitive(uniqueChannels, channelsToAdd);
        return new ArrayList<>(uniqueChannels.values());
    }

    protected String categoryLookupKey(String categoryTitle) {
        return normalizeCaseInsensitiveKey(categoryTitle);
    }

    protected String normalizeCaseInsensitiveKey(String value) {
        return isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    protected Set<String> categoryIds(List<Category> categories) {
        Set<String> ids = new LinkedHashSet<>();
        if (categories == null) {
            return ids;
        }
        for (Category category : categories) {
            if (category != null && isNotBlank(category.getCategoryId())) {
                ids.add(category.getCategoryId());
            }
        }
        return ids;
    }

    protected List<Channel> retainChannelsForVisibleCategories(List<Channel> channels,
                                                               Set<String> knownCategoryIds,
                                                               Set<String> visibleCategoryIds,
                                                               Map<String, String> canonicalCategoryIdByOriginalId) {
        if (channels == null || channels.isEmpty()
                || knownCategoryIds == null || knownCategoryIds.isEmpty()
                || visibleCategoryIds == null || knownCategoryIds.equals(visibleCategoryIds)) {
            return channels == null ? List.of() : channels;
        }

        List<Channel> retained = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            String categoryId = canonicalCategoryId(channel.getCategoryId(), canonicalCategoryIdByOriginalId);
            if (isBlank(categoryId) || !knownCategoryIds.contains(categoryId) || visibleCategoryIds.contains(categoryId)) {
                retained.add(channel);
            }
        }
        return retained;
    }

    protected int censoredItemCount(int rawCount, int filteredCount, boolean censoringEnabled) {
        return censoringEnabled ? Math.max(0, rawCount - filteredCount) : 0;
    }

    protected boolean wasEverythingRemovedByActiveCensoring(int rawCount, int filteredCount, boolean censoringEnabled) {
        return censoringEnabled && rawCount > 0 && filteredCount == 0;
    }

    protected void logKeepingExistingCacheAfterFullCensoring(LoggerCallback logger, String itemType) {
        log(logger, "All " + itemType + " removed by active censoring. Keeping existing cache.");
    }

    protected int uniqueChannelCount(Map<String, List<Channel>> channelsByCategory) {
        if (channelsByCategory == null || channelsByCategory.isEmpty()) {
            return 0;
        }
        Set<String> channelKeys = new LinkedHashSet<>();
        for (List<Channel> channels : channelsByCategory.values()) {
            if (channels == null) {
                continue;
            }
            for (Channel channel : channels) {
                if (channel != null) {
                    channelKeys.add(channelComparisonKey(channel));
                }
            }
        }
        return channelKeys.size();
    }

    protected void logCensoringSummary(LoggerCallback logger, CensoringSummary summary) {
        if (summary == null) {
            return;
        }
        if (summary.categories() > 0) {
            log(logger, "Censored Categories " + summary.categories());
        }
        if (summary.channels() > 0) {
            log(logger, "Censored Channels " + summary.channels());
        }
    }

    private void addChannelsCaseInsensitive(Map<String, Channel> uniqueChannels, List<Channel> channels) {
        if (channels == null) {
            return;
        }
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            uniqueChannels.putIfAbsent(channelComparisonKey(channel), channel);
        }
    }

    private String categoryComparisonKey(Category category) {
        if (category == null) {
            return "";
        }
        String titleKey = normalizeCaseInsensitiveKey(category.getTitle());
        if (isNotBlank(titleKey)) {
            return titleKey;
        }
        String categoryIdKey = normalizeCaseInsensitiveKey(category.getCategoryId());
        if (isNotBlank(categoryIdKey)) {
            return categoryIdKey;
        }
        return UUID.randomUUID().toString();
    }

    protected String channelComparisonKey(Channel channel) {
        String channelIdKey = normalizeCaseInsensitiveKey(channel.getChannelId());
        if (isNotBlank(channelIdKey)) {
            return "id:" + channelIdKey;
        }
        String nameKey = normalizeCaseInsensitiveKey(channel.getName());
        if (isNotBlank(nameKey)) {
            return "name:" + nameKey;
        }
        String cmdKey = normalizeCaseInsensitiveKey(channel.getCmd());
        if (isNotBlank(cmdKey)) {
            return "cmd:" + cmdKey;
        }
        return "fallback:" + UUID.randomUUID();
    }

    protected List<Channel> m3u8Channels(String category, Account account) {
        Set<Channel> channels = new LinkedHashSet<>();
        Set<PlaylistEntry> m3uCategories = loadM3uCategories(account);
        boolean hasOtherCategories = m3uCategories.size() >= 2;
        for (PlaylistEntry entry : loadM3uEntries(account)) {
            if (matchesM3uCategory(entry, category, hasOtherCategories)) {
                channels.add(toChannel(entry));
            }
        }

        return channels.stream().toList();
    }

    protected Set<PlaylistEntry> loadM3uCategories(Account account) {
        String path = account.getM3u8Path();
        if (isBlank(path)) {
            return new LinkedHashSet<>();
        }
        return account.getType() == AccountType.M3U8_URL
                ? M3U8Parser.parseSourceCategory(path)
                : M3U8Parser.parsePathCategory(path);
    }

    protected List<PlaylistEntry> loadM3uEntries(Account account) {
        String path = account.getM3u8Path();
        if (isBlank(path)) {
            return List.of();
        }
        return account.getType() == AccountType.M3U8_URL
                ? parseChannelSourceM3U8(path)
                : parseChannelPathM3U8(path);
    }

    private boolean matchesM3uCategory(PlaylistEntry entry, String category, boolean hasOtherCategories) {
        String groupTitle = entry.getGroupTitle();
        String trimmedGroupTitle = groupTitle == null ? "" : groupTitle.trim();
        if (category.equalsIgnoreCase(ALL_CATEGORY)) {
            return true;
        }
        if (category.equalsIgnoreCase(UNCATEGORIZED_NAME)) {
            return hasOtherCategories && (trimmedGroupTitle.isEmpty() || trimmedGroupTitle.equalsIgnoreCase(UNCATEGORIZED_NAME));
        }
        return trimmedGroupTitle.equalsIgnoreCase(category)
                || (entry.getId() != null && entry.getId().equalsIgnoreCase(category));
    }

    protected Channel toChannel(PlaylistEntry entry) {
        String channelId = entry.getId();
        if (isBlank(channelId)) {
            channelId = UUID.nameUUIDFromBytes((entry.getTitle() + entry.getPlaylistEntry()).getBytes()).toString();
        }
        return new Channel(channelId, entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null,
                entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(),
                entry.getInputstreamaddon(), entry.getManifestType());
    }

    protected record CensoringSummary(int categories, int channels) {
        static CensoringSummary empty() {
            return new CensoringSummary(0, 0);
        }

        CensoringSummary add(CensoringSummary other) {
            if (other == null) {
                return this;
            }
            return new CensoringSummary(categories + other.categories, channels + other.channels);
        }

        CensoringSummary addCategories(int value) {
            return value <= 0 ? this : new CensoringSummary(categories + value, channels);
        }

        CensoringSummary addChannels(int value) {
            return value <= 0 ? this : new CensoringSummary(categories, channels + value);
        }
    }

    protected record CategoryNormalization(List<Category> categories, Map<String, String> canonicalCategoryIdByOriginalId) {
    }
}
