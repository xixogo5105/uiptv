package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.Pagination;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.ui.RssParser;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.M3U8Parser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.M3U8Parser.parseChannelPathM3U8;
import static com.uiptv.util.M3U8Parser.parseChannelUrlM3U8;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class CacheServiceImpl implements CacheService {

    private static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    private static Map<String, String> getAllChannelsParams(Integer page, Integer perPage) {
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

    @Override
    public void reloadCache(Account account, LoggerCallback logger) throws IOException {
        if (account.getType() == STALKER_PORTAL) {
            reloadAllChannelsAndCategories(account, logger);
        } else {
            reloadChannelsForOtherTypes(account, logger);
        }
    }

    @Override
    public boolean verifyMacAddress(Account account, String macAddress) {
        if (account == null) {
            return false;
        }

        String originalMac = account.getMacAddress();
        try {
            account.setMacAddress(macAddress);
            HandshakeService.getInstance().connect(account);

            if (account.isNotConnected()) {
                return false;
            }

            String jsonCategories = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
            return !CategoryService.getInstance().parseCategories(jsonCategories, false).isEmpty();
        } catch (Exception e) {
            return false;
        } finally {
            account.setMacAddress(originalMac);
        }
    }

    @Override
    public int getChannelCountForAccount(String accountId) {
        return ChannelDb.get().getChannelCountForAccount(accountId);
    }

    private void reloadAllChannelsAndCategories(Account account, LoggerCallback logger) {
        logger.log("Performing handshake for: " + account.getAccountName());
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) {
            logger.log("Handshake failed for: " + account.getAccountName());
            return;
        }
        logger.log("Handshake successful.");

        // 1. Fetch official categories
        logger.log("Fetching official categories for: " + account.getAccountName());
        String jsonCategories = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
        List<Category> officialCategories = CategoryService.getInstance().parseCategories(jsonCategories, false);
        officialCategories = officialCategories.stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .collect(Collectors.toList());
        Map<String, Category> officialCategoryMap = officialCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));

        // 2. Fetch all channels
        logger.log("Fetching all channels for: " + account.getAccountName());
        String jsonChannels = fetchAllStalkerChannelsJson(account, logger);
        List<Channel> allChannels = Collections.emptyList();

        if (isNotBlank(jsonChannels)) {
            try {
                logger.log("Received response for channels.");
                allChannels = ChannelService.getInstance().parseItvChannels(jsonChannels, false);
                logger.log("Fetched " + allChannels.size() + " channels.");
            } catch (Exception e) {
                logger.log("Error processing channel data: " + e.getMessage());
            }
        } else {
            logger.log("No response or empty response from server for channels");
        }

        if (allChannels.isEmpty()) {
            logger.log("No channels returned by get_all_channels. Trying last-resort category-by-category fetch.");
            allChannels = fetchAllChannelsByCategoryLastResort(account, officialCategories, logger);
            if (allChannels.isEmpty()) {
                logger.log("Last-resort fetch also returned zero channels. Keeping existing cache.");
                return;
            }
            logger.log("Last-resort fetch succeeded. Collected " + allChannels.size() + " channels.");
        }

        // Clear cache only after successful fetch
        logger.log("Clearing cache for account: " + account.getAccountName());
        clearCache(account);
        logger.log("Cache cleared for account: " + account.getAccountName());

        // 3. Partition and group channels in a single pass
        List<Channel> unmatchedChannels = new ArrayList<>();
        Map<String, List<Channel>> matchedChannelsByCatId = new HashMap<>();
        for (Channel channel : allChannels) {
            if (isNotBlank(channel.getCategoryId()) && officialCategoryMap.containsKey(channel.getCategoryId())) {
                matchedChannelsByCatId.computeIfAbsent(channel.getCategoryId(), k -> new ArrayList<>()).add(channel);
            } else {
                unmatchedChannels.add(channel);
            }
        }

        List<Category> categoriesToSave = new ArrayList<>(officialCategories);

        // 4. Handle unmatched channels
        final String UNCATEGORIZED_ID = "uncategorized";
        final String UNCATEGORIZED_NAME = "Uncategorized";
        boolean hasUnmatched = !unmatchedChannels.isEmpty();

        if (hasUnmatched) {
            logger.log("Found " + unmatchedChannels.size() + " channels with no matching category.");
            boolean uncategorizedExists = officialCategories.stream()
                    .anyMatch(c -> UNCATEGORIZED_ID.equals(c.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(c.getTitle()));
            if (!uncategorizedExists) {
                categoriesToSave.add(new Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0));
            }
        }

        // 5. Save all categories
        CategoryDb.get().saveAll(categoriesToSave, account);

        // 6. Re-fetch to get all dbIds
        List<Category> finalSavedCategories = CategoryDb.get().getCategories(account);
        Map<String, Category> finalCategoryMap = finalSavedCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));

        // 7. Save matched channels
        for (Map.Entry<String, List<Channel>> entry : matchedChannelsByCatId.entrySet()) {
            Category category = finalCategoryMap.get(entry.getKey());
            if (category != null && category.getDbId() != null) {
                ChannelDb.get().saveAll(entry.getValue(), category.getDbId(), account);
            }
        }

        // 8. Save unmatched channels
        if (hasUnmatched) {
            Category uncategorizedCategory = finalCategoryMap.values().stream()
                    .filter(c -> UNCATEGORIZED_ID.equals(c.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(c.getTitle()))
                    .findFirst().orElse(null);

            if (uncategorizedCategory != null && uncategorizedCategory.getDbId() != null) {
                ChannelDb.get().saveAll(unmatchedChannels, uncategorizedCategory.getDbId(), account);
                logger.log("Saved " + unmatchedChannels.size() + " channels to '" + UNCATEGORIZED_NAME + "' category.");
            } else {
                logger.log("Error: Could not save unmatched channels because '" + UNCATEGORIZED_NAME + "' category was not found after saving.");
            }
        }

        logger.log("Parsing complete. Found " + finalSavedCategories.size() + " categories and " + allChannels.size() + " channels saved.");
    }

    private String fetchAllStalkerChannelsJson(Account account, LoggerCallback logger) {
        List<Map<String, String>> attempts = List.of(
                getAllChannelsParams(null, null),
                getAllChannelsParams(0, 99999),
                getAllChannelsParams(1, 99999)
        );

        for (int i = 0; i < attempts.size(); i++) {
            String json = FetchAPI.fetch(attempts.get(i), account);
            if (isBlank(json)) {
                continue;
            }
            try {
                List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);
                if (!channels.isEmpty()) {
                    if (i > 0) {
                        logger.log("get_all_channels succeeded with fallback strategy #" + (i + 1));
                    }
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

            String categoryId = category.getCategoryId();
            logger.log("Last-resort fetch for category: " + category.getTitle() + " (id=" + categoryId + ")");
            List<Channel> channelsForCategory = fetchStalkerCategoryChannelsLastResort(account, categoryId, logger);

            for (Channel channel : channelsForCategory) {
                if (channel == null || isBlank(channel.getChannelId())) {
                    continue;
                }
                if (isBlank(channel.getCategoryId())) {
                    channel.setCategoryId(categoryId);
                }
                uniqueChannels.putIfAbsent(channel.getChannelId(), channel);
            }

            logger.log("Last-resort fetched " + channelsForCategory.size() + " channels for category id=" + categoryId);
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
                if (page == startPage) {
                    logger.log("Last-resort category fetch returned empty response for category " + categoryId + " at page " + page);
                }
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
                logger.log("Last-resort category fetch parse error for category " + categoryId + " at page " + page + ": " + e.getMessage());
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

    private void reloadChannelsForOtherTypes(Account account, LoggerCallback logger) throws IOException {
        logger.log("Fetching categories for: " + account.getAccountName());
        List<Category> categories = CategoryService.getInstance().get(account, false); // This fetches from the source, not DB
        logger.log("Found " + categories.size() + " categories.");

        if (categories.isEmpty()) {
            logger.log("No categories found. Keeping existing cache.");
            return;
        }

        Map<String, List<Channel>> channelsMap = new HashMap<>();
        int totalChannels = 0;

        for (Category category : categories) {
            if (account.getType() == XTREME_API && "All".equalsIgnoreCase(category.getTitle())) {
                continue;
            }
            String categoryId = account.getType() == XTREME_API ? category.getCategoryId() : category.getTitle();
            List<Channel> channels = fetchChannelsForCategory(categoryId, account, logger);
            if (!channels.isEmpty()) {
                channelsMap.put(categoryId, channels);
                totalChannels += channels.size();
            }
        }

        if (totalChannels == 0) {
            logger.log("No channels found in any category. Keeping existing cache.");
            return;
        }

        // Clear cache only after successful fetch
        logger.log("Clearing cache for account: " + account.getAccountName());
        clearCache(account);
        logger.log("Cache cleared for account: " + account.getAccountName());

        // Save categories to get DB IDs
        CategoryDb.get().saveAll(categories, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account); // Re-fetch to get DB IDs

        for (Category savedCat : savedCategories) {
            String key = account.getType() == XTREME_API ? savedCat.getCategoryId() : savedCat.getTitle();
            List<Channel> channels = channelsMap.get(key);
            if (channels != null && !channels.isEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.getDbId(), account);
                logger.log("Saved " + channels.size() + " channels to database for category: " + savedCat.getTitle());
            }
        }
        logger.log("All channels reloaded for account: " + account.getAccountName());
    }

    private List<Channel> fetchChannelsForCategory(String categoryId, Account account, LoggerCallback logger) {
        List<Channel> channels = new ArrayList<>();
        try {
            if (Objects.requireNonNull(account.getType()) == AccountType.M3U8_LOCAL || account.getType() == AccountType.M3U8_URL) {
                logger.log("Fetching M3U8 channels from: " + account.getM3u8Path());
                channels.addAll(m3u8Channels(categoryId, account));
            } else if (account.getType() == AccountType.XTREME_API) {
                logger.log("Fetching Xtreme API channels for category: " + categoryId);
                channels.addAll(xtremeAPICategories(categoryId, account));
            } else if (account.getType() == AccountType.RSS_FEED) {
                logger.log("Fetching RSS channels from: " + account.getM3u8Path());
                channels.addAll(rssChannels(categoryId, account));
            } else {
                logger.log("Fetching Stalker Portal channels for category: " + categoryId);
                channels.addAll(ChannelService.getInstance().getStalkerPortalChOrSeries(categoryId, account, null, "0", null, null, false));
            }
            logger.log("Found " + channels.size() + " channels for category: " + categoryId);
        } catch (Exception e) {
            logger.log("Error fetching channels for category " + categoryId + ": " + e.getMessage());
        }
        return channels;
    }

    private List<Channel> xtremeAPICategories(String category, Account account) {
        return XtremeParser.parseChannels(category, account);
    }

    private List<Channel> m3u8Channels(String category, Account account) throws MalformedURLException {
        Set<Channel> channels = new LinkedHashSet<>();

        Set<PlaylistEntry> m3uCategories = account.getType() == AccountType.M3U8_URL
                ? M3U8Parser.parseUrlCategory(new URL(account.getM3u8Path()))
                : M3U8Parser.parsePathCategory(account.getM3u8Path());
        boolean hasOtherCategories = m3uCategories.size() >= 2;

        List<PlaylistEntry> m3uEntries = account.getType() == AccountType.M3U8_URL
                ? parseChannelUrlM3U8(new URL(account.getM3u8Path()))
                : parseChannelPathM3U8(account.getM3u8Path());

        m3uEntries.stream().filter(e -> {
            String gt = e.getGroupTitle();
            String gtTrim = gt == null ? "" : gt.trim();

            if (category.equalsIgnoreCase("All")) {
                return true;
            }

            if (category.equalsIgnoreCase("Uncategorized")) {
                if (!hasOtherCategories) {
                    return false;
                }
                return gtTrim.isEmpty() || gtTrim.equalsIgnoreCase("Uncategorized");
            }

            return gtTrim.equalsIgnoreCase(category) || (e.getId() != null && e.getId().equalsIgnoreCase(category));
        }).forEach(entry -> {
            String channelId = entry.getId();
            if (isBlank(channelId)) {
                channelId = UUID.nameUUIDFromBytes((entry.getTitle() + entry.getPlaylistEntry()).getBytes()).toString();
            }
            Channel c = new Channel(channelId, entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
            channels.add(c);
        });

        return channels.stream().toList();
    }

    private List<Channel> rssChannels(String category, Account account) throws MalformedURLException {
        Set<Channel> channels = new LinkedHashSet<>();
        List<PlaylistEntry> rssEntries = RssParser.parse(account.getM3u8Path());
        rssEntries.stream().filter(e -> category.equalsIgnoreCase("All") || e.getGroupTitle().equalsIgnoreCase(category) || e.getId().equalsIgnoreCase(category)).forEach(entry -> {
            Channel c = new Channel(entry.getId(), entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
            channels.add(c);
        });
        return channels.stream().toList();
    }
}
