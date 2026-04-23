package com.uiptv.util;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceId2AttributeParser implements StalkerAttributeParser {
    private static final Pattern DEVICE_ID2_PATTERN = Pattern.compile("(?:id\\s*2|device\\s*id\\s*2).*?([A-F0-9]{10,64})\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String parse(String line) {
        String normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD);
        Matcher matcher = DEVICE_ID2_PATTERN.matcher(normalizedLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public StalkerAttributeType getAttributeType() {
        return StalkerAttributeType.DEVICE_ID_2;
    }
}
