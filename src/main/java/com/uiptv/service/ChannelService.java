package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.shared.Pagination;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.ui.RssParser;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.FetchAPI.nullSafeInteger;
import static com.uiptv.util.FetchAPI.nullSafeString;
import static com.uiptv.util.M3U8Parser.parseChannelPathM3U8;
import static com.uiptv.util.M3U8Parser.parseChannelUrlM3U8;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

@Slf4j
public class ChannelService {
    private static ChannelService instance;

    private ChannelService() {
    }

    public static synchronized ChannelService getInstance() {
        if (instance == null) {
            instance = new ChannelService();
        }
        return instance;
    }

    private static Map<String, String> getChannelOrSeriesParams(String category, int pageNumber, Account.AccountAction accountAction, String movieId, String seriesId) {
        final Map<String, String> params = new HashMap<>();
        params.put("type", accountAction.name());
        params.put("action", "get_ordered_list");
        params.put("genre", category);
        params.put("force_ch_link_check", "");
        params.put("fav", "0");
        params.put("sortby", "added");
        if (accountAction == series) {
            params.put("movie_id", isBlank(movieId) ? "0" : movieId);
            params.put("category", category);
            params.put("season_id", isBlank(seriesId) ? "0" : seriesId);
            params.put("episode_id", "0");
        }
        params.put("hd", "1");
        params.put("p", String.valueOf(pageNumber));
        params.put("per_page", "999");
        params.put("max_count", "0");
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    private static Map<String, String> getAllChannelsParams() {
        final Map<String, String> params = new HashMap<>();
        params.put("type", "itv");
        params.put("action", "get_all_channels");
        params.put("p", "1");
        params.put("per_page", "99999");
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    private static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    public void reloadAllChannelsAndCategories(Account account, LoggerCallback logger) {
        logger.log("Clearing cache for account: " + account.getAccountName());
        ConfigurationService.getInstance().clearCache(account);
        logger.log("Cache cleared for account: " + account.getAccountName());

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
        List<Category> officialCategories = CategoryService.getInstance().parseCategories(jsonCategories);
        Map<String, Category> officialCategoryMap = officialCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));

        // 2. Fetch all channels
        logger.log("Fetching all channels for: " + account.getAccountName());
        String jsonChannels = FetchAPI.fetch(getAllChannelsParams(), account);

        if (isNotBlank(jsonChannels)) {
            try {
                logger.log("Received response for channels.");
                List<Channel> allChannels = parseItvChannels(jsonChannels);
                List<Channel> censoredChannels = censor(allChannels);
                logger.log("Fetched " + censoredChannels.size() + " channels.");

                // 3. Partition channels
                List<Channel> unmatchedChannels = new ArrayList<>();
                List<Channel> matchedChannels = new ArrayList<>();
                for (Channel channel : censoredChannels) {
                    if (isNotBlank(channel.getCategoryId()) && officialCategoryMap.containsKey(channel.getCategoryId())) {
                        matchedChannels.add(channel);
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
                Map<String, List<Channel>> matchedChannelsByCatId = matchedChannels.stream()
                        .collect(Collectors.groupingBy(Channel::getCategoryId));

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

            } catch (Exception e) {
                logger.log("Error processing channel data: " + e.getMessage());
                showError("Error while processing channel data");
            }
        } else {
            logger.log("No response or empty response from server for channels");
        }
    }

    public List<Channel> get(String categoryId, Account account, String dbId) throws IOException {
        return get(categoryId, account, dbId, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger) throws IOException {
        List<Channel> cachedChannels = ChannelDb.get().getChannels(dbId);
        if (cachedChannels.isEmpty() && account.getType() != STALKER_PORTAL) {
            hardReloadChannels(categoryId, account, dbId);
        } else if (cachedChannels.isEmpty()) {
            reloadAllChannelsAndCategories(account, logger != null ? logger : log::info);
        }
        return censor(ChannelDb.get().getChannels(dbId));
    }

    public boolean isChannelListCached(String dbId) {
        return ChannelDb.get().isChannelListCached(dbId);
    }

    public List<Channel> getSeries(String categoryId, String movieId, Account account) {
        return censor(getStalkerPortalChOrSeries(categoryId, account, movieId, "0"));
    }


    private void hardReloadChannels(String categoryId, Account account, String dbId) {
        List<Channel> channels = new ArrayList<>();
        try {
            if (Objects.requireNonNull(account.getType()) == AccountType.M3U8_LOCAL || account.getType() == M3U8_URL) {
                channels.addAll(m3u8Channels(categoryId, account));
            } else if (account.getType() == AccountType.XTREME_API) {
                channels.addAll(xtremeAPICategories(categoryId, account));
            } else if (account.getType() == AccountType.RSS_FEED) {
                channels.addAll(rssChannels(categoryId, account));
            } else {
                channels.addAll(getStalkerPortalChOrSeries(categoryId, account, null, "0"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ChannelDb.get().saveAll(channels, dbId, account);
    }

    private List<Channel> xtremeAPICategories(String category, Account account) {
        return XtremeParser.parseChannels(category, account);
    }

    private List<Channel> m3u8Channels(String category, Account account) throws MalformedURLException {
        Set<Channel> channels = new LinkedHashSet<>();

        Set<PlaylistEntry> m3uCategories = account.getType() == M3U8_URL
                ? M3U8Parser.parseUrlCategory(new URL(account.getM3u8Path()))
                : M3U8Parser.parsePathCategory(account.getM3u8Path());
        boolean hasOtherCategories = m3uCategories.size() >= 2;

        List<PlaylistEntry> m3uEntries = account.getType() == M3U8_URL
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
            Channel c = new Channel(entry.getId(), entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
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

    private List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId) {
        List<Channel> channelList = new ArrayList<>();
        int pageNumber = 1;
        String json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
        Pagination pagination = parsePagination(json, null);
        if (pagination == null) return channelList;
        List<Channel> page1Channels = account.getAction() == itv ? parseItvChannels(json) : parseVodChannels(account, json);
        if (page1Channels != null) {
            channelList.addAll(page1Channels);
        }
        for (pageNumber = 2; pageNumber <= pagination.getPageCount(); pageNumber++) {
            json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
            List<Channel> pagedChannels = account.getAction() == itv ? parseItvChannels(json) : parseVodChannels(account, json);
            if (pagedChannels != null) {
                channelList.addAll(pagedChannels);
            }
        }
        return channelList;
    }

    public String readToJson(Category category, Account account) throws IOException {
        String categoryIdToUse;
        if (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API) {
            categoryIdToUse = category.getCategoryId();
        } else {
            categoryIdToUse = category.getTitle();
        }
        return ServerUtils.objectToJson(get(categoryIdToUse, account, category.getDbId()));
    }

    public Pagination parsePagination(String json, LoggerCallback logger) {
        try {
            JSONObject js = new JSONObject(json).getJSONObject("js");
            if (logger != null) {
                logger.log("total_items " + nullSafeInteger(js, "total_items"));
                logger.log("max_page_items " + nullSafeInteger(js, "max_page_items"));
            }
            return new Pagination(nullSafeInteger(js, "total_items"), nullSafeInteger(js, "max_page_items"));
        } catch (Exception ignored) {
            showError("Error while processing response data");
        }
        return null;
    }

    public List<Channel> parseItvChannels(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject js = root.optJSONObject("js", root);
            JSONArray list = js.getJSONArray("data");
            List<Channel> channelList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonChannel = list.getJSONObject(i);
                Channel channel = new Channel(String.valueOf(jsonChannel.get("id")), jsonChannel.getString("name"), jsonChannel.getString("number"), jsonChannel.getString("cmd"), jsonChannel.getString("cmd_1"), jsonChannel.getString("cmd_2"), jsonChannel.getString("cmd_3"), jsonChannel.getString("logo"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                channel.setCategoryId(nullSafeString(jsonChannel, "tv_genre_id"));
                channelList.add(channel);
            }
            return censor(channelList);

        } catch (Exception ignored) {
            showError("Error while processing itv response data");
        }
        return Collections.emptyList();
    }

    public List<Channel> parseVodChannels(Account account, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject js = root.optJSONObject("js", root);
            JSONArray list = js.getJSONArray("data");
            List<Channel> channelList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonChannel = list.getJSONObject(i);
                String name = nullSafeString(jsonChannel, "name");
                if (isBlank(name)) {
                    name = nullSafeString(jsonChannel, "o_name");
                }
                String number = nullSafeString(jsonChannel, "id");
                String cmd = nullSafeString(jsonChannel, "cmd");
                String categoryId = nullSafeString(jsonChannel, "tv_genre_id");

                if (account.getAction() == series && isNotBlank(cmd)) {
                    JSONArray seriesArray = jsonChannel.getJSONArray("series");
                    if (seriesArray != null) {
                        for (int j = 0; j < seriesArray.length(); j++) {
                            Channel channel = new Channel(String.valueOf(seriesArray.get(j)), name + " - Episode " + String.valueOf(seriesArray.get(j)), number, cmd, null, null, null, nullSafeString(jsonChannel, "screenshot_uri"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                            channel.setCategoryId(categoryId);
                            channelList.add(channel);
                        }
                    }
                } else {
                    Channel channel = new Channel(String.valueOf(jsonChannel.get("id")), name, number, cmd, null, null, null, nullSafeString(jsonChannel, "screenshot_uri"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                    channel.setCategoryId(categoryId);
                    channelList.add(channel);
                }
            }
            List<Channel> censoredChannelList = censor(channelList);
            Collections.sort(censoredChannelList, Comparator.comparing(Channel::getCompareSeason).thenComparing(Channel::getCompareEpisode));
            return censoredChannelList;
        } catch (Exception ignored) {
            showError("Error while processing vod response data");
        }
        return Collections.emptyList();
    }

    public List<Channel> censor(List<Channel> channelList) {
        Configuration configuration = ConfigurationService.getInstance().read();
        String commaSeparatedList = configuration.getFilterChannelsList();
        if (isBlank(commaSeparatedList) || configuration.isPauseFiltering()) return channelList;

        List<String> censoredChannels = new ArrayList<>(List.of(commaSeparatedList.split(",")));
        censoredChannels.replaceAll(String::trim);

        Predicate<Channel> hasCensoredWord = channel -> {
            String safeName = StringUtils.safeUtf(channel.getName()).toLowerCase();
            boolean containsBanned = censoredChannels.stream().anyMatch(word -> safeName.contains(word.toLowerCase()));
            return !containsBanned && channel.getCensored() != 1;
        };

        return channelList.stream().filter(hasCensoredWord).collect(Collectors.toList());
    }

}
