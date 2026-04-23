package com.uiptv.util;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerialAttributeParser implements StalkerAttributeParser {
    private static final Pattern SERIAL_PATTERN = Pattern.compile("(?:sn|serial).*?([A-F0-9]{10,32})\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String parse(String line) {
        String normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD);
        Matcher matcher = SERIAL_PATTERN.matcher(normalizedLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public StalkerAttributeType getAttributeType() {
        return StalkerAttributeType.SERIAL;
    }
}
