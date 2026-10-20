package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.SeasonInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import org.json.JSONTokener;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.nullSafeEncode;
import static com.uiptv.util.StringUtils.safeGetString;
import static java.nio.charset.StandardCharsets.UTF_8;

public class XtremeApiParser {
    private static final String STREAM_ID = "stream_id";
    private static final String PARAM_CATEGORY_ID = "category_id";
    private static final String XTREME_ERROR_PROCESSING_RESPONSE_DATA = "xtremeErrorProcessingResponseData: ";
    private static final int CHANNEL_PARSE_BATCH_SIZE = Integer.getInteger("uiptv.xtreme.channelParseBatchSize", 250);

    private XtremeApiParser() {
    }

    public static List<Category> parseCategories(Account account) {
        try {
            return doParseCategories(fetchPlayerApi(account, getCategoryAction(account.getAction()), null));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load Xtreme categories", e);
        }
    }

    public static List<Channel> parseChannels(String categoryId, Account account) {
        return parseChannels(categoryId, account, null, null);
    }

    public static List<Channel> parseChannels(String categoryId, Account account, Consumer<List<Channel>> callback, BooleanSupplier isCancelled) {
        try {
            Map<String, String> extraParams = new LinkedHashMap<>();
            extraParams.put(PARAM_CATEGORY_ID, categoryId);
            if (callback != null || isCancelled != null) {
                return doParseChannels(fetchPlayerApi(account, getChannelListAction(account.getAction()), extraParams), account, callback, isCancelled);
            }
            return fetchChannelCatalogue(account, extraParams, null, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load Xtreme channels", e);
        }
    }

    public static List<Channel> parseAllChannels(Account account) {
        try {
            return fetchChannelCatalogue(account, null, null, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load all Xtreme channels", e);
        }
    }

    /** Stream channel rows so large valid catalogues do not require a second full JSON tree. */
    private static List<Channel> fetchChannelCatalogue(Account account, Map<String, String> params,
                                                       Consumer<List<Channel>> callback, BooleanSupplier cancelled) throws IOException {
        IOException last = null;
        for (String base : baseUrlCandidates(account)) {
            ReloadRequestScope.checkCurrent();
            try (HttpUtil.StreamResult response = HttpUtil.openStream(
                    buildPlayerApiUrl(base, account, getChannelListAction(account.getAction()), params),
                    null, "GET", null, HttpUtil.RequestOptions.streamingCatalogue())) {
                return readChannelResponse(response, account, callback, cancelled);
            } catch (ReloadRequestScope.LimitExceeded failure) {
                throw failure;
            } catch (IOException | RuntimeException failure) {
                ReloadRequestScope.checkCurrent();
                if (cancelled != null && cancelled.getAsBoolean()) return List.of();
                last = new IOException("Unable to read complete Xtreme channel catalogue", failure);
            }
        }
        throw last != null ? last : new IOException("Xtreme base URL is blank.");
    }

    private static List<Channel> readChannelResponse(HttpUtil.StreamResult response, Account account,
                                                      Consumer<List<Channel>> callback, BooleanSupplier cancelled)
            throws IOException {
        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Xtreme API request failed with HTTP " + response.statusCode());
            }
            return readChannelCatalogue(new InputStreamReader(response.bodyStream(), UTF_8), account, callback, cancelled);
        } catch (IOException | RuntimeException failure) {
            try {
                response.abort();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    static List<Channel> readChannelCatalogue(Reader source, Account account,
                                               Consumer<List<Channel>> callback, BooleanSupplier cancelled) {
        JSONTokener input = new JSONTokener(source);
        if (input.nextClean() != '[') throw input.syntaxError("Expected Xtreme channel array");
        List<Channel> channels = new ArrayList<>();
        List<Channel> batch = new ArrayList<>();
        char next = input.nextClean();
        if (next != ']') {
            input.back();
            readChannelRows(input, account, callback, cancelled, channels, batch);
        }
        if (input.nextClean() != 0) throw input.syntaxError("Unexpected data after Xtreme channel array");
        if (callback != null && !batch.isEmpty()) callback.accept(List.copyOf(batch));
        return channels;
    }

    private static void readChannelRows(JSONTokener input, Account account, Consumer<List<Channel>> callback,
                                        BooleanSupplier cancelled, List<Channel> channels, List<Channel> batch) {
        while (true) {
            ReloadRequestScope.checkCurrent();
            if (cancelled != null && cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
            appendChannel(input, account, callback, channels, batch);
            char next = input.nextClean();
            if (next == ']') return;
            if (next != ',') throw input.syntaxError("Expected Xtreme channel separator");
        }
    }

    private static void appendChannel(JSONTokener input, Account account, Consumer<List<Channel>> callback,
                                      List<Channel> channels, List<Channel> batch) {
        Channel channel = parseXtremeChannel(new JSONObject(input), account);
        channels.add(channel);
        if (callback == null) return;
        batch.add(channel);
        if (batch.size() >= CHANNEL_PARSE_BATCH_SIZE) {
            callback.accept(List.copyOf(batch));
            batch.clear();
        }
    }

    public static EpisodeList parseEpisodes(String seriesId, Account account) {
        try {
            Map<String, String> extraParams = new LinkedHashMap<>();
            extraParams.put("series_id", seriesId);
            return doParseEpisodes(fetchPlayerApi(account, "get_series_info", extraParams), account);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load Xtreme episodes", e);
        }
    }

    private static List<Category> doParseCategories(String json) {
        List<Category> categoryList = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonCategory = list.getJSONObject(i);
                Category category = new Category(safeGetString(jsonCategory, PARAM_CATEGORY_ID), jsonCategory.getString("category_name"), jsonCategory.getString("category_name"), true, 0);
                category.setExtraJson(jsonCategory.toString());
                categoryList.add(category);
            }
        } catch (Exception e) {
            AppLog.addErrorLog(XtremeApiParser.class, XTREME_ERROR_PROCESSING_RESPONSE_DATA + e.getMessage());
        }
        return categoryList;
    }

    private static List<Channel> doParseChannels(String json, Account account, Consumer<List<Channel>> callback, BooleanSupplier isCancelled) {
        List<Channel> categoryList = new ArrayList<>();
        List<Channel> callbackBatch = callback == null ? null : new ArrayList<>(CHANNEL_PARSE_BATCH_SIZE);
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                if (isCancelled != null && isCancelled.getAsBoolean()) {
                    break;
                }
                JSONObject jsonCategory = list.getJSONObject(i);
                Channel channel = parseXtremeChannel(jsonCategory, account);
                categoryList.add(channel);
                if (callbackBatch != null) {
                    callbackBatch.add(channel);
                    if (callbackBatch.size() >= CHANNEL_PARSE_BATCH_SIZE) {
                        callback.accept(List.copyOf(callbackBatch));
                        callbackBatch.clear();
                    }
                }
            }
            if (callbackBatch != null && !callbackBatch.isEmpty()) {
                callback.accept(List.copyOf(callbackBatch));
            }
        } catch (Exception e) {
            AppLog.addErrorLog(XtremeApiParser.class, XTREME_ERROR_PROCESSING_RESPONSE_DATA + e.getMessage());
        }
        return categoryList;
    }

