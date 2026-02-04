package com.uiptv.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacAttributeParser implements StalkerAttributeParser {
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)(([0-9A-F]{2}[:-]){5}([0-9A-F]{2}))");

    @Override
    public String parse(String line) {
        Matcher m = MAC_PATTERN.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    @Override
    public StalkerAttributeType getAttributeType() {
        return StalkerAttributeType.MAC;
    }
}
