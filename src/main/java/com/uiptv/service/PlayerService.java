package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.util.ServerUrlUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.StringUtils.isBlank;

public class PlayerService {
    private static PlayerService instance;
    private final Set<PlaybackResolvedListener> playbackResolvedListeners = new CopyOnWriteArraySet<>();

    private final XtremePlayerService xtremePlayerService = new XtremePlayerService();
    private final StalkerPortalPlayerService stalkerPortalPlayerService = new StalkerPortalPlayerService();
    private final PredefinedPlayerService predefinedPlayerService = new PredefinedPlayerService();

    private PlayerService() {
        addPlaybackResolvedListener((account, channel, seriesId, parentSeriesId, categoryId) ->
                SeriesWatchStateService.getInstance().onPlaybackResolved(account, channel, seriesId, parentSeriesId, categoryId));
    }

    public static synchronized PlayerService getInstance() {
        if (instance == null) {
            instance = new PlayerService();
        }
        return instance;
    }

    public PlayerResponse get(Account account, Channel channel) throws IOException {
        return get(account, channel, "", "", "");
    }

    public PlayerResponse get(Account account, Channel channel, String series) throws IOException {
        return get(account, channel, series, "", "");
    }

    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId) throws IOException {
        return get(account, channel, series, parentSeriesId, "");
    }

    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        AccountPlayerService service = getPlayerService(account);
        PlayerResponse response = service.get(account, channel, series, parentSeriesId, categoryId);
        notifyPlaybackResolved(account, channel, series, parentSeriesId, categoryId);
        return response;
    }

    private AccountPlayerService getPlayerService(Account account) {
        if (account.getType() == XTREME_API) {
            return xtremePlayerService;
        } else if (Account.PRE_DEFINED_URLS.contains(account.getType())) {
            return predefinedPlayerService;
        } else {
            return stalkerPortalPlayerService;
        }
    }

    public void addPlaybackResolvedListener(PlaybackResolvedListener listener) {
        if (listener != null) {
            playbackResolvedListeners.add(listener);
        }
    }

    public void removePlaybackResolvedListener(PlaybackResolvedListener listener) {
        if (listener != null) {
            playbackResolvedListeners.remove(listener);
        }
    }

    public boolean isDrmProtected(Channel channel) {
        if (channel == null) {
            return false;
        }
        return !isBlank(channel.getDrmType())
                || !isBlank(channel.getDrmLicenseUrl())
                || !isBlank(channel.getClearKeysJson())
                || !isBlank(channel.getInputstreamaddon())
                || !isBlank(channel.getManifestType());
    }

    public String buildDrmBrowserPlaybackUrl(Account account, Channel channel, String categoryId, String mode) {
        JSONObject payload = new JSONObject();
        payload.put("mode", normalizeMode(mode, account));
        payload.put("accountId", account == null ? "" : safe(account.getDbId()));
        payload.put("categoryId", safe(categoryId));

        JSONObject channelJson = new JSONObject();
        channelJson.put("dbId", channel == null ? "" : safe(channel.getDbId()));
        channelJson.put("channelId", channel == null ? "" : safe(channel.getChannelId()));
        channelJson.put("name", channel == null ? "" : safe(channel.getName()));
        channelJson.put("logo", channel == null ? "" : safe(channel.getLogo()));
        channelJson.put("cmd", channel == null ? "" : safe(channel.getCmd()));
        channelJson.put("cmd_1", channel == null ? "" : safe(channel.getCmd_1()));
        channelJson.put("cmd_2", channel == null ? "" : safe(channel.getCmd_2()));
        channelJson.put("cmd_3", channel == null ? "" : safe(channel.getCmd_3()));
        channelJson.put("drmType", channel == null ? "" : safe(channel.getDrmType()));
        channelJson.put("drmLicenseUrl", channel == null ? "" : safe(channel.getDrmLicenseUrl()));
        channelJson.put("clearKeysJson", channel == null ? "" : safe(channel.getClearKeysJson()));
        channelJson.put("inputstreamaddon", channel == null ? "" : safe(channel.getInputstreamaddon()));
        channelJson.put("manifestType", channel == null ? "" : safe(channel.getManifestType()));
        payload.put("channel", channelJson);

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return localServerOrigin() + "/player.html?drmLaunch=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8) + "&v=20260224a";
    }

    private static String localServerOrigin() {
        return ServerUrlUtil.getLocalServerUrl();
    }

    private static String normalizeMode(String mode, Account account) {
        String m = safe(mode).toLowerCase();
        if ("itv".equals(m) || "vod".equals(m) || "series".equals(m)) {
            return m;
        }
        if (account != null && account.getAction() != null) {
            String derived = safe(account.getAction().name()).toLowerCase();
            if ("itv".equals(derived) || "vod".equals(derived) || "series".equals(derived)) {
                return derived;
            }
        }
        return "itv";
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private void notifyPlaybackResolved(Account account, Channel channel, String seriesId, String parentSeriesId, String categoryId) {
        for (PlaybackResolvedListener listener : playbackResolvedListeners) {
            try {
                listener.onPlaybackResolved(account, channel, seriesId, parentSeriesId, categoryId);
            } catch (Exception ignored) {
                // Callback failures should not impact playback.
            }
        }
    }

    @FunctionalInterface
    public interface PlaybackResolvedListener {
        void onPlaybackResolved(Account account, Channel channel, String seriesId, String parentSeriesId, String categoryId);
    }
}
