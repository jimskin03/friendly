package me.rerere.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingWhisperTest {
    @Test
    fun whisper_defaults_are_expected() {
        val setting = ASRProviderSetting.Whisper()

        assertEquals("Groq Whisper ASR", setting.name)
        assertEquals("https://api.groq.com/openai/v1", setting.baseUrl)
        assertEquals("whisper-large-v3-turbo", setting.model)
        assertEquals("", setting.language)
        assertEquals("", setting.prompt)
        assertEquals(16000, setting.sampleRate)
        assertEquals(30, setting.segmentDurationSec)
        assertEquals("", setting.apiKey)
        assertFalse(setting.supportsServerVadVoiceMode)
    }

    @Test
    fun whisper_is_registered_in_provider_types() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.Whisper::class))
    }

    @Test
    fun whisper_copy_provider_preserves_extra_fields() {
        val original = ASRProviderSetting.Whisper(
            apiKey = "gsk-12345",
            baseUrl = "https://api.openai.com/v1",
            model = "whisper-1",
            language = "en",
            prompt = "Custom glossary",
            sampleRate = 16000,
            segmentDurationSec = 60,
        )
        val copied = original.copyProvider(id = original.id, name = "OpenAI Whisper")

        assertTrue(copied is ASRProviderSetting.Whisper)
        val whisper = copied as ASRProviderSetting.Whisper
        assertEquals("OpenAI Whisper", whisper.name)
        assertEquals("gsk-12345", whisper.apiKey)
        assertEquals("https://api.openai.com/v1", whisper.baseUrl)
        assertEquals("whisper-1", whisper.model)
        assertEquals("en", whisper.language)
        assertEquals("Custom glossary", whisper.prompt)
        assertEquals(16000, whisper.sampleRate)
        assertEquals(60, whisper.segmentDurationSec)
    }
}