    private static Channel parseXtremeChannel(JSONObject jsonCategory, Account account) {
        Channel channel = new Channel(
                safeGetString(jsonCategory, account.getAction() == series ? "series_id" : STREAM_ID),
                safeGetString(jsonCategory, "name"),
                null,
                buildXtremeStreamUrl(account, safeGetString(jsonCategory, STREAM_ID), safeGetString(jsonCategory, "container_extension")),
                null,
                null,
                null,
                safeGetString(jsonCategory, account.getAction() == series ? "cover" : "stream_icon"),
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null
        );
        channel.setCategoryId(safeGetString(jsonCategory, PARAM_CATEGORY_ID));
        channel.setExtraJson(jsonCategory.toString());
        return channel;
    }

    private static EpisodeList doParseEpisodes(String json, Account account) {
        EpisodeList episodeList = new EpisodeList();
        try {
            JSONObject data = new JSONObject(json);
            episodeList.setSeasonInfo(new SeasonInfo(data.getJSONObject("info")));
            for (Map.Entry<String, Object> entry : data.getJSONObject("episodes").toMap().entrySet()) {
                List<?> seasonEpisodes = (List<?>) entry.getValue();
                if (seasonEpisodes != null && !seasonEpisodes.isEmpty()) {
                    seasonEpisodes.forEach(episode -> episodeList.getEpisodes().add(new Episode(account, (Map<?, ?>) episode)));
                }
            }
        } catch (Exception e) {
            AppLog.addErrorLog(XtremeApiParser.class, XTREME_ERROR_PROCESSING_RESPONSE_DATA + e.getMessage());
        }
        return episodeList;
    }

    private static String getCategoryAction(Account.AccountAction action) {
        switch (action) {
            case vod:
                return "get_vod_categories";
            case series:
                return "get_series_categories";
            case itv:
            default:
                return "get_live_categories";
        }
    }

    private static String getChannelListAction(Account.AccountAction action) {
        switch (action) {
            case vod:
                return "get_vod_streams";
            case series:
                return "get_series";
            case itv:
            default:
                return "get_live_streams";
        }
    }

    public static String fetchEpg(Account account, String action, String streamId) throws IOException {
        if (!List.of("get_short_epg", "get_simple_data_table").contains(action)) throw new IOException("Unsupported EPG action");
        return fetchPlayerApi(account, action, Map.of(STREAM_ID, streamId, "limit", "100"));
    }

