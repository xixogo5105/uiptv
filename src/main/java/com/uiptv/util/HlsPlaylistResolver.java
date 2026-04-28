package com.uiptv.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.uiptv.util.StringUtils.isBlank;

public final class HlsPlaylistResolver {
    private HlsPlaylistResolver() {
    }

    public static String resolveHlsPlaylistChain(String uri, Map<String, String> requestHeaders, int maxDepth) {
        return resolveHlsPlaylistChain(uri, requestHeaders, maxDepth, new LinkedHashSet<>(), 0);
    }

    private static String resolveHlsPlaylistChain(String uri,
                                                  Map<String, String> requestHeaders,
                                                  int maxDepth,
                                                  Set<String> visited,
                                                  int depth) {
        if (uri == null) {
            return null;
        }
        String normalizedUri = uri.trim();
        if (normalizedUri.isEmpty()) {
            return uri;
        }
        if (!visited.add(normalizedUri) || depth >= maxDepth || !isLikelyManifest(normalizedUri)) {
            return normalizedUri;
        }

        try {
            HttpUtil.HttpResult result = HttpUtil.sendRequest(normalizedUri, requestHeaders, "GET");
            if (result == null || result.statusCode() != 200 || isBlank(result.body()) || !isMasterManifest(result.body())) {
                return normalizedUri;
            }
            String effectiveBaseUri = isBlank(result.requestUri()) ? normalizedUri : result.requestUri();
            String variantUrl = extractBestVariantUrl(effectiveBaseUri, result.body());
            if (isBlank(variantUrl) || variantUrl.equals(normalizedUri)) {
                return normalizedUri;
            }
            return resolveHlsPlaylistChain(variantUrl, requestHeaders, maxDepth, visited, depth + 1);
        } catch (Exception _) {
            return normalizedUri;
        }
    }

    private static boolean isLikelyManifest(String uri) {
        String path = uri.split("\\?")[0].toLowerCase();
        if (path.endsWith(".m3u8") || path.endsWith(".m3u")) {
            return true;
        }
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return !lastSegment.contains(".");
    }

    private static boolean isMasterManifest(String body) {
        return body.startsWith("#EXTM3U") && body.contains("#EXT-X-STREAM-INF");
    }

    private static String extractBestVariantUrl(String baseUrl, String playlistContent) {
        String[] lines = playlistContent.split("\\r?\\n");
        String bestVariant = null;
        long maxBandwidth = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF:") && i + 1 < lines.length) {
                long bandwidth = parseBandwidth(line);
                String candidate = lines[i + 1].trim();
                if (!candidate.isEmpty() && !candidate.startsWith("#") && bandwidth > maxBandwidth) {
                    maxBandwidth = bandwidth;
                    bestVariant = candidate;
                }
            }
        }
        return isBlank(bestVariant) ? null : resolveVariantUrl(baseUrl, bestVariant);
    }

    private static long parseBandwidth(String line) {
        try {
            int idx = line.toUpperCase().indexOf("BANDWIDTH=");
            if (idx < 0) {
                return 0;
            }
            String suffix = line.substring(idx + "BANDWIDTH=".length());
            int comma = suffix.indexOf(',');
            String value = comma >= 0 ? suffix.substring(0, comma) : suffix;
            return Long.parseLong(value.trim());
        } catch (Exception _) {
            return 0;
        }
    }

    private static String resolveVariantUrl(String baseUrl, String variantUrl) {
        try {
            java.net.URI base = java.net.URI.create(baseUrl);
            java.net.URI resolved = base.resolve(variantUrl).normalize();
            if (base.getQuery() != null && !variantUrl.contains("?")) {
                String resolvedStr = resolved.toString();
                return resolvedStr.contains("?") ? resolvedStr : resolvedStr + "?" + base.getQuery();
            }
            return resolved.toString();
        } catch (Exception _) {
            return null;
        }
    }
}
