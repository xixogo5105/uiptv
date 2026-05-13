package com.uiptv.mobile.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RemoteSyncContractsTest {
    @Test
    fun androidPullRequestUsesImportDirectionAndSkipsConfiguration() {
        val request = RemoteSyncRequest(verificationCode = "1234")

        assertEquals(RemoteSyncDirection.IMPORT_FROM_REMOTE, request.direction)
        assertEquals("UIPTV Android", request.requesterName)
        assertFalse(request.options.syncConfiguration)
        assertFalse(request.options.syncExternalPlayerPaths)
    }
}
