package com.uiptv.util;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignatureAttributeParser implements StalkerAttributeParser {
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?:signature|signature1|sig).*?([A-F0-9]{64})\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String parse(String line) {
        String normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD);
        Matcher matcher = SIGNATURE_PATTERN.matcher(normalizedLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public StalkerAttributeType getAttributeType() {
        return StalkerAttributeType.SIGNATURE;
    }
}
