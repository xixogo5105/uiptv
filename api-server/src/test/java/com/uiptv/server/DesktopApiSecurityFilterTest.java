package com.uiptv.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class DesktopApiSecurityFilterTest {
    private static final String TOKEN = "per-run-test-token";

    @Test
    void rejectsNonLoopbackApiRequests() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("192.168.1.20");

        new DesktopApiSecurityFilter(TOKEN).doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        new DesktopApiSecurityFilter(TOKEN).doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void permitsLoopbackRequestWithBearerToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);

        new DesktopApiSecurityFilter(TOKEN).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
