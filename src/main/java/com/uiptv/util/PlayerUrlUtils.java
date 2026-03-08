package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.player.YoutubeDL;

import java.net.URI;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.isBlank;

public class PlayerUrlUtils {
    private static final String FFMPEG_PREFIX = "ffmpeg ";
    private static final String FFMPEG_PLUS_PREFIX = FFMPEG_PREFIX.replace(" ", "+");
    private static final String FFMPEG_URL_ENCODED_PREFIX = "ffmpeg%20";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";

    private PlayerUrlUtils() {
    }

    public static String resolveAndProcessUrl(String url) {
        if (isBlank(url)) return url;

        String processedUrl = extractPlayableUrl(url);

        // Check if the link is a YouTube video URL
        if (processedUrl != null && (processedUrl.contains("youtube.com/watch?v=") || processedUrl.contains("youtu.be/"))) {
            String streamingUrl = YoutubeDL.getStreamingUrl(processedUrl);
            if (streamingUrl != null && !streamingUrl.isEmpty()) {
                return streamingUrl;
            }
        }
        return processedUrl;
    }

    public static String extractPlayableUrl(String raw) {
        if (isBlank(raw)) {
            return raw;
        }
        String value = raw.trim();
        String lower = value.toLowerCase();

        if (lower.startsWith(FFMPEG_PREFIX)) {
            return value.substring(FFMPEG_PREFIX.length()).trim();
        }
        if (lower.startsWith(FFMPEG_PLUS_PREFIX)) {
            return value.substring(FFMPEG_PLUS_PREFIX.length()).trim();
        }
        if (lower.startsWith(FFMPEG_URL_ENCODED_PREFIX)) {
            return value.substring(FFMPEG_URL_ENCODED_PREFIX.length()).trim();
        }

        String[] uriParts = value.split(" ");
        if (uriParts.length > 1) {
            return uriParts[uriParts.length - 1];
        }

        return value;
    }

    public static String normalizeStreamUrl(Account account, String url) {
        if (isBlank(url)) {
            return url;
        }

        String value = url.trim();
        URI portalUri = resolvePortalUri(account);
        String scheme = resolvePortalScheme(portalUri);

        if (isAbsoluteUrl(value)) {
            return alignStalkerPlaybackScheme(account, value, scheme);
        }

        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }

        if (value.startsWith("/")) {
            return prependPortalHost(value, scheme, portalUri);
        }

        if (isHostPathLike(value)) {
            return scheme + "://" + value;
        }

        return value;
    }

    public static String resolveBestChannelCmd(Account account, Channel channel) {
        if (channel == null) return "";
        String primary = channel.getCmd();
        if (account == null) return primary;

        // For Stalker live channels, some portals expose multiple cmd variants and
        // the first one can contain an empty stream id (stream=), causing 405.
        if (account.getType() == STALKER_PORTAL && account.getAction() == Account.AccountAction.itv) {
            String[] candidates = new String[]{channel.getCmd(), channel.getCmd_1(), channel.getCmd_2(), channel.getCmd_3()};
            for (String c : candidates) {
                if (isUsableLiveCmd(c)) return c;
            }
            return primary;
        }
        return primary;
    }

    public static boolean isUsableLiveCmd(String cmd) {
        if (isBlank(cmd)) return false;
        String normalized = cmd.trim().toLowerCase();
        if (normalized.startsWith(FFMPEG_PREFIX)) {
            normalized = normalized.substring(FFMPEG_PREFIX.length()).trim();
        }
        // Reject known broken pattern with empty stream parameter.
        if (normalized.contains("stream=&")) return false;
        return true;
    }

    private static URI resolvePortalUri(Account account) {
        try {
            String portal = account == null ? null : account.getServerPortalUrl();
            if (!isBlank(portal)) {
                return URI.create(portal.trim());
            }
        } catch (Exception _) {
            // Invalid portal URLs should simply disable portal-relative URL rewriting.
        }
        return null;
    }

    private static String resolvePortalScheme(URI portalUri) {
        if (portalUri != null && !isBlank(portalUri.getScheme())) {
            return portalUri.getScheme();
        }
        return HTTP_SCHEME;
    }

    private static boolean isAbsoluteUrl(String value) {
        return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }

    private static boolean isHostPathLike(String value) {
        return value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*");
    }

    private static String alignStalkerPlaybackScheme(Account account, String value, String scheme) {
        String lowerValue = value.toLowerCase();
        if (account != null
                && account.getType() == STALKER_PORTAL
                && HTTP_SCHEME.equalsIgnoreCase(scheme)
                && lowerValue.startsWith(HTTPS_PREFIX)
                && (lowerValue.contains("/live/play/") || lowerValue.contains("/play/movie.php"))) {
            return HTTP_PREFIX + value.substring(HTTPS_PREFIX.length());
        }
        return value;
    }

    private static String prependPortalHost(String value, String scheme, URI portalUri) {
        if (portalUri != null && !isBlank(portalUri.getHost())) {
            int port = portalUri.getPort();
            return scheme + "://" + portalUri.getHost() + (port > 0 ? ":" + port : "") + value;
        }
        return value;
    }
}