    private static String fetchPlayerApi(Account account, String action, Map<String, String> extraParams) throws IOException {
        List<String> baseUrlCandidates = baseUrlCandidates(account);
        if (baseUrlCandidates.isEmpty()) {
            throw new IOException("Xtreme base URL is blank.");
        }

        IOException lastIoException = null;
        for (int index = 0; index < baseUrlCandidates.size(); index++) {
            boolean hasMoreCandidates = hasMoreBaseUrlCandidates(baseUrlCandidates, index);
            try {
                String responseBody = fetchPlayerApiCandidate(baseUrlCandidates.get(index), account, action, extraParams, hasMoreCandidates);
                if (responseBody != null) {
                    return responseBody;
                }
            } catch (Exception e) {
                lastIoException = toPlayerApiIOException(e);
            }
        }
        throw lastIoException != null ? lastIoException : new IOException("Failed to call Xtreme API.");
    }

    private static boolean hasMoreBaseUrlCandidates(List<String> baseUrlCandidates, int index) {
        return index + 1 < baseUrlCandidates.size();
    }

    private static String fetchPlayerApiCandidate(String baseUrl, Account account, String action, Map<String, String> extraParams,
                                                  boolean hasMoreCandidates) throws IOException {
        HttpUtil.HttpResult response;
        try {
            response = HttpUtil.sendRequest(buildPlayerApiUrl(baseUrl, account, action, extraParams), null, "GET", null,
                    action.startsWith("get_short_epg") || action.equals("get_simple_data_table")
                            ? new HttpUtil.RequestOptions(true, true, 5, 5, 10) : HttpUtil.RequestOptions.defaults());
        } catch (Exception e) {
            throw toPlayerApiIOException(e);
        }
        if (isSuccessfulPlayerApiResponse(response)) {
            return response.body();
        }
        if (response.statusCode() == 404 && hasMoreCandidates) {
            return null;
        }
        throw new IOException("Xtreme API request failed with HTTP " + response.statusCode());
    }

    private static IOException toPlayerApiIOException(Exception e) {
        if (e instanceof IOException ioException) {
            return ioException;
        }
        return new IOException("Failed to call Xtreme API: " + e.getMessage(), e);
    }

    private static String buildPlayerApiUrl(String baseUrl, Account account, String action, Map<String, String> extraParams) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("player_api.php")
                .append("?username=").append(nullSafeEncode(account.getUsername()))
                .append("&password=").append(nullSafeEncode(account.getPassword()))
                .append("&action=").append(nullSafeEncode(action));
        appendExtraParams(url, extraParams);
        return url.toString();
    }

    private static void appendExtraParams(StringBuilder url, Map<String, String> extraParams) {
        if (extraParams == null) {
            return;
        }
        for (Map.Entry<String, String> entry : extraParams.entrySet()) {
            url.append('&')
                    .append(nullSafeEncode(entry.getKey()))
                    .append('=')
                    .append(nullSafeEncode(entry.getValue()));
        }
    }

    private static boolean isSuccessfulPlayerApiResponse(HttpUtil.HttpResult response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private static String normalizedBaseUrl(Account account) {
        if (account == null) {
            return "";
        }
        String fromM3uPath = normalizeBaseUrl(account.getM3u8Path());
        if (isBlank(fromM3uPath)) {
            return normalizeBaseUrl(account.getUrl());
        }
        return fromM3uPath;
    }

    private static List<String> baseUrlCandidates(Account account) {
        Set<String> candidates = new LinkedHashSet<>();
        if (account != null) {
            String fromM3uPath = normalizeBaseUrl(account.getM3u8Path());
            if (!isBlank(fromM3uPath)) {
                candidates.add(fromM3uPath);
            }
            String fromUrl = normalizeBaseUrl(account.getUrl());
            if (!isBlank(fromUrl)) {
                candidates.add(fromUrl);
            }
        }
        return new ArrayList<>(candidates);
    }

    private static String normalizeBaseUrl(String source) {
        if (isBlank(source)) {
            return "";
        }
        String trimmed = source.trim();
        if (!trimmed.contains("://")) {
            trimmed = "http://" + trimmed;
        }
        int playerApiIndex = trimmed.toLowerCase().indexOf("player_api.php");
        if (playerApiIndex >= 0) {
            trimmed = trimmed.substring(0, playerApiIndex);
        }
        if (!trimmed.endsWith("/")) {
            trimmed += "/";
        }
        return trimmed;
    }

    private static String buildXtremeStreamUrl(Account account, String streamId, String extension) {
        String baseUrl = normalizedBaseUrl(account);
        if (isBlank(baseUrl) || isBlank(streamId)) {
            return "";
        }
        String ext = isBlank(extension) ? "ts" : extension;
        String username = URLEncoder.encode(account.getUsername(), UTF_8);
        String password = URLEncoder.encode(account.getPassword(), UTF_8);
        switch (account.getAction()) {
            case vod:
                return baseUrl + "movie/" + username + "/" + password + "/" + streamId + "." + ext;
            case series:
                return baseUrl + "series/" + username + "/" + password + "/" + streamId + "." + ext;
            default:
                return baseUrl + username + "/" + password + "/" + streamId;
        }
    }
}
