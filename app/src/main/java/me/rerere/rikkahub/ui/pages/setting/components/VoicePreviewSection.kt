package me.rerere.rikkahub.ui.pages.setting.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.tts.controller.AudioPlayer
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val TAG = "VoicePreviewSection"

@Composable
fun VoicePreviewSection(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val defaultText = stringResource(R.string.setting_tts_page_test_text)
    var testText by remember { mutableStateOf(defaultText) }

    var isSynthesizing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val ttsManager = remember { TTSManager(context) }
    val audioPlayer = remember { AudioPlayer(context) }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            audioPlayer.stop()
            audioPlayer.release()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = HugeIcons.VolumeHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.setting_speech_preview_voice),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(defaultText) },
                maxLines = 2,
                textStyle = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSynthesizing || isPlaying) {
                    FilledTonalButton(
                        onClick = {
                            activeJob?.cancel()
                            audioPlayer.stop()
                            isSynthesizing = false
                            isPlaying = false
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (isSynthesizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = " " + stringResource(R.string.setting_speech_preview_synthesizing),
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Icon(
                                imageVector = HugeIcons.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = " " + stringResource(R.string.setting_speech_preview_stop),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            // Check API key requirement
                            val apiKey = when (setting) {
                                is TTSProviderSetting.Gemini -> setting.apiKey
                                is TTSProviderSetting.OpenAI -> setting.apiKey
                                is TTSProviderSetting.ElevenLabs -> setting.apiKey
                                is TTSProviderSetting.Groq -> setting.apiKey
                                is TTSProviderSetting.FishAudio -> setting.apiKey
                                is TTSProviderSetting.MiniMax -> setting.apiKey
                                is TTSProviderSetting.Qwen -> setting.apiKey
                                is TTSProviderSetting.XAI -> setting.apiKey
                                is TTSProviderSetting.MiMo -> setting.apiKey
                                is TTSProviderSetting.Step -> setting.apiKey
                                is TTSProviderSetting.Volcengine -> setting.apiKey
                                is TTSProviderSetting.SystemTTS -> "system"
                            }

                            if (apiKey.isBlank()) {
                                toaster.show(
                                    context.getString(R.string.setting_speech_api_key_required),
                                    type = ToastType.Warning
                                )
                                return@OutlinedButton
                            }

                            val phrase = testText.ifBlank { defaultText }
                            activeJob = scope.launch {
                                isSynthesizing = true
                                try {
                                    val output = ByteArrayOutputStream()
                                    var format: AudioFormat? = null
                                    var sampleRate: Int? = null

                                    ttsManager.generateSpeech(setting, TTSRequest(text = phrase))
                                        .collect { chunk ->
                                            if (format == null) format = chunk.format
                                            if (sampleRate == null) sampleRate = chunk.sampleRate
                                            output.write(chunk.data)
                                        }

                                    val audioBytes = output.toByteArray()
                                    if (audioBytes.isEmpty()) {
                                        throw IOException("Empty audio received from provider")
                                    }

                                    val response = TTSResponse(
                                        audioData = audioBytes,
                                        format = format ?: AudioFormat.MP3,
                                        sampleRate = sampleRate
                                    )

                                    isSynthesizing = false
                                    isPlaying = true
                                    audioPlayer.play(response)
                                } catch (e: CancellationException) {
                                    // Ignored on cancel
                                } catch (e: Exception) {
                                    Log.e(TAG, "Voice preview synthesis error", e)
                                    toaster.show(
                                        context.getString(
                                            R.string.setting_speech_preview_failed,
                                            e.message ?: "Unknown error"
                                        ),
                                        type = ToastType.Error
                                    )
                                } finally {
                                    isSynthesizing = false
                                    isPlaying = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = HugeIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = " " + stringResource(R.string.setting_speech_preview_voice),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
