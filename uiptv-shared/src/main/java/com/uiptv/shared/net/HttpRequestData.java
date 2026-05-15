package com.uiptv.shared.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Objects;

public record HttpRequestData(String url,
                              String method,
                              Map<String, String> headers,
                              byte[] body,
                              boolean followRedirects,
                              int timeoutSeconds) {
    public HttpRequestData {
        headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        body = body == null ? new byte[0] : body.clone();
        method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HttpRequestData(String thatUrl,
                                                String thatMethod,
                                                Map<String, String> thatHeaders,
                                                byte[] thatBody,
                                                boolean thatFollowRedirects,
                                                int thatTimeoutSeconds)
                && followRedirects == thatFollowRedirects
                && timeoutSeconds == thatTimeoutSeconds
                && Objects.equals(url, thatUrl)
                && Objects.equals(method, thatMethod)
                && Objects.equals(headers, thatHeaders)
                && Arrays.equals(body, thatBody);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(url, method, headers, followRedirects, timeoutSeconds);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "HttpRequestData[url=" + url
                + ", method=" + method
                + ", headers=" + headers
                + ", body=" + Arrays.toString(body)
                + ", followRedirects=" + followRedirects
                + ", timeoutSeconds=" + timeoutSeconds
                + "]";
    }
}
