package com.uiptv.application;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerRequestResolver;
import com.uiptv.service.PlayerService;
import com.uiptv.util.HlsPlaylistResolver;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.uiptv.util.M3uPlaylistUtils.escapeAttributeValue;
import static com.uiptv.util.M3uPlaylistUtils.sanitizeTitle;
import static com.uiptv.util.StringUtils.isBlank;
import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("java:S6548")
public class PlaybackApplicationService {
    private static final int MAX_HLS_RESOLUTION_DEPTH = 8;
    private static final String CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36";
    private final PlayerRequestResolver playerRequestResolver = new PlayerRequestResolver();

    private PlaybackApplicationService() {
    }

    public static PlaybackApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public PlaybackPlaylistResult buildPlaylist(PlaybackPlaylistRequest request) throws IOException {
        Account account = AccountService.getInstance().getById(request.accountId());
        Channel channel = ChannelDb.get().getChannelById(request.channelId(), request.categoryId());
        HandshakeService.getInstance().hardTokenRefresh(account);
        String originalCmd = channel.getCmd();
        channel.setCmd(URLDecoder.decode(originalCmd, UTF_8));

        PlayerResponse playerResponse = PlayerService.getInstance().get(account, channel);
        String cmd = playerResponse.getUrl();

        channel.setCmd(originalCmd);

        String channelName = sanitizeTitle(channel.getName());
        String response = "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"" + escapeAttributeValue(account.getDbId()) + "\" tvg-name=\"" + escapeAttributeValue(channelName)
                + "\" group-title=\"" + escapeAttributeValue(account.getAccountName()) + "\"," + channelName + "\n"
                + StringUtils.EMPTY + cmd + "\n";
        String filename = request.accountId() + "-" + request.categoryId() + "-" + request.channelId() + ".m3u8";
        return new PlaybackPlaylistResult(response, filename);
    }

    public String resolveBookmarkRedirectUrl(String bookmarkId) throws IOException {
        BookmarkRedirectResult result = resolveBookmarkRedirect(bookmarkId);
        return result == null ? "" : result.url();
    }

    public BookmarkRedirectResult resolveBookmarkRedirect(String bookmarkId) throws IOException {
        Bookmark bookmark = BookmarkApplicationService.getInstance().getBookmark(bookmarkId);
        if (bookmark == null) {
            return null;
        }
        Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
        if (account == null) {
            return null;
        }
        PlayerResponse response = playerRequestResolver.resolveBookmarkPlayback(bookmark.getDbId(), "", "");
        if (response == null || isBlank(response.getUrl())) {
            return null;
        }
        return new BookmarkRedirectResult(resolveBookmarkRedirectChain(response.getUrl(), account), response);
    }

    private String resolveBookmarkRedirectChain(String url, Account account) {
        if (!ConfigurationService.getInstance().isResolveChainAndDeepRedirectsEnabled(account)) {
            return url;
        }
        return HlsPlaylistResolver.resolveHlsPlaylistChain(url, createBrowserHeaders(), MAX_HLS_RESOLUTION_DEPTH);
    }

    private Map<String, String> createBrowserHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (ConfigurationService.getInstance().isVlcHttpUserAgentEnabled()) {
            headers.put("User-Agent", CHROME_USER_AGENT);
        }
        headers.put("Accept", "application/vnd.apple.mpegurl, */*");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        return headers;
    }

    public record BookmarkRedirectResult(String url, PlayerResponse response) {
    }

    private static class SingletonHelper {
        private static final PlaybackApplicationService INSTANCE = new PlaybackApplicationService();
    }
}
