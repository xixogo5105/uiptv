package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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

    @Test
    void normalizeImageUrl_handlesBlankQuotedProtocolRelativeAndFallbackCases() {
        assertEquals("", ImageUrlNormalizer.normalizeImageUrl(null, null));
        assertEquals("", ImageUrlNormalizer.normalizeImageUrl(" ' ' ", null));
        assertEquals("blob:https://example.test/id", ImageUrlNormalizer.normalizeImageUrl("blob:https://example.test/id", null));
        assertEquals("file:/tmp/image.png", ImageUrlNormalizer.normalizeImageUrl("file:/tmp/image.png", null));
        assertEquals("https://cdn.example.test/image.png", ImageUrlNormalizer.normalizeImageUrl("//cdn.example.test/image.png", null));
        assertEquals("https://cdn.example.test/image.png", ImageUrlNormalizer.normalizeImageUrl("cdn.example.test/image.png", null));
        assertEquals("/root.png", ImageUrlNormalizer.normalizeImageUrl("/root.png", null));

        try (MockedStatic<ServerUrlUtil> serverUrlUtil = Mockito.mockStatic(ServerUrlUtil.class)) {
            serverUrlUtil.when(ServerUrlUtil::getLocalServerUrl).thenReturn("http://127.0.0.1:9999");

            assertEquals("http://127.0.0.1:9999/images/poster.jpg",
                    ImageUrlNormalizer.normalizeImageUrl("images/poster.jpg", null));
        }
    }

    @Test
    void normalizeImageUrl_usesPortalBeforeAccountUrlAndAddsMissingScheme() {
        Account portalAccount = new Account("acc", "user", "pass", "http://fallback.test/base", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        portalAccount.setServerPortalUrl("https://portal.test:8443/stalker_portal/server/load.php");
        assertEquals("https://portal.test:8443/logo.png", ImageUrlNormalizer.normalizeImageUrl("\"/logo.png\"", portalAccount));
        assertEquals("https://portal.test:8443/images/logo.png", ImageUrlNormalizer.normalizeImageUrl("'images/logo.png'", portalAccount));

        Account hostOnly = new Account("acc", "user", "pass", "hostonly.test/path", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        assertEquals("http://hostonly.test/logo.png", ImageUrlNormalizer.normalizeImageUrl("/logo.png", hostOnly));

        Account invalidPortal = new Account("acc", "user", "pass", "http://fallback.test/base", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        invalidPortal.setServerPortalUrl("http://[bad");
        assertEquals("http://fallback.test/logo.png", ImageUrlNormalizer.normalizeImageUrl("/logo.png", invalidPortal));
    }
}
