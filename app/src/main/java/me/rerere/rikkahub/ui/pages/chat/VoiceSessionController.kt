package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import me.rerere.asr.ASRController
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.MessageQueuePausedException

private const val SPEECH_THRESHOLD = 0.4f
private const val CLIENT_SILENCE_MS = 800L

enum class VoicePhase { Off, Connecting, Listening, Transcribing, Speaking, Error }

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.Off,
    val transcript: String = "",
    val error: String? = null,
    val pendingReplies: Int = 0,
) {
    val isActive: Boolean get() = phase != VoicePhase.Off && phase != VoicePhase.Error
}

/** Capture and generation run independently. Only TTS owns an exclusive microphone pause. */
class VoiceSessionController(
    private val scope: CoroutineScope,
    private val getString: (Int) -> String,
    private val enqueueMessage: (String) -> Deferred<String?>,
) {
    private val mutableState = MutableStateFlow(VoiceSessionState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var serverVad: Boolean = true
    private var silenceDurationMs: Long = CLIENT_SILENCE_MS

    private sealed interface Event {
        data class Utterance(val text: String) : Event
        data class Reply(val text: String?) : Event
        data class Failed(val error: Exception) : Event
    }

    fun start(
        createAsr: () -> ASRController,
        speak: (suspend (String) -> Unit)?,
        stopSpeaking: () -> Unit,
        serverVad: Boolean = true,
        silenceDurationMs: Long = CLIENT_SILENCE_MS,
    ) {
        if (job?.isCompleted == false) return
        this.serverVad = serverVad
        this.silenceDurationMs = silenceDurationMs
        mutableState.value = VoiceSessionState(VoicePhase.Connecting)
        job = scope.launch {
            try {
                stopSpeaking()
                delay(200)
                runSession(createAsr, speak)
            } catch (e: Exception) {
                if (e is CancellationException && !currentCoroutineContext().isActive) throw e
                mutableState.update {
                    it.copy(
                        phase = VoicePhase.Error,
                        error = when (e) {
                            is TimeoutCancellationException -> "Speech recognition timed out. Restart voice mode."
                            is MessageQueuePausedException -> getString(R.string.chat_page_voice_queue_paused)
                            else -> e.message ?: getString(R.string.chat_page_voice_failed)
                        },
                    )
                }
            } finally {
                stopSpeaking()
            }
        }
    }

    private suspend fun runSession(
        createAsr: () -> ASRController,
        speak: (suspend (String) -> Unit)?,
    ) = coroutineScope {
        val events = Channel<Event>(Channel.UNLIMITED)
        val submitted = Channel<Deferred<String?>>(Channel.UNLIMITED)
        val replies = ArrayDeque<String>()
        var asr: ASRController? = null
        var capture: Job? = null

        // Await replies in submission order, without blocking capture. Queue removal returns null.
        launch {
            try {
                for (reply in submitted) events.send(Event.Reply(reply.await()))
            } catch (e: Exception) {
                if (e is CancellationException && !isActive) throw e
                events.send(Event.Failed(e))
            }
        }

        try {
            while (isActive) {
                // Finish any sentence already in progress before giving TTS the microphone pause.
                if (speak != null && replies.isNotEmpty() && !turnOpen(asr)) {
                    capture?.cancelAndJoin()
                    capture = null
                    asr = null
                    mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                    speak(replies.removeFirst())
                    delay(300) // Let the loudspeaker's tail decay before opening the microphone.
                    continue
                }
                if (capture == null) {
                    mutableState.update { it.copy(phase = VoicePhase.Connecting, transcript = "") }
                    val recorder = createAsr()
                    asr = recorder
                    capture = launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            events.send(Event.Utterance(listen(recorder)))
                        } catch (e: Exception) {
                            if (e is CancellationException && !isActive) throw e
                            events.send(Event.Failed(e))
                        }
                    }
                }
                when (val event = events.receive()) {
                    is Event.Utterance -> {
                        capture?.join()
                        capture = null
                        asr = null
                        if (event.text.isNotBlank()) {
                            val reply = enqueueMessage(event.text)
                            mutableState.update { it.copy(transcript = event.text, pendingReplies = it.pendingReplies + 1) }
                            submitted.send(reply)
                        }
                    }
                    is Event.Reply -> {
                        mutableState.update { it.copy(pendingReplies = (it.pendingReplies - 1).coerceAtLeast(0)) }
                        event.text?.takeIf { speak != null && it.isNotBlank() }?.let { replies.addLast(it) }
                    }
                    is Event.Failed -> throw event.error
                }
            }
        } finally {
            capture?.cancel()
            // Submitted messages belong to the chat queue; leaving voice mode only detaches observers.
            submitted.close()
        }
    }

    private fun turnOpen(asr: ASRController?): Boolean {
        val snapshot = asr?.state?.value ?: return false
        if (snapshot.voiceTurn.itemId != null) return true
        if (serverVad) return false
        return (snapshot.amplitudes.lastOrNull() ?: 0f) >= SPEECH_THRESHOLD
    }

    private suspend fun listen(asr: ASRController): String =
        if (serverVad) listenServer(asr) else listenClient(asr)

    private suspend fun listenClient(asr: ASRController): String {
        try {
            asr.start {}
            withTimeout(15_000) {
                asr.state.first {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    it.status == ASRStatus.Listening || it.status == ASRStatus.Error
                }.also { check(it.status == ASRStatus.Listening) { "Unable to start speech recognition" } }
            }
            var heardSpeech = false
            var quietSince = 0L
            withTimeout(120_000) {
                while (true) {
                    val snapshot = asr.state.value
                    check(snapshot.errorMessage == null) { snapshot.errorMessage.orEmpty() }
                    check(snapshot.status == ASRStatus.Listening) { "Speech recognition disconnected" }
                    val level = snapshot.amplitudes.lastOrNull() ?: 0f
                    val now = System.currentTimeMillis()
                    if (level >= SPEECH_THRESHOLD) {
                        heardSpeech = true
                        quietSince = 0L
                    } else if (heardSpeech && quietSince == 0L) {
                        quietSince = now
                    }
                    mutableState.update { current ->
                        current.copy(phase = VoicePhase.Listening, transcript = snapshot.transcript)
                    }
                    if (heardSpeech && quietSince != 0L && now - quietSince >= silenceDurationMs) break
                    delay(50)
                }
            }
            asr.stop()
            mutableState.update { it.copy(phase = VoicePhase.Transcribing) }
            return withTimeout(30_000) {
                asr.state.first { it.status == ASRStatus.Idle || it.status == ASRStatus.Error }
                    .also { check(it.errorMessage == null) { it.errorMessage.orEmpty() } }
                    .transcript
                    .trim()
            }
        } finally {
            asr.dispose()
        }
    }

    private suspend fun listenServer(asr: ASRController): String {
        try {
            asr.start {}
            withTimeout(15_000) {
                asr.state.first {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    it.status != ASRStatus.Connecting
                }.also { check(it.status == ASRStatus.Listening || it.voiceTurn.isComplete) { "Unable to start speech recognition" } }
            }
            val ended = withTimeout(120_000) {
                asr.state.onEach {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    check(it.status == ASRStatus.Listening || it.voiceTurn.isComplete) { "Speech recognition disconnected" }
                    mutableState.update { current -> current.copy(phase = VoicePhase.Listening, transcript = it.transcript) }
                }.first { it.voiceTurn.speechEnded }
            }
            asr.pauseCapture()
            mutableState.update { it.copy(phase = VoicePhase.Transcribing, transcript = ended.transcript) }
            return withTimeout(15_000) {
                asr.state.first {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    check(it.voiceTurn.isComplete || it.status == ASRStatus.Listening) {
                        "Speech recognition disconnected before the final transcript was received"
                    }
                    it.voiceTurn.isComplete
                }.voiceTurn.finalText.orEmpty()
            }
        } finally {
            asr.dispose()
        }
    }

    fun stop() {
        job?.cancel()
        mutableState.value = VoiceSessionState()
    }
}
