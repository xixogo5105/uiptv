package com.uiptv.shared.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
}
