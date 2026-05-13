package com.uiptv.shared.net;

import java.io.IOException;

@FunctionalInterface
public interface HttpClientPort {
    HttpResponseData execute(HttpRequestData request) throws IOException;
}
