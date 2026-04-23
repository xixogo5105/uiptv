package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageUrlNormalizerTest {

    @Test
    void normalizeImageUrl_resolvesRelativeAgainstAccount() {
        Account account = new Account("acc", "user", "pass", "http://example.com:8080/portal.php", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://example.com:8080/portal.php", false);

        String resolved = ImageUrlNormalizer.normalizeImageUrl("images/poster.jpg", account);
        assertEquals("http://example.com:8080/images/poster.jpg", resolved);
    }

    @Test
    void normalizeImageUrl_keepsAbsoluteAndInline() {
        Account account = new Account("acc", "user", "pass", "http://example.com:8080/portal.php", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://example.com:8080/portal.php", false);

        assertEquals("https://cdn.example.com/poster.jpg", ImageUrlNormalizer.normalizeImageUrl("https://cdn.example.com/poster.jpg", account));
        assertEquals("data:image/png;base64,aaaa", ImageUrlNormalizer.normalizeImageUrl("data:image/png;base64,aaaa", account));
    }
}
