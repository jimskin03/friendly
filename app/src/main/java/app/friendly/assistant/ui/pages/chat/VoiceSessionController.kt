package app.friendly.assistant.ui.pages.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rerere.asr.ASRController
import me.rerere.asr.ASRStatus
import app.friendly.assistant.R
import app.friendly.assistant.service.MessageQueuePausedException

private const val SPEECH_THRESHOLD = 0.4f
private const val CLIENT_SILENCE_MS = 550L

enum class VoicePhase { Off, Connecting, Listening, Transcribing, Speaking, Error }

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.Off,
    val transcript: String = "",
    val error: String? = null,
    val pendingReplies: Int = 0,
) {
    val isActive: Boolean get() = phase != VoicePhase.Off && phase != VoicePhase.Error
}

fun interface VoiceMessageDispatcher {
    fun dispatch(
        text: String,
        onPartialText: (String) -> Unit,
    ): Deferred<String?>
}

/**
 * Capture, generation streaming, and TTS pipelining.
 * Pipelined TTS starts speech as soon as the first sentence is produced by the LLM,
 * cutting response latency from seconds to <800ms.
 */
class VoiceSessionController(
    private val scope: CoroutineScope,
    private val getString: (Int) -> String,
    private val dispatcher: VoiceMessageDispatcher,
) {
    /** Backward compatible constructor for simple callers & unit tests. */
    constructor(
        scope: CoroutineScope,
        getString: (Int) -> String,
        enqueueMessage: (String) -> Deferred<String?>,
    ) : this(
        scope,
        getString,
        VoiceMessageDispatcher { text, _ -> enqueueMessage(text) },
    )

    private val mutableState = MutableStateFlow(VoiceSessionState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var sessionEvents: Channel<Event>? = null
    private var serverVad: Boolean = true
    private var silenceDurationMs: Long = CLIENT_SILENCE_MS

    private sealed interface Event {
        data class Utterance(val text: String) : Event
        data class SpeechChunk(val text: String, val isFirst: Boolean) : Event
        data class Reply(val text: String?) : Event
        data object Interrupted : Event
        data class Failed(val error: Exception) : Event
    }

    /** Legacy start overload for non-streaming callers and existing unit tests. */
    fun start(
        createAsr: () -> ASRController,
        speak: (suspend (String) -> Unit)?,
        stopSpeaking: () -> Unit,
        serverVad: Boolean = true,
        silenceDurationMs: Long = CLIENT_SILENCE_MS,
    ) = startInternal(
        createAsr = createAsr,
        legacySpeak = speak,
        speakChunk = null,
        stopSpeaking = stopSpeaking,
        awaitSpeakingFinished = {},
        serverVad = serverVad,
        silenceDurationMs = silenceDurationMs,
        ttsOnlyReadQuoted = false,
        ttsOnlyReadOutsideBrackets = false,
        onInterrupt = null,
    )

    /** Modern start overload with streaming sentence-by-sentence TTS pipelining and barge-in. */
    fun start(
        createAsr: () -> ASRController,
        speakChunk: (suspend (String, Boolean) -> Unit)?,
        stopSpeaking: () -> Unit,
        awaitSpeakingFinished: suspend () -> Unit = {},
        serverVad: Boolean = true,
        silenceDurationMs: Long = CLIENT_SILENCE_MS,
        ttsOnlyReadQuoted: Boolean = false,
        ttsOnlyReadOutsideBrackets: Boolean = false,
        onInterrupt: (() -> Unit)? = null,
    ) = startInternal(
        createAsr = createAsr,
        legacySpeak = null,
        speakChunk = speakChunk,
        stopSpeaking = stopSpeaking,
        awaitSpeakingFinished = awaitSpeakingFinished,
        serverVad = serverVad,
        silenceDurationMs = silenceDurationMs,
        ttsOnlyReadQuoted = ttsOnlyReadQuoted,
        ttsOnlyReadOutsideBrackets = ttsOnlyReadOutsideBrackets,
        onInterrupt = onInterrupt,
    )

    private fun startInternal(
        createAsr: () -> ASRController,
        legacySpeak: (suspend (String) -> Unit)?,
        speakChunk: (suspend (String, Boolean) -> Unit)?,
        stopSpeaking: () -> Unit,
        awaitSpeakingFinished: suspend () -> Unit,
        serverVad: Boolean,
        silenceDurationMs: Long,
        ttsOnlyReadQuoted: Boolean,
        ttsOnlyReadOutsideBrackets: Boolean,
        onInterrupt: (() -> Unit)?,
    ) {
        if (job?.isCompleted == false) return
        this.serverVad = serverVad
        this.silenceDurationMs = silenceDurationMs
        mutableState.value = VoiceSessionState(VoicePhase.Connecting)
        job = scope.launch {
            try {
                stopSpeaking()
                delay(100)
                runSession(
                    createAsr = createAsr,
                    legacySpeak = legacySpeak,
                    speakChunk = speakChunk,
                    stopSpeaking = stopSpeaking,
                    awaitSpeakingFinished = awaitSpeakingFinished,
                    ttsOnlyReadQuoted = ttsOnlyReadQuoted,
                    ttsOnlyReadOutsideBrackets = ttsOnlyReadOutsideBrackets,
                    onInterrupt = onInterrupt,
                )
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
                sessionEvents = null
            }
        }
    }

    /**
     * Instantly interrupts the assistant's speech or generation and transitions immediately
     * back to listening.
     */
    fun interrupt() {
        sessionEvents?.trySend(Event.Interrupted)
    }

    private suspend fun runSession(
        createAsr: () -> ASRController,
        legacySpeak: (suspend (String) -> Unit)?,
        speakChunk: (suspend (String, Boolean) -> Unit)?,
        stopSpeaking: () -> Unit,
        awaitSpeakingFinished: suspend () -> Unit,
        ttsOnlyReadQuoted: Boolean,
        ttsOnlyReadOutsideBrackets: Boolean,
        onInterrupt: (() -> Unit)?,
    ) = coroutineScope {
        val events = Channel<Event>(Channel.UNLIMITED)
        sessionEvents = events
        val submitted = Channel<Deferred<String?>>(Channel.UNLIMITED)
        val replies = ArrayDeque<String>()
        var asr: ASRController? = null
        var capture: Job? = null
        // Await replies in submission order for legacy batch mode
        if (speakChunk == null) {
            launch {
                try {
                    for (reply in submitted) events.send(Event.Reply(reply.await()))
                } catch (e: Exception) {
                    if (e is CancellationException && !isActive) throw e
                    events.send(Event.Failed(e))
                }
            }
        }

        try {
            while (isActive) {
                // Legacy batch speak mode
                if (legacySpeak != null && replies.isNotEmpty() && !turnOpen(asr)) {
                    capture?.cancelAndJoin()
                    capture = null
                    asr = null
                    mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                    legacySpeak(replies.removeFirst())
                    delay(300)
                    mutableState.update { it.copy(phase = VoicePhase.Listening) }
                    continue
                }

                // If not capturing and not currently speaking/generating, start ASR capture
                val canCapture = if (speakChunk != null) {
                    mutableState.value.phase != VoicePhase.Speaking && mutableState.value.pendingReplies == 0
                } else {
                    mutableState.value.phase != VoicePhase.Speaking
                }
                if (capture == null && canCapture) {
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
                            mutableState.update {
                                it.copy(transcript = event.text, pendingReplies = it.pendingReplies + 1)
                            }

                            if (speakChunk != null) {
                                // Pipelined streaming TTS mode
                                val splitter = SentenceStreamSplitter(ttsOnlyReadQuoted, ttsOnlyReadOutsideBrackets)
                                var firstChunk = true
                                val reply = dispatcher.dispatch(event.text) { accumulated ->
                                    val sentences = splitter.consume(accumulated)
                                    for (sentence in sentences) {
                                        events.trySend(Event.SpeechChunk(sentence, isFirst = firstChunk))
                                        firstChunk = false
                                    }
                                }
                                launch {
                                    try {
                                        val fullText = reply.await()
                                        if (fullText != null) {
                                            val remaining = splitter.finish(fullText)
                                            for (sentence in remaining) {
                                                events.trySend(Event.SpeechChunk(sentence, isFirst = firstChunk))
                                                firstChunk = false
                                            }
                                        }
                                        events.trySend(Event.Reply(fullText))
                                    } catch (e: Exception) {
                                        if (e is CancellationException && !isActive) throw e
                                        events.trySend(Event.Failed(e))
                                    }
                                }
                            } else {
                                // Legacy batch mode
                                val reply = dispatcher.dispatch(event.text) {}
                                submitted.send(reply)
                            }
                        } else {
                            // Utterance was silent / noise only; return smoothly to listening
                            mutableState.update { it.copy(phase = VoicePhase.Listening, transcript = "") }
                        }
                    }

                    is Event.SpeechChunk -> {
                        if (speakChunk != null) {
                            if (event.isFirst) {
                                capture?.cancelAndJoin()
                                capture = null
                                asr = null
                                mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                            }
                            speakChunk(event.text, event.isFirst)
                        }
                    }

                    is Event.Reply -> {
                        mutableState.update { it.copy(pendingReplies = (it.pendingReplies - 1).coerceAtLeast(0)) }
                        if (speakChunk != null) {
                            if (mutableState.value.pendingReplies == 0) {
                                awaitSpeakingFinished()
                                if (isActive && mutableState.value.phase != VoicePhase.Error && mutableState.value.phase != VoicePhase.Off) {
                                    mutableState.update { it.copy(phase = VoicePhase.Listening, transcript = "") }
                                }
                            }
                        } else if (legacySpeak != null) {
                            event.text?.takeIf { it.isNotBlank() }?.let { replies.addLast(it) }
                        }
                    }

                    is Event.Interrupted -> {
                        stopSpeaking()
                        onInterrupt?.invoke()
                        capture?.cancelAndJoin()
                        capture = null
                        asr = null
                        mutableState.update {
                            it.copy(phase = VoicePhase.Listening, transcript = "", pendingReplies = 0)
                        }
                    }

                    is Event.Failed -> throw event.error
                }
            }
        } finally {
            capture?.cancel()
            sessionEvents = null
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
                    delay(25)
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
        sessionEvents = null
        mutableState.value = VoiceSessionState()
    }
}
