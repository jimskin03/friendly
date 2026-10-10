package me.rerere.asr.providers

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude

private const val TAG = "SystemASR"
private const val RESTART_DELAY_MS = 250L
private const val STOP_RESULT_TIMEOUT_MS = 1200L

class SystemASRController(
    context: Context,
    private val provider: ASRProviderSetting.System
) : ASRController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var onTranscriptChangeCallback: ((String) -> Unit)? = null
    private var sessionActive = false
    private var generation = 0
    private var hardRetries = 0
    private val committed = StringBuilder()

    private val recognitionAvailable = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    private val _state = MutableStateFlow(
        ASRState(
            status = ASRStatus.Idle,
            isAvailable = recognitionAvailable
        )
    )
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    init {
        val component = findRecognitionComponent()
        Log.i(
            TAG,
            "SpeechRecognizer available=$recognitionAvailable component=${component?.flattenToShortString() ?: "default"}"
        )
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        onTranscriptChangeCallback = onTranscriptChange
        sessionActive = true
        hardRetries = 0
        committed.clear()
        mainHandler.removeCallbacksAndMessages(null)

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sessionActive = false
            setError("Microphone permission is required")
            return
        }
        if (!recognitionAvailable) {
            sessionActive = false
            _state.update {
                it.copy(
                    status = ASRStatus.Error,
                    isAvailable = false,
                    errorMessage = "System Speech Recognition is not available on this device"
                )
            }
            return
        }

        _state.update {
            it.copy(
                status = ASRStatus.Connecting,
                isAvailable = true,
                errorMessage = null,
                transcript = ""
            )
        }
        mainHandler.post { beginRecognition() }
    }

    override fun stop() {
        sessionActive = false
        _state.update { it.copy(status = ASRStatus.Stopping) }
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop speech recognition", e)
            }
            mainHandler.postDelayed({
                _state.update { current ->
                    if (current.status == ASRStatus.Stopping) {
                        current.copy(status = ASRStatus.Idle)
                    } else {
                        current
                    }
                }
            }, STOP_RESULT_TIMEOUT_MS)
        }
    }

    override fun dispose() {
        sessionActive = false
        onTranscriptChangeCallback = null
        generation += 1
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { destroyRecognizer() }
    }

    private fun beginRecognition() {
        if (!sessionActive) return
        val gen = ++generation
        destroyRecognizer()
        try {
            val component = findRecognitionComponent()
            val recognizer = if (component != null) {
                SpeechRecognizer.createSpeechRecognizer(appContext, component)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(listener(gen))
            Log.i(TAG, "SpeechRecognizer starting component=${component?.flattenToShortString() ?: "default"}")
            recognizer.startListening(recognitionIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            sessionActive = false
            setError(e.message ?: "Failed to start speech recognition")
        }
    }

    private fun listener(gen: Int) = object : RecognitionListener {
        private var finished = false

        override fun onReadyForSpeech(params: Bundle?) {
            if (!current(gen)) return
            hardRetries = 0
            Log.i(TAG, "SpeechRecognizer listening")
            _state.update {
                it.copy(
                    status = ASRStatus.Listening,
                    isAvailable = true,
                    errorMessage = null
                )
            }
        }

        override fun onBeginningOfSpeech() {
            if (!current(gen)) return
            _state.update { it.copy(status = ASRStatus.Listening) }
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (!current(gen)) return
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(normalized)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // The platform recognizer ends each utterance on its own. Voice mode
            // treats any status other than Listening as a disconnect, so stay in
            // Listening and open the next utterance when the result arrives.
        }

        override fun onError(error: Int) {
            if (finished || gen != generation) return
            finished = true
            destroyRecognizer()
            val message = errorMessage(error)
            Log.w(TAG, "SpeechRecognizer error: $error ($message)")
            if (!sessionActive) {
                finishIfStopping()
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(gen)

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    sessionActive = false
                    setError(message)
                }

                else -> if (hardRetries < 2) {
                    hardRetries += 1
                    scheduleRestart(gen)
                } else {
                    sessionActive = false
                    setError(message)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (finished || gen != generation) return
            finished = true
            destroyRecognizer()
            commit(bestHypothesis(results))
            if (sessionActive) {
                publish(status = ASRStatus.Listening)
                scheduleRestart(gen)
            } else {
                publish(status = ASRStatus.Idle)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current(gen)) return
            val text = bestHypothesis(partialResults)
            if (text.isNotBlank()) publish(partial = text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun scheduleRestart(gen: Int) {
        mainHandler.postDelayed({
            if (sessionActive && gen == generation) beginRecognition()
        }, RESTART_DELAY_MS)
    }

    private fun current(gen: Int): Boolean = sessionActive && gen == generation

    private fun commit(text: String) {
        val utterance = text.trim()
        if (utterance.isEmpty()) return
        if (committed.isNotEmpty()) committed.append(' ')
        committed.append(utterance)
    }

    private fun transcript(partial: String = ""): String {
        val extra = partial.trim()
        return buildString {
            append(committed)
            if (extra.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append(extra)
            }
        }
    }

    private fun publish(partial: String = "", status: ASRStatus? = null) {
        val text = transcript(partial)
        _state.update {
            it.copy(
                transcript = text,
                status = status ?: it.status,
                errorMessage = null
            )
        }
        if (text.isNotBlank()) onTranscriptChangeCallback?.invoke(text)
    }

    private fun finishIfStopping() {
        _state.update { current ->
            if (current.status == ASRStatus.Stopping) current.copy(status = ASRStatus.Idle) else current
        }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun recognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            if (provider.language.isNotBlank() && provider.language != "auto") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, provider.language)
            }
        }
    }

    private fun findRecognitionComponent(): ComponentName? {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = runCatching {
            appContext.packageManager.queryIntentServices(intent, 0)
        }.getOrDefault(emptyList())
        val infos = services.mapNotNull { it.serviceInfo }
        val preferred = infos.firstOrNull { info ->
            info.packageName == "com.google.android.googlequicksearchbox" ||
                info.packageName == "com.google.android.as" ||
                info.packageName == "com.google.android.apps.speechservices"
        } ?: infos.firstOrNull()
        return preferred?.let { ComponentName(it.packageName, it.name) }
    }

    private fun bestHypothesis(bundle: Bundle?): String {
        val stable = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!stable.isNullOrBlank()) return stable.trim()
        val unstable = bundle?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")?.firstOrNull()
        return unstable?.trim().orEmpty()
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
        else -> "Speech recognition error ($error)"
    }

    private fun destroyRecognizer() {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        try {
            recognizer.cancel()
            recognizer.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
    }
}
