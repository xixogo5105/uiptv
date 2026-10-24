package com.uiptv.ui;

import com.uiptv.db.EpgSourceBlacklistDb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpgSourceBlacklistDialogTest {
    private static final EpgSourceBlacklistDb.Entry GITHUB = new EpgSourceBlacklistDb.Entry(
            "github", "https://github.com/example/guide.xml.gz", 0);

    @Test
    void searchMatchesUrlsCaseInsensitivelyAndShowsAllForBlankQuery() {
        assertTrue(EpgSourceBlacklistDialog.matchesSearch(GITHUB, "github"));
        assertTrue(EpgSourceBlacklistDialog.matchesSearch(GITHUB, "  GITHUB  "));
        assertTrue(EpgSourceBlacklistDialog.matchesSearch(GITHUB, ""));
        assertFalse(EpgSourceBlacklistDialog.matchesSearch(GITHUB, "example.org"));
    }
}
