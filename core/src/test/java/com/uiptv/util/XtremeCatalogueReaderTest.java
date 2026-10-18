package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class XtremeCatalogueReaderTest {
    private Account account() {
        Account account = new Account(); account.setAction(Account.AccountAction.itv);
        account.setUrl("http://example.invalid/"); account.setUsername("fixture"); account.setPassword("fixture");
        return account;
    }
    @Test void preservesProviderIdentityAndCategory() {
        var rows = XtremeApiParser.readChannelCatalogue(new StringReader(
                "[{\"stream_id\":42,\"category_id\":7,\"name\":\"News\"}]"), account(), null, null);
        assertEquals("42", rows.getFirst().getChannelId()); assertEquals("7", rows.getFirst().getCategoryId());
    }
    @Test void streamsCatalogueLargerThanFormer16MillionCharacterCap() {
        String row = "{\"stream_id\":42,\"category_id\":7,\"name\":\"News\",\"metadata\":\"" + "x".repeat(9000) + "\"}";
        java.util.Iterator<java.io.Reader> segments = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(new StringReader("[")),
                java.util.stream.Stream.concat(java.util.stream.IntStream.range(0, 2000)
                        .mapToObj(i -> new StringReader((i == 0 ? "" : ",") + row)), java.util.stream.Stream.of(new StringReader("]"))))
                .map(reader -> (java.io.Reader) reader).iterator();
        java.io.Reader source = new java.io.Reader() {
            java.io.Reader current = segments.next();
            @Override public int read(char[] buffer, int offset, int length) throws java.io.IOException {
                int read;
                while ((read = current.read(buffer, offset, length)) == -1 && segments.hasNext()) current = segments.next();
                return read;
            }
            @Override public void close() { /* Test stream has no external resources. */ }
        };
        assertEquals(2000, XtremeApiParser.readChannelCatalogue(source, account(), null, null).size());
    }

    @Test void rejectsTruncatedCatalogueInsteadOfReturningPartialChannels() {
        assertThrows(org.json.JSONException.class, () -> XtremeApiParser.readChannelCatalogue(
                new StringReader("[{\"stream_id\":42,\"name\":\"News\"},"), account(), null, null));
    }
    @Test void rejectsProviderErrorObjectAndTrailingGarbage() {
        for (String json : new String[]{"{\"error\":\"denied\"}", "[]garbage"})
            assertThrows(org.json.JSONException.class, () -> XtremeApiParser.readChannelCatalogue(new StringReader(json), account(), null, null));
    }
}
