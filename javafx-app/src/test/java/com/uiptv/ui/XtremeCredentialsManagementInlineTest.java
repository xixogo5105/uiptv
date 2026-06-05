package com.uiptv.ui;

import com.uiptv.util.XtremeCredentialsJson;
import com.uiptv.testsupport.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XtremeCredentialsManagementInlineTest {
    @BeforeAll
    static void initJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void addUpdateAndSetDefaultCredential() throws Exception {
        XtremeCredentialsManagementInline inline = runOnFxThread(() -> new XtremeCredentialsManagementInline(
                List.of(
                        new XtremeCredentialsJson.Entry("alpha", "passA", true),
                        new XtremeCredentialsJson.Entry("beta", "passB", false)
                ),
                "alpha",
                (entries, def) -> {
                }
        ));

        runOnFxThread(() -> {
            inline.setInputForTest("gamma", "passC");
            inline.addCredentialForTest();
            inline.selectIndexForTest(1);
            inline.setInputForTest("beta-updated", "passB2");
            inline.updateSelectedForTest();
            inline.setDefaultForTest();
            return null;
        });

        List<XtremeCredentialsJson.Entry> entries = runOnFxThread(inline::entriesForTest);
        assertEquals(3, entries.size());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        assertNotNull(defaultEntry);
        assertEquals("beta-updated", defaultEntry.username());
        assertEquals("passB2", defaultEntry.password());
    }

    @Test
    void bulkDeleteKeepsOneAndResetsDefault() throws Exception {
        XtremeCredentialsManagementInline inline = runOnFxThread(() -> new XtremeCredentialsManagementInline(
                List.of(
                        new XtremeCredentialsJson.Entry("alpha", "passA", true),
                        new XtremeCredentialsJson.Entry("beta", "passB", false),
                        new XtremeCredentialsJson.Entry("gamma", "passC", false)
                ),
                "alpha",
                (entries, def) -> {
                }
        ));

        runOnFxThread(() -> {
            inline.setItemSelectedForTest(0, true);
            inline.setItemSelectedForTest(1, true);
            inline.removeSelectedForTest();
            return null;
        });

        int count = runOnFxThread(inline::itemCountForTest);
        assertEquals(1, count);
        String defaultUsername = runOnFxThread(inline::defaultUsernameForTest);
        assertEquals("gamma", defaultUsername);
        boolean deleteDisabled = runOnFxThread(inline::isDeleteDisabledForTest);
        boolean defaultDisabled = runOnFxThread(inline::isDefaultDisabledForTest);
        assertEquals(true, deleteDisabled);
        assertEquals(true, defaultDisabled);
    }
}
