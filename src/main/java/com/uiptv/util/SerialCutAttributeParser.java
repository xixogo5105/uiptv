package com.uiptv.util;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerialCutAttributeParser implements StalkerAttributeParser {
    private static final Pattern LABEL_PATTERN = Pattern.compile("(?i)sncut|serialcut|sn\\s*cut|serial\\s*cut");
    private static final Pattern HEX_PATTERN = Pattern.compile("([A-F0-9]{13,16})", Pattern.CASE_INSENSITIVE);

    @Override
    public String parse(String line) {
        String normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD);
        Matcher labelMatcher = LABEL_PATTERN.matcher(normalizedLine);
        if (!labelMatcher.find()) {
            return null;
        }
        Matcher hexMatcher = HEX_PATTERN.matcher(normalizedLine);
        if (hexMatcher.find()) {
            return hexMatcher.group(1).toUpperCase();
        }
        return null;
    }

    @Override
    public StalkerAttributeType getAttributeType() {
        return StalkerAttributeType.SERIAL_CUT;
    }
}
