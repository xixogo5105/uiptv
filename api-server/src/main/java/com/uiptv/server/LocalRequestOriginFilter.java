package com.uiptv.server;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import static com.uiptv.util.ServerUrlUtil.getServerBindAddresses;

/** Rejects DNS-rebinding Host values and browser requests initiated by another origin. */
final class LocalRequestOriginFilter implements Filter {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String hostHeader = httpRequest.getHeader("Host");
        Authority host = parseAuthority(httpRequest.getScheme() + "://" + hostHeader);
        if (host == null || !isLocalHost(host.host()) || host.port() != httpRequest.getServerPort()) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String originHeader = httpRequest.getHeader("Origin");
        if (originHeader != null) {
            Authority origin = parseAuthority(originHeader);
            if (origin == null || !origin.equals(host)) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        String fetchSite = httpRequest.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite)) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLocalHost(String host) {
        String normalized = normalizeHost(host);
        if (LOOPBACK_HOSTS.contains(normalized)) return true;
        return getServerBindAddresses().stream().map(LocalRequestOriginFilter::normalizeHost)
                .anyMatch(normalized::equals);
    }

    private static Authority parseAuthority(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            int port = uri.getPort();
            if (port < 0) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            return new Authority(uri.getScheme().toLowerCase(Locale.ROOT), normalizeHost(uri.getHost()), port);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]"))
            normalized = normalized.substring(1, normalized.length() - 1);
        return normalized;
    }

    private record Authority(String scheme, String host, int port) {
    }
}
