package com.uiptv.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Interface for attribute parsers used by the StalkerPortalParser.
 */
public interface StalkerAttributeParser {
    
    /**
     * Parses the line and returns the extracted value, or null if not found.
     */
    String parse(String line);

    /**
     * Returns the type of attribute this parser handles.
     */
    StalkerAttributeType getAttributeType();

    default boolean lineContainsKeyword(String line, List<String> keywords) {
        String normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKC).toLowerCase();
        return keywords.stream().anyMatch(normalizedLine::contains);
    }

    default String extractHexValueFromLine(String line) {
        String normalized = Normalizer.normalize(line, Normalizer.Form.NFKC);
        String cleaned = normalized.replaceAll("[^\\x20-\\x7E]", " ").trim();
        List<String> tokens = new ArrayList<>(List.of(cleaned.split("\\s+")));
        Collections.reverse(tokens);
        for (String token : tokens) {
            if (Pattern.compile("^[0-9A-Fa-f]{6,}$").matcher(token).matches()) {
                return token;
            }
        }
        return null;
    }
}
