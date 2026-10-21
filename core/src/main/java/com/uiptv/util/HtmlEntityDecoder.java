package com.uiptv.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes common HTML character references in provider-supplied display text. */
public final class HtmlEntityDecoder {
    private static final Pattern ENTITY = Pattern.compile(
            "&(?:#(?:(\\d+)|[xX]([0-9a-fA-F]+))|([A-Za-z][A-Za-z0-9]+));");
    private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", "\u00a0"),
            Map.entry("rsquo", "\u2019"), Map.entry("lsquo", "\u2018"),
            Map.entry("rdquo", "\u201d"), Map.entry("ldquo", "\u201c"),
            Map.entry("ndash", "\u2013"), Map.entry("mdash", "\u2014"));
    private static final int MAX_PASSES = 3;

    private HtmlEntityDecoder() {
    }

    public static String decode(String value) {
        if (value == null || value.isEmpty() || value.indexOf('&') < 0) return value;
        String decoded = value;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String next = decodePass(decoded);
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded;
    }

    private static String decodePass(String value) {
        Matcher matcher = ENTITY.matcher(value);
        StringBuilder decoded = new StringBuilder(value.length());
        int lastEnd = 0;
        while (matcher.find()) {
            decoded.append(value, lastEnd, matcher.start());
            String replacement = matcher.group(3) == null
                    ? decodeNumeric(matcher)
                    : NAMED_ENTITIES.get(matcher.group(3));
            decoded.append(replacement == null ? matcher.group() : replacement);
            lastEnd = matcher.end();
        }
        return decoded.append(value, lastEnd, value.length()).toString();
    }

    private static String decodeNumeric(Matcher matcher) {
        try {
            int codePoint = matcher.group(1) != null
                    ? Integer.parseInt(matcher.group(1))
                    : Integer.parseInt(matcher.group(2), 16);
            if (Character.isValidCodePoint(codePoint)
                    && (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE)) {
                return new String(Character.toChars(codePoint));
            }
        } catch (NumberFormatException _) {
            // Preserve malformed or out-of-range entities verbatim.
        }
        return null;
    }
}
