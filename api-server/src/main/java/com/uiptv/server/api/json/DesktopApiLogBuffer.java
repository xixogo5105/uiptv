package com.uiptv.server.api.json;

import com.uiptv.util.AppLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class DesktopApiLogBuffer {
    private static final int MAX_LINES = 2_000;
    private static final Pattern URL_CREDENTIAL = Pattern.compile(
            "(?i)([?&](?:username|password|token|auth|api_key|key|mac)=)[^&\\s]+"
    );
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)\\b(password|token|authorization|cookie|secret)\\s*[:=]\\s*([^\\s,;&]+)"
    );
    private static final Pattern URL_USER_INFO = Pattern.compile(
            "(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@"
    );
    private static final ArrayDeque<String> lines = new ArrayDeque<>();

    static {
        AppLog.registerListener(DesktopApiLogBuffer::append);
    }

    private DesktopApiLogBuffer() {
    }

    static void initialize() {
    }

    static synchronized List<String> readAll() {
        return new ArrayList<>(lines);
    }

    static synchronized void clear() {
        lines.clear();
    }

    private static synchronized void append(String message) {
        String safe = redact(message);
        lines.addLast(safe);
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    static String redact(String message) {
        String safe = URL_CREDENTIAL.matcher(message).replaceAll("$1[redacted]");
        safe = KEY_VALUE_SECRET.matcher(safe).replaceAll("$1=[redacted]");
        return URL_USER_INFO.matcher(safe).replaceAll("$1[redacted]@");
    }
}