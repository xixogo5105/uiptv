package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.VodChannelDb;
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
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
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
    private static final int MAX_PAGES_WITHOUT_PAGINATION = 200;
    private static final long VOD_SERIES_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L;
    private static ChannelService instance;
    private final CacheService cacheService;
    private final ContentFilterService contentFilterService;
    private final LogoResolverService logoResolverService;

    private ChannelService() {
        this.cacheService = new CacheServiceImpl();
        this.contentFilterService = ContentFilterService.getInstance();
        this.logoResolverService = LogoResolverService.getInstance();
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
        if (NOT_LIVE_TV_CHANNELS.contains(account.getAction())) {
            if (shouldUseVodSeriesDbCache(account)) {
                List<Channel> cachedChannels = getVodSeriesFromDbCache(account, dbId);
                if (!cachedChannels.isEmpty() && isVodSeriesChannelsFresh(account, dbId)) {
                    log(logger, "Loaded channels from local cache for category " + categoryId + ".");
                    List<Channel> result = maybeFilterChannels(dedupeChannels(cachedChannels), true);
                    if (callback != null) callback.accept(result);
                    return result;
                }

                log(logger, "No fresh cache found for category " + categoryId + ". Fetching from portal...");
                List<Channel> fetchedChannels = fetchVodSeriesFromProviderAllPages(categoryId, account, isCancelled, logger);
                boolean cancelled = Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.get());
                if (!fetchedChannels.isEmpty() && !cancelled) {
                    saveVodSeriesToDbCache(account, dbId, fetchedChannels);
                    log(logger, "Saved " + fetchedChannels.size() + " channels to local cache.");
                }
                List<Channel> resolved = !fetchedChannels.isEmpty() ? fetchedChannels : cachedChannels;
                List<Channel> result = maybeFilterChannels(dedupeChannels(resolved), true);
                if (callback != null) callback.accept(result);
                return result;
            }

            // Existing behavior for non Stalker/Xtreme VOD/Series sources.
            return getVodOrSeries(categoryId, account, callback, isCancelled, logger);
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

        List<Channel> channels = dedupeChannels(ChannelDb.get().getChannels(dbId));
        channels.forEach(this::resolveLogoIfNeeded);
        if (account.getType() == STALKER_PORTAL && account.getAction() == itv && channels.isEmpty()) {
            //if live TV channels for a category is empty then make a direct call to stream server for fetching the contents
            log(logger, "No cached live channels for category " + categoryId + ". Fetching from portal...");
            List<Channel> fetchedChannels = getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, false, logger);
            if (!fetchedChannels.isEmpty()) {
                ChannelDb.get().saveAll(fetchedChannels, dbId, account);
                channels.addAll(fetchedChannels);
                log(logger, "Saved " + fetchedChannels.size() + " live channels to local cache.");
            }
        }
        List<Channel> censoredChannels = maybeFilterChannels(dedupeChannels(channels), true);
        if (callback != null) callback.accept(censoredChannels);
        return censoredChannels;
    }

    private List<Channel> getVodOrSeries(String categoryId, Account account, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, LoggerCallback logger) throws IOException {
        List<Channel> cachedChannels = new ArrayList<>(getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, true, logger));
        return maybeFilterChannels(cachedChannels, true);
    }

    private boolean shouldUseVodSeriesDbCache(Account account) {
        return (account.getAction() == vod || account.getAction() == series)
                && (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API);
    }

    private List<Channel> getVodSeriesFromDbCache(Account account, String dbCategoryId) {
        List<Channel> channels;
        if (account.getAction() == vod) {
            channels = VodChannelDb.get().getChannels(account, dbCategoryId);
        } else if (account.getAction() == series) {
            channels = SeriesChannelDb.get().getChannels(account, dbCategoryId);
        } else {
            channels = Collections.emptyList();
        }
        channels = dedupeChannels(channels);
        channels.forEach(channel -> {
            channel.setLogo(normalizeLogoUrl(account, channel.getLogo()));
            if (isBlank(channel.getLogo())) {
                channel.setLogo(normalizeLogoUrl(account, extractLogoFromExtraJson(channel.getExtraJson())));
            }
        });
        channels.forEach(this::resolveLogoIfNeeded);
        return channels;
    }

    private boolean isVodSeriesChannelsFresh(Account account, String dbCategoryId) {
        if (account.getAction() == vod) {
            return VodChannelDb.get().isFresh(account, dbCategoryId, VOD_SERIES_CACHE_TTL_MS);
        }
        if (account.getAction() == series) {
            return SeriesChannelDb.get().isFresh(account, dbCategoryId, VOD_SERIES_CACHE_TTL_MS);
        }
        return false;
    }

    private void saveVodSeriesToDbCache(Account account, String dbCategoryId, List<Channel> channels) {
        if (account.getAction() == vod) {
            VodChannelDb.get().saveAll(channels, dbCategoryId, account);
            return;
        }
        if (account.getAction() == series) {
            SeriesChannelDb.get().saveAll(channels, dbCategoryId, account);
        }
    }

    private List<Channel> fetchVodSeriesFromProviderAllPages(String categoryId, Account account, Supplier<Boolean> isCancelled, LoggerCallback logger) throws IOException {
        List<Channel> channels;
        if (account.getType() == XTREME_API) {
            channels = dedupeChannels(XtremeParser.parseChannels(categoryId, account));
        } else {
            // Callback is intentionally null: UI callback is fired once after all pages are fetched.
            channels = getStalkerPortalChOrSeries(categoryId, account, null, "0", null, isCancelled, true, logger);
        }
        channels.forEach(this::resolveLogoIfNeeded);
        return channels;
    }

    private List<Channel> rssChannels(String category, Account account) throws MalformedURLException {
        Set<Channel> channels = new LinkedHashSet<>();
        List<PlaylistEntry> rssEntries = RssParser.parse(account.getM3u8Path());
        rssEntries.stream().filter(e -> category.equalsIgnoreCase("All") || e.getGroupTitle().equalsIgnoreCase(category) || e.getId().equalsIgnoreCase(category)).forEach(entry -> {
            Channel c = new Channel(entry.getId(), entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
            resolveLogoIfNeeded(c);
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

    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, LoggerCallback logger) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, true, logger);
    }

    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, censor, null);
    }

    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor, LoggerCallback logger) {
        log(logger, "Starting portal fetch for category " + category + ".");
        // Different portals are inconsistent on first page index (0 vs 1); try both.
        List<Channel> channelsFromPageZero = fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 0, logger);
        if (!channelsFromPageZero.isEmpty()) {
            log(logger, "Portal fetch finished with " + channelsFromPageZero.size() + " channels.");
            return channelsFromPageZero;
        }
        log(logger, "No channels on page 0. Retrying from page 1...");
        List<Channel> fallback = dedupeChannels(fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 1, logger));
        log(logger, "Portal fetch finished with " + fallback.size() + " channels.");
        return fallback;
    }

    private List<Channel> fetchPagedStalkerChannels(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor, int startPage, LoggerCallback logger) {
        List<Channel> channelList = new ArrayList<>();
        log(logger, "Fetching page " + startPage + " for category " + category + "...");
        String json = FetchAPI.fetch(getChannelOrSeriesParams(category, startPage, account.getAction(), movieId, seriesId), account);
        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
        List<Channel> firstPage = account.getAction() == itv
                ? ChannelService.getInstance().parseItvChannels(json, censor)
                : ChannelService.getInstance().parseVodChannels(account, json, censor);

        if (firstPage == null || firstPage.isEmpty()) {
            log(logger, "Page " + startPage + " returned no channels.");
            return channelList;
        }

        channelList.addAll(firstPage);
        log(logger, "Fetched " + firstPage.size() + " channels from page " + startPage + ".");
        if (callback != null) callback.accept(firstPage);

        int maxAdditionalPages = pagination == null ? MAX_PAGES_WITHOUT_PAGINATION : Math.max(pagination.getPageCount() + 1, 2);
        for (int pageNumber = startPage + 1; pageNumber <= startPage + maxAdditionalPages; pageNumber++) {
            if (Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.get())) {
                log(logger, "Portal fetch cancelled at page " + pageNumber + ".");
                break;
            }

            log(logger, "Fetching page " + pageNumber + " for category " + category + "...");
            json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
            List<Channel> pagedChannels = account.getAction() == itv
                    ? ChannelService.getInstance().parseItvChannels(json, censor)
                    : ChannelService.getInstance().parseVodChannels(account, json, censor);
            if (pagedChannels == null || pagedChannels.isEmpty()) {
                log(logger, "Page " + pageNumber + " returned no channels. Stopping pagination.");
                break;
            }
            channelList.addAll(pagedChannels);
            log(logger, "Fetched " + pagedChannels.size() + " channels from page " + pageNumber + ".");
            if (callback != null) callback.accept(pagedChannels);
        }
        return dedupeChannels(channelList);
    }

    private void log(LoggerCallback logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
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
                Channel channel = new Channel(String.valueOf(jsonChannel.get("id")), jsonChannel.getString("name"), jsonChannel.getString("number"), jsonChannel.getString("cmd"), jsonChannel.getString("cmd_1"), jsonChannel.getString("cmd_2"), jsonChannel.getString("cmd_3"), normalizeLogoUrl(null, jsonChannel.getString("logo")), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                channel.setCategoryId(nullSafeString(jsonChannel, "tv_genre_id"));
                channel.setExtraJson(jsonChannel.toString());
                resolveLogoIfNeeded(channel);
                channelList.add(channel);
            }
            return maybeFilterChannels(dedupeChannels(channelList), censor);

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
                String preferredLogo = preferredVodLogo(jsonChannel);

                if (account.getAction() == series && isNotBlank(cmd)) {
                    JSONArray seriesArray = jsonChannel.getJSONArray("series");
                    if (seriesArray != null) {
                        for (int j = 0; j < seriesArray.length(); j++) {
                            Channel channel = new Channel(String.valueOf(seriesArray.get(j)), name + " - Episode " + seriesArray.get(j), number, cmd, null, null, null, normalizeLogoUrl(account, preferredLogo), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                            channel.setCategoryId(categoryId);
                            channel.setExtraJson(jsonChannel.toString());
                            resolveLogoIfNeeded(channel);
                            channelList.add(channel);
                        }
                    }
                } else {
                    Channel channel = new Channel(String.valueOf(jsonChannel.get("id")), name, number, cmd, null, null, null, normalizeLogoUrl(account, preferredLogo), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                    channel.setCategoryId(categoryId);
                    channel.setExtraJson(jsonChannel.toString());
                    resolveLogoIfNeeded(channel);
                    channelList.add(channel);
                }
            }
            List<Channel> censoredChannelList = maybeFilterChannels(dedupeChannels(channelList), censor);
            Collections.sort(censoredChannelList, Comparator.comparing(Channel::getCompareSeason).thenComparing(Channel::getCompareEpisode));
            return censoredChannelList;
        } catch (Exception ignored) {
            showError("Error while processing vod response data");
        }
        return Collections.emptyList();
    }

    private String preferredVodLogo(JSONObject jsonChannel) {
        if (jsonChannel == null) {
            return "";
        }
        String logo = nullSafeString(jsonChannel, "screenshot_uri");
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "stream_icon");
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "cover");
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "movie_image");
        return logo;
    }

    private String extractLogoFromExtraJson(String extraJson) {
        if (isBlank(extraJson)) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(extraJson);
            String logo = nullSafeString(json, "screenshot_uri");
            if (isBlank(logo)) logo = nullSafeString(json, "stream_icon");
            if (isBlank(logo)) logo = nullSafeString(json, "cover");
            if (isBlank(logo)) logo = nullSafeString(json, "movie_image");
            return logo == null ? "" : logo;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeLogoUrl(Account account, String logo) {
        if (isBlank(logo)) {
            return "";
        }
        String value = logo.trim().replace("\\/", "/");
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (isBlank(value)) {
            return "";
        }
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return value;
        }
        String portal = account != null ? account.getServerPortalUrl() : "";
        String scheme = "https";
        String host = "";
        int port = -1;
        try {
            if (!isBlank(portal)) {
                URI uri = URI.create(portal.trim());
                if (!isBlank(uri.getScheme())) scheme = uri.getScheme();
                if (!isBlank(uri.getHost())) host = uri.getHost();
                port = uri.getPort();
            }
        } catch (Exception ignored) {
        }
        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }
        if (value.startsWith("/") && !isBlank(host)) {
            return scheme + "://" + host + (port > 0 ? ":" + port : "") + value;
        }
        return value;
    }

    public List<Channel> censor(List<Channel> channelList) {
        return contentFilterService.filterChannels(channelList);
    }

    private List<Channel> maybeFilterChannels(List<Channel> channels, boolean applyFilter) {
        return applyFilter ? contentFilterService.filterChannels(channels) : channels;
    }

    private List<Channel> dedupeChannels(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return channels == null ? Collections.emptyList() : channels;
        }
        LinkedHashMap<String, Channel> unique = new LinkedHashMap<>();
        for (Channel c : channels) {
            if (c == null) continue;
            String key = String.join("|",
                    StringUtils.isBlank(c.getChannelId()) ? "" : c.getChannelId().trim(),
                    StringUtils.isBlank(c.getCmd()) ? "" : c.getCmd().trim(),
                    StringUtils.isBlank(c.getName()) ? "" : c.getName().trim().toLowerCase()
            );
            unique.putIfAbsent(key, c);
        }
        return new ArrayList<>(unique.values());
    }

    private void resolveLogoIfNeeded(Channel channel) {
        if (channel == null) {
            return;
        }
        String currentLogo = channel.getLogo();
        boolean hasAbsoluteLogo = isNotBlank(currentLogo)
                && (currentLogo.startsWith("http://") || currentLogo.startsWith("https://"));
        if (hasAbsoluteLogo) {
            return;
        }
        String resolved = logoResolverService.resolve(channel.getName(), currentLogo, null);
        if (isNotBlank(resolved)) {
            channel.setLogo(resolved);
        }
    }

}
