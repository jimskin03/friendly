package me.rerere.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingGeminiTest {
    @Test
    fun gemini_defaults_are_expected() {
        val setting = ASRProviderSetting.Gemini()

        assertEquals("Gemini ASR", setting.name)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", setting.baseUrl)
        assertEquals("gemini-2.0-flash", setting.model)
        assertEquals(16000, setting.sampleRate)
        assertEquals(30, setting.segmentDurationSec)
        assertEquals("", setting.apiKey)
        assertFalse(setting.supportsServerVadVoiceMode)
    }

    @Test
    fun gemini_is_registered_in_provider_types() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.Gemini::class))
    }

    @Test
    fun gemini_copy_provider_preserves_extra_fields() {
        val original = ASRProviderSetting.Gemini(
            apiKey = "ai-key-123",
            baseUrl = "https://custom.endpoint.com/v1beta",
            model = "gemini-1.5-flash",
            prompt = "Custom prompt",
            sampleRate = 24000,
            segmentDurationSec = 15,
        )
        val copied = original.copyProvider(id = original.id, name = "Renamed Gemini")

        assertTrue(copied is ASRProviderSetting.Gemini)
        val gemini = copied as ASRProviderSetting.Gemini
        assertEquals("Renamed Gemini", gemini.name)
        assertEquals("ai-key-123", gemini.apiKey)
        assertEquals("https://custom.endpoint.com/v1beta", gemini.baseUrl)
        assertEquals("gemini-1.5-flash", gemini.model)
        assertEquals("Custom prompt", gemini.prompt)
        assertEquals(24000, gemini.sampleRate)
        assertEquals(15, gemini.segmentDurationSec)
    }
}
