package app.friendly.assistant.ui.pages.setting.components

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "SpeechApiHelper"

object SpeechApiHelper {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // -------------------------------------------------------------
    // Presets
    // -------------------------------------------------------------

    val GEMINI_TTS_VOICES = listOf(
        "Puck", "Charon", "Kore", "Fenrir", "Aoede", "Zephyr", "Leda", "Orus",
        "Lyra", "Callirrhoe", "Eurydice", "Despina", "Thalassa", "Autonoe",
        "Thyone", "Dia", "Umbriel", "Arche", "Ganymede", "Pandora", "Erinome",
        "Enceladus", "Iapetus", "Kalyke", "Thebe", "Algol", "Castor", "Pollux",
        "Rhea", "Dione"
    )

    val GEMINI_TTS_MODELS_PRESET = listOf(
        "gemini-2.5-flash-preview-tts",
        "gemini-2.0-flash",
        "gemini-2.5-pro-preview-tts",
        "gemini-1.5-flash"
    )

    val GEMINI_ASR_MODELS_PRESET = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-2.5-flash-preview",
        "gemini-1.5-pro"
    )

    val OPENAI_TTS_MODELS_PRESET = listOf(
        "tts-1",
        "tts-1-hd",
        "gpt-4o-mini-tts"
    )

    val OPENAI_TTS_VOICES_PRESET = listOf(
        "alloy", "echo", "fable", "onyx", "nova", "shimmer", "ash", "coral", "sage", "verse", "ballad"
    )

    val WHISPER_MODELS_PRESET = listOf(
        "whisper-large-v3-turbo",
        "whisper-large-v3",
        "distil-whisper-large-v3-en",
        "whisper-1"
    )

    val OPENAI_REALTIME_MODELS_PRESET = listOf(
        "gpt-4o-transcribe",
        "gpt-4o-mini-transcribe",
        "gpt-4o-realtime-preview"
    )

    val ELEVENLABS_DEFAULT_VOICES = listOf(
        "21m00Tcm4TlvDq8ikWAM" to "Rachel (Calm)",
        "AZnzlk1XvdvUeBnXmlld" to "Domi (Strong)",
        "EXAVITQu4vr4xnSDxMaL" to "Bella (Soft)",
        "ErXwobaYiN019PkySvjV" to "Antoni (Well-rounded)",
        "MF3mGyEYCl7XYWbV9V6O" to "Elli (Emotional)",
        "TxGEqnHWrfWFTfGW9XjX" to "Josh (Deep)",
        "VR6AewLTigWG4xSOukaG" to "Arnold (Crisp)",
        "pNInz6obpgDQGcFmaJgB" to "Adam (Conversational)",
        "yoZ06aMyZSVviqRWmAcG" to "Sam (Dynamic)"
    )

    // -------------------------------------------------------------
    // API Fetchers
    // -------------------------------------------------------------

    suspend fun fetchGeminiModels(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanBase = baseUrl.trim().trimEnd('/')
                val cleanKey = apiKey.trim()
                val separator = if (cleanBase.contains("?")) "&" else "?"
                val url = "$cleanBase/models?pageSize=100${if (cleanKey.isNotBlank()) "${separator}key=$cleanKey" else ""}"

                val reqBuilder = Request.Builder().url(url).get()
                if (cleanKey.isNotBlank()) {
                    reqBuilder.addHeader("x-goog-api-key", cleanKey)
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        throw IOException(errMsg?.takeIf { it.isNotBlank() } ?: "Gemini API error (HTTP ${resp.code})")
                    }

                    val json = JSONObject(body)
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    val models = mutableListOf<String>()

                    for (i in 0 until modelsArray.length()) {
                        val obj = modelsArray.optJSONObject(i) ?: continue
                        val name = obj.optString("name").removePrefix("models/")
                        val methods = obj.optJSONArray("supportedGenerationMethods")
                        val supportsGenerate = if (methods != null) {
                            var found = false
                            for (j in 0 until methods.length()) {
                                if (methods.optString(j) == "generateContent") {
                                    found = true
                                    break
                                }
                            }
                            found
                        } else true

                        if (name.isNotBlank() && supportsGenerate) {
                            models.add(name)
                        }
                    }

                    // Sort: prioritize models with 'tts', then 'flash', then general
                    models.sortedWith(
                        compareBy<String> { !it.contains("tts", ignoreCase = true) }
                            .thenBy { !it.contains("flash", ignoreCase = true) }
                            .thenBy { it }
                    )
                }
            }
        }

    suspend fun fetchOpenAIModels(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanBase = baseUrl.trim().trimEnd('/')
                val cleanKey = apiKey.trim()
                val url = "$cleanBase/models"

                val reqBuilder = Request.Builder().url(url).get()
                if (cleanKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $cleanKey")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        throw IOException(errMsg?.takeIf { it.isNotBlank() } ?: "API error (HTTP ${resp.code})")
                    }

                    val json = JSONObject(body)
                    val dataArray = json.optJSONArray("data") ?: JSONArray()
                    val models = mutableListOf<String>()

                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isNotBlank()) {
                            models.add(id)
                        }
                    }

                    models.sortedWith(
                        compareBy<String> { !it.contains("tts", ignoreCase = true) }
                            .thenBy { it }
                    )
                }
            }
        }

    suspend fun fetchWhisperModels(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanBase = baseUrl.trim().trimEnd('/')
                val cleanKey = apiKey.trim()
                val url = "$cleanBase/models"

                val reqBuilder = Request.Builder().url(url).get()
                if (cleanKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $cleanKey")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        throw IOException(errMsg?.takeIf { it.isNotBlank() } ?: "API error (HTTP ${resp.code})")
                    }

                    val json = JSONObject(body)
                    val dataArray = json.optJSONArray("data") ?: JSONArray()
                    val models = mutableListOf<String>()

                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isNotBlank()) {
                            models.add(id)
                        }
                    }

                    // Prioritize whisper models
                    val whisperModels = models.filter { it.contains("whisper", ignoreCase = true) }
                    if (whisperModels.isNotEmpty()) {
                        whisperModels.sorted()
                    } else {
                        models.sorted()
                    }
                }
            }
        }

    suspend fun fetchElevenLabsVoices(baseUrl: String, apiKey: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanBase = baseUrl.trim().trimEnd('/')
                val cleanKey = apiKey.trim()
                val url = "$cleanBase/v1/voices"

                val reqBuilder = Request.Builder().url(url).get()
                if (cleanKey.isNotBlank()) {
                    reqBuilder.addHeader("xi-api-key", cleanKey)
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = runCatching {
                            JSONObject(body).optJSONObject("detail")?.optString("message")
                                ?: JSONObject(body).optString("detail")
                        }.getOrNull()
                        throw IOException(errMsg?.takeIf { it.isNotBlank() } ?: "ElevenLabs error (HTTP ${resp.code})")
                    }

                    val json = JSONObject(body)
                    val voicesArray = json.optJSONArray("voices") ?: JSONArray()
                    val voices = mutableListOf<Pair<String, String>>()

                    for (i in 0 until voicesArray.length()) {
                        val obj = voicesArray.optJSONObject(i) ?: continue
                        val id = obj.optString("voice_id")
                        val name = obj.optString("name")
                        val category = obj.optString("category")
                        val label = if (category.isNotBlank()) "$name ($category)" else name
                        if (id.isNotBlank()) {
                            voices.add(id to label)
                        }
                    }

                    voices.sortedBy { it.second }
                }
            }
        }

    suspend fun fetchElevenLabsModels(baseUrl: String, apiKey: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanBase = baseUrl.trim().trimEnd('/')
                val cleanKey = apiKey.trim()
                val url = "$cleanBase/v1/models"

                val reqBuilder = Request.Builder().url(url).get()
                if (cleanKey.isNotBlank()) {
                    reqBuilder.addHeader("xi-api-key", cleanKey)
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IOException("ElevenLabs models error (HTTP ${resp.code})")
                    }

                    val models = mutableListOf<Pair<String, String>>()
                    // Handle array response or object with models property
                    val modelsArray = runCatching { JSONArray(body) }.getOrNull()
                        ?: JSONObject(body).optJSONArray("models")
                        ?: JSONArray()

                    for (i in 0 until modelsArray.length()) {
                        val obj = modelsArray.optJSONObject(i) ?: continue
                        val id = obj.optString("model_id")
                        val name = obj.optString("name")
                        val canTts = obj.optBoolean("can_do_text_to_speech", true)
                        if (id.isNotBlank() && canTts) {
                            models.add(id to name)
                        }
                    }

                    models.sortedBy { it.second }
                }
            }
        }
}
