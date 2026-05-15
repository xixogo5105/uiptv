package com.uiptv.shared.net;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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

    @Override
    public boolean equals(Object other) {
        return other instanceof HttpResponseData that
                && statusCode == that.statusCode
                && Objects.equals(headers, that.headers)
                && Arrays.equals(body, that.body)
                && Objects.equals(finalUrl, that.finalUrl);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(statusCode, headers, finalUrl);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "HttpResponseData[statusCode=" + statusCode
                + ", headers=" + headers
                + ", body=" + Arrays.toString(body)
                + ", finalUrl=" + finalUrl
                + "]";
    }
}
