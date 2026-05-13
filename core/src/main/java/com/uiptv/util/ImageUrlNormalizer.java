package com.uiptv.util;

import com.uiptv.model.Account;

import java.net.URI;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public final class ImageUrlNormalizer {
    private ImageUrlNormalizer() {
    }

    public static String normalizeImageUrl(String imageUrl, Account account) {
        if (isBlank(imageUrl)) {
            return "";
        }
        String value = imageUrl.trim().replace("\\/", "/");
        value = trimWrappedImageQuotes(value);
        if (isBlank(value)) {
            return "";
        }
        if (isAbsoluteImageUrl(value) || isInlineImageUrl(value)) {
            return value;
        }
        URI base = resolveBaseUri(account);
        String scheme = resolveBaseScheme(base);
        String host = resolveBaseHost(base);
        int port = base == null ? -1 : base.getPort();
        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }
        if (value.startsWith("/")) {
            return buildRootRelativeImageUrl(value, scheme, host, port);
        }
        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*") && isBlank(host)) {
            int slash = value.indexOf('/');
            String hostCandidate = slash > 0 ? value.substring(0, slash) : value;
            if (hostCandidate.contains(".") || "localhost".equalsIgnoreCase(hostCandidate)) {
                return scheme + "://" + value;
            }
        }
        return buildRelativeImageUrl(value, scheme, host, port);
    }

    private static URI resolveBaseUri(Account account) {
        if (account == null) {
            return null;
        }
        List<String> candidates = new java.util.ArrayList<>();
        if (!isBlank(account.getServerPortalUrl())) {
            candidates.add(account.getServerPortalUrl());
        }
        if (!isBlank(account.getUrl())) {
            candidates.add(account.getUrl());
        }
        for (String candidate : candidates) {
            URI resolved = parseCandidateBaseUri(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static String resolveBaseScheme(URI base) {
        return base != null && !isBlank(base.getScheme()) ? base.getScheme() : "https";
    }

    private static String resolveBaseHost(URI base) {
        return base != null && !isBlank(base.getHost()) ? base.getHost() : "";
    }

    private static String trimWrappedImageQuotes(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            return trimmed;
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean isAbsoluteImageUrl(String value) {
        return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }

    private static boolean isInlineImageUrl(String value) {
        return value.startsWith("data:") || value.startsWith("blob:") || value.startsWith("file:");
    }

    private static String buildRootRelativeImageUrl(String value, String scheme, String host, int port) {
        if (!isBlank(host)) {
            return scheme + "://" + host + formatPort(port) + value;
        }
        return value;
    }

    private static String buildRelativeImageUrl(String value, String scheme, String host, int port) {
        String normalized = value.startsWith("./") ? value.substring(2) : value;
        if (!isBlank(host)) {
            return scheme + "://" + host + formatPort(port) + "/" + normalized;
        }
        return ServerUrlUtil.getLocalServerUrl() + "/" + normalized;
    }

    private static String formatPort(int port) {
        return port > 0 ? ":" + port : "";
    }

    private static URI parseCandidateBaseUri(String candidate) {
        if (isBlank(candidate)) {
            return null;
        }
        try {
            URI uri = URI.create(candidate.trim());
            if (!isBlank(uri.getHost())) {
                return uri;
            }
            if (isBlank(uri.getScheme())) {
                URI withScheme = URI.create("http://" + candidate.trim());
                if (!isBlank(withScheme.getHost())) {
                    return withScheme;
                }
            }
        } catch (Exception _) {
            // Invalid image/base URIs should not break rendering.
        }
        return null;
    }
}
