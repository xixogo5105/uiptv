package com.uiptv.util;

import com.rometools.rome.io.FeedException;
import com.uiptv.model.CategoryType;
import com.uiptv.shared.PlaylistEntry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssParserTest {

    @Test
    void getCategoriesReturnsAllCategory() {
        Set<PlaylistEntry> categories = RssParser.getCategories();

        assertEquals(1, categories.size());
        assertEquals(CategoryType.ALL.displayName(), categories.iterator().next().getGroupTitle());
    }

    @Test
    void parseMapsFeedItemsToPlaylistEntries() {
        try (MockedStatic<RssFeedReader> feedReader = Mockito.mockStatic(RssFeedReader.class)) {
            feedReader.when(() -> RssFeedReader.getItems("rss://feed")).thenReturn(List.of(
                    new RssFeedReader.RssItem("Title One", "https://stream.test/one.mp4", "desc")
            ));

            List<PlaylistEntry> entries = RssParser.parse("rss://feed");

            assertEquals(1, entries.size());
            assertEquals("Title One", entries.getFirst().getTitle());
            assertEquals("Title One", entries.getFirst().getGroupTitle());
            assertEquals("https://stream.test/one.mp4", entries.getFirst().getPlaylistEntry());
            assertTrue(entries.getFirst().getId().matches("\\d{40,}"));
        }
    }

    @Test
    void parseWrapsIoAndCheckedParserFailuresButRethrowsRuntime() {
        try (MockedStatic<RssFeedReader> feedReader = Mockito.mockStatic(RssFeedReader.class)) {
            feedReader.when(() -> RssFeedReader.getItems("rss://io"))
                    .thenThrow(new IOException("network down"));
            UncheckedIOException io = assertThrows(UncheckedIOException.class, () -> RssParser.parse("rss://io"));
            assertTrue(io.getMessage().contains("network down"));

            feedReader.when(() -> RssFeedReader.getItems("rss://blank-io"))
                    .thenThrow(new IOException(" "));
            assertTrue(assertThrows(UncheckedIOException.class, () -> RssParser.parse("rss://blank-io"))
                    .getMessage().endsWith("Unable to load RSS feed"));

            feedReader.when(() -> RssFeedReader.getItems("rss://feed-error"))
                    .thenThrow(new FeedException("bad feed"));
            assertThrows(IllegalStateException.class, () -> RssParser.parse("rss://feed-error"));

            feedReader.when(() -> RssFeedReader.getItems("rss://runtime"))
                    .thenThrow(new IllegalArgumentException("bad"));
            assertThrows(IllegalArgumentException.class, () -> RssParser.parse("rss://runtime"));
        }
    }
}
