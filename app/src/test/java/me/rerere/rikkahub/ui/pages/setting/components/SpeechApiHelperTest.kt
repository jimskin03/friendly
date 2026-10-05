package me.rerere.rikkahub.ui.pages.setting.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechApiHelperTest {

    @Test
    fun `gemini voices list contains all 30 official voices`() {
        val voices = SpeechApiHelper.GEMINI_TTS_VOICES
        assertEquals(30, voices.size)
        assertTrue(voices.contains("Puck"))
        assertTrue(voices.contains("Charon"))
        assertTrue(voices.contains("Kore"))
        assertTrue(voices.contains("Fenrir"))
        assertTrue(voices.contains("Aoede"))
        assertTrue(voices.contains("Zephyr"))
        assertTrue(voices.contains("Dione"))
    }

    @Test
    fun `gemini presets contain expected tts and asr models`() {
        val ttsModels = SpeechApiHelper.GEMINI_TTS_MODELS_PRESET
        assertTrue(ttsModels.contains("gemini-2.5-flash-preview-tts"))
        assertTrue(ttsModels.contains("gemini-2.0-flash"))

        val asrModels = SpeechApiHelper.GEMINI_ASR_MODELS_PRESET
        assertTrue(asrModels.contains("gemini-2.0-flash"))
        assertTrue(asrModels.contains("gemini-1.5-flash"))
    }

    @Test
    fun `openai presets contain expected models and voices`() {
        val models = SpeechApiHelper.OPENAI_TTS_MODELS_PRESET
        assertTrue(models.contains("tts-1"))
        assertTrue(models.contains("tts-1-hd"))

        val voices = SpeechApiHelper.OPENAI_TTS_VOICES_PRESET
        assertTrue(voices.contains("alloy"))
        assertTrue(voices.contains("echo"))
        assertTrue(voices.contains("shimmer"))
    }

    @Test
    fun `whisper presets contain turbo and large v3`() {
        val models = SpeechApiHelper.WHISPER_MODELS_PRESET
        assertTrue(models.contains("whisper-large-v3-turbo"))
        assertTrue(models.contains("whisper-large-v3"))
    }

    @Test
    fun `elevenlabs default voices contain popular voices`() {
        val voices = SpeechApiHelper.ELEVENLABS_DEFAULT_VOICES
        assertTrue(voices.isNotEmpty())
        assertTrue(voices.any { it.second.startsWith("Rachel") })
        assertTrue(voices.any { it.second.startsWith("Adam") })
    }
}
