package com.uiptv.server;

import com.uiptv.util.WebActivityLog;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class WebRequestActivityLoggingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        long startedAt = System.nanoTime();
        Exception failure = null;
        try {
            chain.doFilter(request, response);
        } catch (Exception exception) {
            failure = exception;
            throw exception;
        } finally {
            int statusCode = failure == null ? resp.getStatus() : Math.max(500, resp.getStatus());
            WebActivityLog.recordRequest(
                    req.getMethod(),
                    req.getRequestURI(),
                    req.getQueryString(),
                    requestIp(req),
                    statusCode,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    activityDescription(req)
            );
        }
    }

    @Override
    public void destroy() {
    }

    private static String activityDescription(HttpServletRequest req) {
        Object description = req.getAttribute(WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE);
        return description instanceof String value ? value : "";
    }

    private static String requestIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return "";
        }
        return remoteAddr;
    }
}