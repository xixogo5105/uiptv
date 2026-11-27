package com.uiptv.server;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DesktopApiSecurityFilter implements Filter {
    private static final String BEARER_PREFIX = "Bearer ";
    private final String expectedToken;

    public DesktopApiSecurityFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }

        if (!isLoopbackAddress(httpRequest.getRemoteAddr())) {
            writeError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Desktop API is loopback-only");
            return;
        }

        String authorization = httpRequest.getHeader("Authorization");
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || !constantTimeEquals(expectedToken, authorization.substring(BEARER_PREFIX.length()).trim())) {
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid desktop API token");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isLoopbackAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        String address = remoteAddress.trim().toLowerCase(Locale.ROOT);
        if (address.startsWith("[")) {
            address = address.substring(1);
            int bracket = address.indexOf(']');
            if (bracket >= 0) {
                address = address.substring(0, bracket);
            }
        }
        if ("localhost".equals(address) || "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address)) {
            return true;
        }
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
        int length = Math.max(expectedBytes.length, suppliedBytes.length);
        int result = expectedBytes.length ^ suppliedBytes.length;
        for (int i = 0; i < length; i++) {
            byte expectedByte = i < expectedBytes.length ? expectedBytes[i] : 0;
            byte suppliedByte = i < suppliedBytes.length ? suppliedBytes[i] : 0;
            result |= expectedByte ^ suppliedByte;
        }
        return result == 0;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
