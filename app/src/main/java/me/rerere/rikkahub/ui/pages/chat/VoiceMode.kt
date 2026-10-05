package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.R
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.providers.DashScopeASRController
import me.rerere.asr.providers.GeminiASRController
import me.rerere.asr.providers.MiMoASRController
import me.rerere.asr.providers.OpenAIRealtimeASRController
import me.rerere.asr.providers.StepASRController
import me.rerere.asr.providers.SystemASRController
import me.rerere.asr.providers.VolcengineASRController
import me.rerere.asr.providers.WhisperASRController
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.service.VoiceCaptureForegroundService
import me.rerere.rikkahub.service.phone.PhoneAutomationMiniIndicatorManager
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.stripMarkdown
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/** Lives above adaptive drawer branches, so resizing does not recreate the voice session. */
@Composable
fun rememberVoiceModeStarter(vm: ChatVM, settings: Settings): () -> Unit {
    val context = LocalContext.current.applicationContext
    val client = koinInject<OkHttpClient>()
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val toaster = LocalToaster.current
    val permission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permission)
    val voice = vm.voiceSession
    val state by voice.state.collectAsStateWithLifecycle()
    val provider = settings.getSelectedASRProvider()
    val lifecycleOwner = LocalLifecycleOwner.current

    val phoneMiniIndicator = koinInject<PhoneAutomationMiniIndicatorManager>()
    val appContext = context

    // Keep continuous STT alive across minimize while the mini indicator session is active.
    // Otherwise ON_STOP (moveTaskToBack / home) would tear down the voice session.
    DisposableEffect(voice, lifecycleOwner, provider, settings.getSelectedTTSProvider(), phoneMiniIndicator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !phoneMiniIndicator.isSessionActive()) {
                voice.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Microphone FGS while voice + mini are both active so OEM/Android allow mic after backgrounding.
    // Also stop voice when mini is dismissed while Friendly is already backgrounded.
    LaunchedEffect(voice, phoneMiniIndicator) {
        combine(voice.state, phoneMiniIndicator.sessionActive) { session, mini ->
            session.isActive to mini
        }
            .distinctUntilChanged()
            .collect { (voiceActive, miniActive) ->
                VoiceCaptureForegroundService.sync(appContext, hold = voiceActive && miniActive)
                if (!miniActive && voiceActive) {
                    val appForeground = ProcessLifecycleOwner.get()
                        .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    if (!appForeground) {
                        voice.stop()
                    }
                }
            }
    }
    DisposableEffect(appContext, phoneMiniIndicator, voice) {
        onDispose {
            // moveTaskToBack keeps composition; if we do leave chat while mini is active,
            // leave mic FGS up until mini dismiss() releases it.
            if (!phoneMiniIndicator.isSessionActive()) {
                VoiceCaptureForegroundService.sync(appContext, hold = false)
                voice.stop()
            }
        }
    }

    val start: () -> Unit = {
        val blocked = when {
            provider == null -> context.getString(R.string.chat_page_voice_configure_asr)
            settings.getCurrentChatModel() == null -> context.getString(R.string.chat_page_voice_select_model)
            asr.state.value.isRecording -> context.getString(R.string.chat_page_voice_finish_dictation)
            vm.messageQueue.value.paused && vm.messageQueue.value.messages.isNotEmpty() ->
                context.getString(R.string.chat_page_voice_resume_queue)
            vm.conversation.value.currentMessages.any { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            } -> context.getString(R.string.chat_page_voice_pending_tools)
            else -> null
        }
        when {
            blocked != null -> toaster.show(message = blocked)
            !permission.allRequiredPermissionsGranted -> permission.requestPermissions()
            else -> voice.start(
                createAsr = { createVoiceAsr(context, client, checkNotNull(provider)) },
                speakChunk = if (settings.displaySetting.replyWithVoice && tts.isAvailable.value) {
                    { sentence, isFirst ->
                        if (sentence.isNotBlank()) {
                            tts.speak(sentence, flush = isFirst)
                        }
                    }
                } else null,
                stopSpeaking = tts::stop,
                awaitSpeakingFinished = {
                    combine(tts.isSpeaking, tts.error) { speaking, error ->
                        check(error == null) { error.orEmpty() }
                        !speaking
                    }.first { it }
                },
                serverVad = checkNotNull(provider).supportsServerVadVoiceMode,
                ttsOnlyReadQuoted = settings.displaySetting.ttsOnlyReadQuoted,
                ttsOnlyReadOutsideBrackets = settings.displaySetting.ttsOnlyReadOutsideBrackets,
                onInterrupt = {
                    vm.stopGeneration()
                    tts.stop()
                },
            )
        }
    }

    if (state.isActive) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }
    return start
}

private fun createVoiceAsr(context: Context, client: OkHttpClient, provider: ASRProviderSetting): ASRController {
    val delegate = when (provider) {
        is ASRProviderSetting.System -> {
            SystemASRController(context, provider)
        }
        is ASRProviderSetting.OpenAIRealtime -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            OpenAIRealtimeASRController(context, client, provider)
        }
        is ASRProviderSetting.DashScope -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            DashScopeASRController(context, client, provider)
        }
        is ASRProviderSetting.Volcengine -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            VolcengineASRController(context, client, provider)
        }
        is ASRProviderSetting.MiMo -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            MiMoASRController(context, client, provider)
        }
        is ASRProviderSetting.Step -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            StepASRController(context, client, provider)
        }
        is ASRProviderSetting.Gemini -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            GeminiASRController(context, client, provider)
        }
        is ASRProviderSetting.Whisper -> {
            check(provider.apiKey.isNotBlank()) { context.getString(R.string.chat_page_voice_configure_key) }
            WhisperASRController(context, client, provider)
        }
    }
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
        .build()
    return object : ASRController by delegate {
        override fun start(onTranscriptChange: (String) -> Unit) {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                "Unable to acquire audio focus for the microphone. Try again later."
            }
            delegate.start(onTranscriptChange)
        }

        override fun dispose() {
            try { delegate.dispose() } finally { audioManager.abandonAudioFocusRequest(focus) }
        }
    }
}
