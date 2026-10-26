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
            val persisted = original.copy(
                renderProfile = EmbeddedPlayerRenderProfile.GPU,
                brightness = 0.63f,
                volumePercent = 42,
                zoomMode = "crop"
            )
            repository.saveEmbeddedPlayerPreference(
                persisted
            )
            assertEquals(persisted, repository.load().embeddedPlayerPreference)
        } finally {
            repository.saveEmbeddedPlayerPreference(original)
        }
    }
}
