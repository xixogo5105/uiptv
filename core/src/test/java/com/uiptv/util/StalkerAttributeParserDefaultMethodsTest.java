package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StalkerAttributeParserDefaultMethodsTest {

    private final StalkerAttributeParser parser = new StalkerAttributeParser() {
        @Override
        public String parse(String line) {
            return line;
        }

        @Override
        public StalkerAttributeType getAttributeType() {
            return StalkerAttributeType.MAC;
        }
    };

    @Test
    void lineContainsKeyword_usesNormalizedCaseInsensitiveMatching() {
        assertTrue(parser.lineContainsKeyword("ＭＡＣ Address Present", List.of("mac")));
        assertTrue(parser.lineContainsKeyword("Device serial number", List.of("serial", "signature")));
    }

    @Test
    void extractHexValueFromLine_returnsLastMeaningfulHexToken() {
        assertEquals("AABBCCDDEEFF", parser.extractHexValueFromLine("MAC => 12345 AABBCCDDEEFF"));
        assertEquals("abcdef12", parser.extractHexValueFromLine("noise abcdef12\u0007"));
        assertNull(parser.extractHexValueFromLine("MAC => xx-yy"));
    }
}
