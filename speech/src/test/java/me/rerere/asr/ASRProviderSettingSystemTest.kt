package me.rerere.asr

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingSystemTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun system_is_the_default_free_provider() {
        val setting = ASRProviderSetting.System()

        assertEquals(DEFAULT_SYSTEM_ASR_ID, setting.id)
        assertEquals("System ASR", setting.name)
        assertEquals("auto", setting.language)
        assertTrue(setting.hasCredentials)
        assertFalse(setting.supportsServerVadVoiceMode)
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.System::class))
    }

    @Test
    fun system_round_trips_as_type_system() {
        val encoded = json.encodeToString<ASRProviderSetting>(ASRProviderSetting.System())

        assertTrue(encoded.contains("\"type\":\"system\""))
        val decoded = json.decodeFromString<ASRProviderSetting>(encoded)
        assertTrue(decoded is ASRProviderSetting.System)
        assertEquals(DEFAULT_SYSTEM_ASR_ID, decoded.id)
    }

    @Test
    fun cloud_providers_without_a_key_are_not_configured() {
        assertFalse(ASRProviderSetting.Gemini().hasCredentials)
        assertFalse(ASRProviderSetting.Whisper().hasCredentials)
        assertTrue(ASRProviderSetting.Gemini(apiKey = "key").hasCredentials)
        assertTrue(ASRProviderSetting.Whisper(apiKey = "key").hasCredentials)
    }
}
