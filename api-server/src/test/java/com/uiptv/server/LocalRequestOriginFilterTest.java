package com.uiptv.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class LocalRequestOriginFilterTest {
    @Test
    void rejectsCrossOriginBrowserRequests() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getHeader("Host")).thenReturn("127.0.0.1:8080");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getHeader("Origin")).thenReturn("http://attacker.example:8080");

        new LocalRequestOriginFilter().doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsDnsRebindingHostnamesEvenWhenOriginMatches() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getHeader("Host")).thenReturn("attacker.example:8080");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getHeader("Origin")).thenReturn("http://attacker.example:8080");

        new LocalRequestOriginFilter().doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void permitsSameOriginRequestToLoopback() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getHeader("Host")).thenReturn("localhost:8080");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getHeader("Origin")).thenReturn("http://localhost:8080");

        new LocalRequestOriginFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(response);
    }
}
