package com.uiptv.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Enumeration;
import java.util.Map;

public final class JettyHttpHandlerAdapter extends HttpServlet {
    private final HttpHandler delegate;

    public JettyHttpHandlerAdapter(HttpHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        DelegatingHttpExchange httpExchange = new DelegatingHttpExchange(req, resp);
        try {
            delegate.handle(httpExchange);
        } finally {
            httpExchange.finish();
        }
    }

    private static final class DelegatingHttpExchange extends HttpExchange {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final URI requestUri;
        private final InputStream requestBody;
        private final OutputStream responseBody;
        private int responseCode = 200;
        private long responseLength = Long.MIN_VALUE;
        private boolean responseCommitted;
        private boolean finished;

        private DelegatingHttpExchange(HttpServletRequest request, HttpServletResponse response) throws IOException {
            this.request = request;
            this.response = response;
            
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    requestHeaders.add(name, values.nextElement());
                }
            }
            
            String queryString = request.getQueryString();
            String requestUrl = request.getRequestURL().toString();
            this.requestUri = URI.create(queryString == null || queryString.isEmpty() ? requestUrl : requestUrl + "?" + queryString);
            this.requestBody = request.getInputStream();
            this.responseBody = response.getOutputStream();
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return request.getMethod();
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            try {
                responseBody.close();
            } catch (IOException _) {
            }
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            if (responseCommitted) {
                return;
            }
            responseCommitted = true;
            responseCode = rCode;
            this.responseLength = responseLength;
            response.setStatus(rCode);
            
            for (Map.Entry<String, java.util.List<String>> entry : responseHeaders.entrySet()) {
                for (String value : entry.getValue()) {
                    if (responseHeaders.get(entry.getKey()) != null) {
                        response.addHeader(entry.getKey(), value);
                    } else {
                        response.setHeader(entry.getKey(), value);
                    }
                }
            }
            
            if (responseLength > 0) {
                response.setContentLengthLong(responseLength);
            }
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            String remoteAddr = request.getRemoteAddr();
            int remotePort = request.getRemotePort();
            return new InetSocketAddress(remoteAddr, remotePort);
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            String localAddr = request.getLocalAddr();
            int localPort = request.getLocalPort();
            return new InetSocketAddress(localAddr, localPort);
        }

        @Override
        public String getProtocol() {
            return request.getProtocol();
        }

        @Override
        public Object getAttribute(String name) {
            return request.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            request.setAttribute(name, value);
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private void finish() throws IOException {
            if (!responseCommitted) {
                sendResponseHeaders(responseCode, responseLength == Long.MIN_VALUE ? -1 : responseLength);
            }
            if (responseLength <= 0) {
                if (!finished) {
                    finished = true;
                }
            } else {
                responseBody.flush();
            }
        }
    }
}