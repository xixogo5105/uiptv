package com.uiptv.mobile.shared.settings

import com.uiptv.mobile.shared.db.UiptvSyncSchema
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPreferencesTest {
    @Test
    fun playerPreferenceIsAndroidOnlyData() {
        assertTrue(AndroidOnlyPreferenceKeys.PLAYER_TYPE in AndroidOnlyPreferenceKeys.all)
        assertTrue(AndroidOnlyPreferenceKeys.PLAYER_PACKAGE in AndroidOnlyPreferenceKeys.all)
        assertTrue(AndroidOnlyPreferenceKeys.PLAYER_REMEMBER in AndroidOnlyPreferenceKeys.all)
        assertFalse(UiptvSyncSchema.syncableTables.any { it.contains("Player", ignoreCase = true) })
    }

    @Test
    fun androidOnlyPreferenceKeysDoNotOverlapPortableConfigurationColumns() {
        assertTrue(AndroidOnlyPreferenceKeys.all.isNotEmpty())
        assertTrue(AndroidOnlyPreferenceKeys.all.none { key ->
            key in UiptvSyncSchema.androidPortableConfigurationColumns
        })
        assertTrue("defaultPlayerPath" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
        assertTrue("serverPort" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
    }
}
