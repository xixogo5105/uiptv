package com.uiptv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlEntityDecoderTest {
    @Test
    void decodesNumericNamedAndNestedEntitiesForDisplay() {
        assertEquals("Women and 's T20 Cricket Highlights", HtmlEntityDecoder.decode("Women and &#039;s T20 Cricket Highlights"));
        assertEquals("A 🎃 & B", HtmlEntityDecoder.decode("A &#x1F383; &amp; B"));
        assertEquals("Don't stop — now", HtmlEntityDecoder.decode("Don&apos;t stop &mdash; now"));
        assertEquals("A & B", HtmlEntityDecoder.decode("A &amp;amp; B"));
    }

    @Test
    void preservesMalformedReferencesAndNullValues() {
        assertEquals("&#bogus; &#x110000;", HtmlEntityDecoder.decode("&#bogus; &#x110000;"));
        assertEquals(null, HtmlEntityDecoder.decode(null));
    }
}
