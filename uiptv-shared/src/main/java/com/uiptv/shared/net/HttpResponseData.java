package com.uiptv.shared.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record HttpResponseData(int statusCode,
                               Map<String, String> headers,
                               byte[] body,
                               String finalUrl) {
    public HttpResponseData {
        headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        body = body == null ? new byte[0] : body.clone();
        finalUrl = finalUrl == null ? "" : finalUrl;
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
