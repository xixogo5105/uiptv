package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
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
import com.uiptv.util.ServerUtils;
import com.uiptv.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.FetchAPI.nullSafeInteger;
import static com.uiptv.util.FetchAPI.nullSafeString;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

@Slf4j
public class ChannelService {
    private static ChannelService instance;
    private final CacheService cacheService;
    private final ContentFilterService contentFilterService;

    private ChannelService() {
        this.cacheService = new CacheServiceImpl();
        this.contentFilterService = ContentFilterService.getInstance();
    }

    public static Map<String, String> getChannelOrSeriesParams(String category, int pageNumber, Account.AccountAction accountAction, String movieId, String seriesId) {
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

    public static synchronized ChannelService getInstance() {
        if (instance == null) {
            instance = new ChannelService();
        }
        return instance;
    }

    public List<Channel> get(String categoryId, Account account, String dbId) throws IOException {
        return get(categoryId, account, dbId, null, null, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger) throws IOException {
        return get(categoryId, account, dbId, logger, null, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger, Consumer<List<Channel>> callback) throws IOException {
        return get(categoryId, account, dbId, logger, callback, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) throws IOException {
        //no caching
        if (NOT_LIVE_TV_CHANNELS.contains(account.getAction())) {
            if (account.getType() == AccountType.XTREME_API) {
                List<Channel> channels = XtremeParser.parseChannels(categoryId, account);
                if (callback != null) callback.accept(channels);
                return channels;
            } else {
                return getVodOrSeries(categoryId, account, callback, isCancelled);
            }
        }
        //no caching
        if (account.getType() == AccountType.RSS_FEED) {
            List<Channel> channels = maybeFilterChannels(rssChannels(categoryId, account), true);
            if (callback != null) callback.accept(channels);
            return channels;
        }
        //caching for everything else
        int channelCount = cacheService.getChannelCountForAccount(account.getDbId());
        if (channelCount == 0) {
            cacheService.reloadCache(account, logger != null ? logger : log::info);
        }

        List<Channel> channels = ChannelDb.get().getChannels(dbId);
        if (account.getType() == STALKER_PORTAL && account.getAction() == itv && channels.isEmpty()) {
            //if live TV channels for a category is empty then make a direct call to stream server for fetching the contents
            List<Channel> fetchedChannels = getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, false);
            if (!fetchedChannels.isEmpty()) {
                ChannelDb.get().saveAll(fetchedChannels, dbId, account);
                channels.addAll(fetchedChannels);
            }
        }
        List<Channel> censoredChannels = maybeFilterChannels(channels, true);
        if (callback != null) callback.accept(censoredChannels);
        return censoredChannels;
    }

    private List<Channel> getVodOrSeries(String categoryId, Account account, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) throws IOException {
        List<Channel> cachedChannels = new ArrayList<>(getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled));
        return maybeFilterChannels(cachedChannels, true);
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

    public void reloadCache(Account account, LoggerCallback logger) throws IOException {
        cacheService.reloadCache(account, logger);
    }

    public int getChannelCountForAccount(String accountId) {
        return cacheService.getChannelCountForAccount(accountId);
    }

    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, true);
    }

    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor) {
        // Different portals are inconsistent on first page index (0 vs 1); try both.
        List<Channel> channelsFromPageZero = fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 0);
        if (!channelsFromPageZero.isEmpty()) {
            return channelsFromPageZero;
        }
        return fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 1);
    }

    private List<Channel> fetchPagedStalkerChannels(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor, int startPage) {
        List<Channel> channelList = new ArrayList<>();
        String json = FetchAPI.fetch(getChannelOrSeriesParams(category, startPage, account.getAction(), movieId, seriesId), account);
        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
        List<Channel> firstPage = account.getAction() == itv
                ? ChannelService.getInstance().parseItvChannels(json, censor)
                : ChannelService.getInstance().parseVodChannels(account, json, censor);

        if (firstPage == null || firstPage.isEmpty()) {
            return channelList;
        }

        channelList.addAll(firstPage);
        if (callback != null) callback.accept(firstPage);

        int maxAdditionalPages = pagination == null ? 2 : Math.max(pagination.getPageCount() + 1, 2);
        for (int pageNumber = startPage + 1; pageNumber <= startPage + maxAdditionalPages; pageNumber++) {
            if (Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.get())) {
                break;
            }

            json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
            List<Channel> pagedChannels = account.getAction() == itv
                    ? ChannelService.getInstance().parseItvChannels(json, censor)
                    : ChannelService.getInstance().parseVodChannels(account, json, censor);
            if (pagedChannels == null || pagedChannels.isEmpty()) {
                break;
            }
            channelList.addAll(pagedChannels);
            if (callback != null) callback.accept(pagedChannels);
        }
        return channelList;
    }

    public List<Channel> getSeries(String categoryId, String movieId, Account account, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) {
        // This method does not seem to be part of the caching logic, so it can stay here.
        // If it needs to be cached, it should be moved to CacheServiceImpl.
        return maybeFilterChannels(getStalkerPortalChOrSeries(categoryId, account, movieId, "0", callback, isCancelled), true);
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
            JSONObject js = new JSONObject(json);
            JSONObject pagination = js.optJSONObject("pagination");
            if (pagination == null) {
                pagination = js.optJSONObject("js");
            }
            if (pagination != null) {
                if (logger != null) {
                    logger.log("total_items " + nullSafeInteger(pagination, "total_items"));
                    logger.log("max_page_items " + nullSafeInteger(pagination, "max_page_items"));
                }
                return new Pagination(nullSafeInteger(pagination, "total_items"), nullSafeInteger(pagination, "max_page_items"));
            }
        } catch (Exception ignored) {
            showError("Error while processing response data");
        }
        return null;
    }

    public List<Channel> parseItvChannels(String json, boolean censor) {
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
            return maybeFilterChannels(channelList, censor);

        } catch (Exception ignored) {
            showError("Error while processing itv response data");
        }
        return Collections.emptyList();
    }

    public List<Channel> parseVodChannels(Account account, String json, boolean censor) {
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
                            Channel channel = new Channel(String.valueOf(seriesArray.get(j)), name + " - Episode " + seriesArray.get(j), number, cmd, null, null, null, nullSafeString(jsonChannel, "screenshot_uri"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
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
            List<Channel> censoredChannelList = maybeFilterChannels(channelList, censor);
            Collections.sort(censoredChannelList, Comparator.comparing(Channel::getCompareSeason).thenComparing(Channel::getCompareEpisode));
            return censoredChannelList;
        } catch (Exception ignored) {
            showError("Error while processing vod response data");
        }
        return Collections.emptyList();
    }

    public List<Channel> censor(List<Channel> channelList) {
        return contentFilterService.filterChannels(channelList);
    }

    private List<Channel> maybeFilterChannels(List<Channel> channels, boolean applyFilter) {
        return applyFilter ? contentFilterService.filterChannels(channels) : channels;
    }

}
