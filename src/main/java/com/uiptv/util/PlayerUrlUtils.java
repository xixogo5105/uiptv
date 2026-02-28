package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.player.YoutubeDL;

import java.net.URI;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.isBlank;

public class PlayerUrlUtils {

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

        if (lower.startsWith("ffmpeg ")) {
            return value.substring("ffmpeg ".length()).trim();
        }
        if (lower.startsWith("ffmpeg+")) {
            return value.substring("ffmpeg+".length()).trim();
        }
        if (lower.startsWith("ffmpeg%20")) {
            return value.substring("ffmpeg%20".length()).trim();
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

        String scheme = "http";
        try {
            String portal = account == null ? null : account.getServerPortalUrl();
            if (!isBlank(portal)) {
                URI portalUri = URI.create(portal.trim());
                if (!isBlank(portalUri.getScheme())) {
                    scheme = portalUri.getScheme();
                }
            }
        } catch (Exception ignored) {
        }

        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            // Some Stalker providers return https links that are actually served over http.
            // Align transport with the portal scheme for known playback paths.
            if (account != null && account.getType() == STALKER_PORTAL
                    && "http".equalsIgnoreCase(scheme)
                    && value.toLowerCase().startsWith("https://")
                    && (value.toLowerCase().contains("/live/play/") || value.toLowerCase().contains("/play/movie.php"))) {
                return "http://" + value.substring("https://".length());
            }
            return value;
        }

        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }

        if (value.startsWith("/")) {
            try {
                String portal = account == null ? null : account.getServerPortalUrl();
                if (!isBlank(portal)) {
                    URI portalUri = URI.create(portal.trim());
                    String host = portalUri.getHost();
                    int port = portalUri.getPort();
                    if (!isBlank(host)) {
                        return scheme + "://" + host + (port > 0 ? ":" + port : "") + value;
                    }
                }
            } catch (Exception ignored) {
            }
            return value;
        }

        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
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
        if (normalized.startsWith("ffmpeg ")) {
            normalized = normalized.substring("ffmpeg ".length()).trim();
        }
        // Reject known broken pattern with empty stream parameter.
        if (normalized.contains("stream=&")) return false;
        return true;
    }
}
