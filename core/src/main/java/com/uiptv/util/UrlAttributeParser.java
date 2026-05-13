package com.uiptv.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlAttributeParser implements StalkerAttributeParser {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    @Override
    public String parse(String line) {
        Matcher m = URL_PATTERN.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    @Override
    public StalkerAttributeType getAttributeType() {
        return StalkerAttributeType.URL;
    }
}
