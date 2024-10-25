package com.uiptv.service;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.*;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.ServerUtils;
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
import static com.uiptv.ui.M3U8Parser.parseChannelPathM3U8;
import static com.uiptv.ui.M3U8Parser.parseChannelUrlM3U8;
import static com.uiptv.util.AccountType.M3U8_URL;
import static com.uiptv.util.FetchAPI.nullSafeInteger;
import static com.uiptv.util.FetchAPI.nullSafeString;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

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
        params.put("hd", "0");
        params.put("p", String.valueOf(pageNumber));
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    public List<Channel> get(String categoryId, Account account, String dbId) throws IOException {
        List<Channel> cachedChannels = ChannelDb.get().getChannels(dbId);
        if (cachedChannels.isEmpty() || account.isPauseCaching() || ConfigurationService.getInstance().read().isPauseCaching()) {
            hardReloadChannels(categoryId, account, dbId);
            return censor(ChannelDb.get().getChannels(dbId));
        }
        return censor(cachedChannels);
    }

    public List<Channel> getSeries(String categoryId, String movieId, Account account) {
        return getStalkerPortalChOrSeries(categoryId, account, movieId, "0");
    }


    private void hardReloadChannels(String categoryId, Account account, String dbId) {
        List<Channel> channels = new ArrayList<>();
        try {
            if (Objects.requireNonNull(account.getType()) == AccountType.M3U8_LOCAL || account.getType() == M3U8_URL) {
                channels.addAll(m3u8Channels(categoryId, account));
            } else if (account.getType() == AccountType.XTREME_API) {
                channels.addAll(xtremeAPICategories(categoryId, account));
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
        List<PlaylistEntry> m3uEntries = account.getType() == M3U8_URL ? parseChannelUrlM3U8(new URL(account.getM3u8Path())) : parseChannelPathM3U8(account.getM3u8Path());
        m3uEntries.stream().filter(e -> category.equalsIgnoreCase("All") || e.getGroupTitle().equalsIgnoreCase(category) || e.getId().equalsIgnoreCase(category)).forEach(entry -> {
            Channel c = new Channel(entry.getId(), entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0);
            channels.add(c);
        });
        return channels.stream().toList();
    }

    private List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId) {
        List<Channel> channelList = new ArrayList<>();
        int pageNumber = 1;
        String json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
        Pagination pagination = parsePagination(json);
        if (pagination == null) return channelList;
        channelList.addAll(account.getAction() == itv ? parseItvChannels(json) : parseVodChannels(account, json));
        for (pageNumber = 2; pageNumber <= pagination.getPageCount(); pageNumber++) {
            json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
            channelList.addAll(account.getAction() == itv ? parseItvChannels(json) : parseVodChannels(account, json));
        }
        return channelList;
    }

    public String readToJson(String category, Account account, String dbId) throws IOException {
        return ServerUtils.objectToJson(get(category, account, dbId));
    }

    public Pagination parsePagination(String json) {
        try {
            JSONObject js = new JSONObject(json).getJSONObject("js");
            return new Pagination(nullSafeInteger(js, "total_items"), nullSafeInteger(js, "max_page_items"));
        } catch (Exception ignored) {
            showError("Error while processing response data");
        }
        return null;
    }

    public List<Channel> parseItvChannels(String json) {
        try {
            JSONObject js = new JSONObject(json).getJSONObject("js");
            JSONArray list = js.getJSONArray("data");
            List<Channel> channelList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonChannel = list.getJSONObject(i);
                channelList.add(new Channel(jsonChannel.getString("id"), jsonChannel.getString("name"), jsonChannel.getString("number"), jsonChannel.getString("cmd"), jsonChannel.getString("cmd_1"), jsonChannel.getString("cmd_2"), jsonChannel.getString("cmd_3"), jsonChannel.getString("logo"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd")));
            }
            return censor(channelList);

        } catch (Exception ignored) {
            showError("Error while processing itv response data");
        }
        return null;
    }

    public List<Channel> parseVodChannels(Account account, String json) {
        try {
            JSONObject js = new JSONObject(json).getJSONObject("js");
            JSONArray list = js.getJSONArray("data");
            List<Channel> channelList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonChannel = list.getJSONObject(i);
                String name = nullSafeString(jsonChannel, "name");
                if (isBlank(name)) {
                    name = nullSafeString(jsonChannel, "o_name");
                }
                //String number = nullSafeString(jsonChannel, "tmdb");
                //if (isBlank(number)) {
                String number = nullSafeString(jsonChannel, "id");
                //}
                String cmd = nullSafeString(jsonChannel, "cmd");
                if (account.getAction() == series && isNotBlank(cmd)) {
                    JSONArray series = jsonChannel.getJSONArray("series");
                    if (series != null) {
                        for (int j = 0; j < series.length(); j++) {
                            channelList.add(new Channel(String.valueOf(series.get(j)), name + " - Episode " + String.valueOf(series.get(j)), number, cmd, null, null, null, nullSafeString(jsonChannel, "screenshot_uri"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd")));
                        }
                    }
                } else {
                    channelList.add(new Channel(jsonChannel.getString("id"), name, number, cmd, null, null, null, nullSafeString(jsonChannel, "screenshot_uri"), nullSafeInteger(jsonChannel, "censored"), nullSafeInteger(jsonChannel, "status"), nullSafeInteger(jsonChannel, "hd")));
                }
            }
            List<Channel> censoredChannelList = censor(channelList);
            Collections.sort(censoredChannelList, Comparator.comparing(Channel::getCompareSeason).thenComparing(Channel::getCompareEpisode));
            return censoredChannelList;
        } catch (Exception ignored) {
            showError("Error while processing vod response data");
        }
        return null;
    }

    public List<Channel> censor(List<Channel> channelList) {
        Configuration configuration = ConfigurationService.getInstance().read();
        String commaSeparatedList = configuration.getFilterChannelsList();
        if (isBlank(commaSeparatedList) || configuration.isPauseFiltering()) return channelList;
        List<String> censoredChannels = new ArrayList<>(List.of(ConfigurationService.getInstance().read().getFilterChannelsList().split(",")));
        censoredChannels.replaceAll(String::trim);
        Predicate<Channel> hasCensoredWord = channel -> censoredChannels.stream().noneMatch(word -> channel.getName().toLowerCase().contains(word.toLowerCase()) || channel.getCensored() == 1);

        return channelList.stream().filter(hasCensoredWord).collect(Collectors.toList());
    }

}

