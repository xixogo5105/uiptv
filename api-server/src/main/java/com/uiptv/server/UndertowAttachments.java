package com.uiptv.server;

import io.undertow.util.AttachmentKey;

import java.util.HashMap;
import java.util.Map;

final class UndertowAttachments {
    private static final AttachmentKey<Map<String, Object>> ATTRIBUTES = AttachmentKey.create(Map.class);

    private UndertowAttachments() {
    }

    static AttachmentKey<Map<String, Object>> attributes() {
        return ATTRIBUTES;
    }

    static Map<String, Object> newAttributes() {
        return new HashMap<>();
    }
}
