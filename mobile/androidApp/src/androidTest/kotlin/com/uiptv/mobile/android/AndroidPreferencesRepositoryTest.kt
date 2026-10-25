package com.uiptv.mobile.android

import androidx.test.platform.app.InstrumentationRegistry
import com.uiptv.mobile.shared.settings.AndroidDataStorePreferencesRepository
import com.uiptv.mobile.shared.settings.EmbeddedPlayerRenderProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidPreferencesRepositoryTest {
    @Test
    fun embeddedRenderProfilePersistsAndCanBeRestored() = runBlocking {
        val repository = AndroidDataStorePreferencesRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val original = repository.load().embeddedPlayerPreference
        try {
            repository.saveEmbeddedPlayerPreference(
                original.copy(renderProfile = EmbeddedPlayerRenderProfile.GPU)
            )
            assertEquals(
                EmbeddedPlayerRenderProfile.GPU,
                repository.load().embeddedPlayerPreference.renderProfile
            )
        } finally {
            repository.saveEmbeddedPlayerPreference(original)
        }
    }
}
