package com.uiptv.application;

import com.uiptv.model.Channel;
import com.uiptv.service.ChannelService;
import com.uiptv.util.ReloadRequestScope;
import org.json.*;
import java.io.Reader;
import java.util.*;

/** Reads a bulk catalogue one channel at a time, without retaining the response JSON tree. */
public final class StalkerCatalogueReader {
    public List<Channel> read(Reader source) {
        JSONTokener input = new JSONTokener(source);
        Map<String, Channel> channels = new LinkedHashMap<>();
        object(input, channels);
        return new ArrayList<>(channels.values());
    }

    private void object(JSONTokener input, Map<String, Channel> channels) {
        expect(input, '{');
        if (input.nextClean() == '}') return;
        input.back();
        do {
            ReloadRequestScope.checkCurrent();
            readObjectEntry(input, channels);
            char next = input.nextClean();
            if (next == '}') return;
            if (next != ',') throw input.syntaxError("Expected catalogue object separator");
        } while (true);
    }

    private void readObjectEntry(JSONTokener input, Map<String, Channel> channels) {
        String key = String.valueOf(input.nextValue());
        expect(input, ':');
        char next = input.nextClean();
        input.back();
        if ("js".equals(key) && next == '{') {
            object(input, channels);
        } else if (("js".equals(key) || "data".equals(key)) && next == '[') {
            array(input, channels);
        } else {
            skip(input);
        }
    }

    private void array(JSONTokener input, Map<String, Channel> channels) {
        expect(input, '[');
        if (input.nextClean() == ']') return;
        input.back();
        do {
            ReloadRequestScope.checkCurrent();
            JSONObject row = new JSONObject(input);
            Channel channel = ChannelService.getInstance().parseItvChannel(row);
            String key = String.join("|", value(channel.getChannelId()), value(channel.getCmd()),
                    value(channel.getName()).toLowerCase());
            channels.putIfAbsent(key, channel);
            char next = input.nextClean();
            if (next == ']') return;
            if (next != ',') throw input.syntaxError("Expected channel separator");
        } while (true);
    }

    private String value(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
    private void expect(JSONTokener input, char expected) {
        if (input.nextClean() != expected) throw input.syntaxError("Expected " + expected);
    }

    /** Skip unrelated envelope metadata without constructing potentially large JSON objects. */
    @SuppressWarnings("java:S3776")
    private void skip(JSONTokener input) {
        // This is a deliberately linear JSON tokenizer state machine. Splitting its quote,
        // escape, and nesting transitions would obscure the streaming parser invariants.
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        char quote = 0;
        do {
            ReloadRequestScope.checkCurrent();
            char next = input.next();
            if (next == 0) throw input.syntaxError("Unexpected end of catalogue");
            if (quoted) {
                if (escaped) escaped = false;
                else if (next == '\\') escaped = true;
                else if (next == quote) quoted = false;
                continue;
            }
            if (next == '"' || next == '\'') {
                quoted = true;
                quote = next;
            } else if (next == '[' || next == '{') {
                depth++;
            } else if (next == ']' || next == '}') {
                if (depth == 0) { input.back(); return; }
                depth--;
            } else if (next == ',' && depth == 0) {
                input.back();
                return;
            }
        } while (true);
    }
}
