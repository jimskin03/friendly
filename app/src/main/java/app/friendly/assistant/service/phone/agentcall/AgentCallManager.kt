package app.friendly.assistant.service.phone.agentcall

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.friendly.assistant.AppScope
import app.friendly.assistant.BuildConfig
import app.friendly.assistant.PHONE_CALL_NOTIFICATION_CHANNEL_ID
import app.friendly.assistant.R
import app.friendly.assistant.RouteActivity
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.datastore.isAgentCallActive
import app.friendly.assistant.service.phone.PhoneCallActionReceiver
import okhttp3.OkHttpClient

private const val TAG = "AgentCallManager"
private const val NOTIFICATION_ID = 2005

class AgentCallManager(
    private val app: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    httpClient: OkHttpClient,
) {
    private val vapiProvider = VapiCallProvider(httpClient)
    private val elevenLabsProvider = ElevenLabsCallProvider(httpClient)

    private val _activeCall = MutableStateFlow<AgentCallSnapshot?>(null)
    val activeCall: StateFlow<AgentCallSnapshot?> = _activeCall.asStateFlow()

    private val _lastCallStatus = MutableStateFlow<AgentCallStatus?>(null)
    val lastCallStatus: StateFlow<AgentCallStatus?> = _lastCallStatus.asStateFlow()

    private var activeJob: Job? = null
    private var currentCallId: String? = null

    private fun getProvider(providerId: String): AgentCallProvider {
        return when (providerId.lowercase().trim()) {
            "elevenlabs" -> elevenLabsProvider
            else -> vapiProvider
        }
    }

    suspend fun executeCall(
        request: AgentCallRequest,
        wait: Boolean = true,
    ): AgentCallStatus {
        if (BuildConfig.IS_PLAY_BUILD) {
            return AgentCallStatus(
                callId = "",
                phase = AgentCallPhase.Failed,
                error = "AI Phone Calls are unavailable in the Play Store build.",
            )
        }
        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        if (!displaySetting.isAgentCallActive) {
            return AgentCallStatus(
                callId = "",
                phase = AgentCallPhase.Failed,
                error = "AI Phone Calls are disabled in Settings. Enable Settings → AI Phone Calls first.",
            )
        }

        val setting = displaySetting.agentCallSetting
        if (setting.apiKey.isBlank()) {
            return AgentCallStatus(
                callId = "",
                phase = AgentCallPhase.Failed,
                error = "AI Phone Calls API Key is not configured. Please set your API key in Settings.",
            )
        }
        if (setting.phoneNumberId.isBlank()) {
            return AgentCallStatus(
                callId = "",
                phase = AgentCallPhase.Failed,
                error = "AI Phone Calls Phone Number ID is not configured. Please configure it in Settings.",
            )
        }

        val provider = getProvider(setting.provider)

        // Effective request with defaults applied from settings
        val effectiveRequest = request.copy(
            discloseAi = setting.discloseAi && request.discloseAi,
            ownerName = request.ownerName.ifBlank { setting.ownerName },
            maxDurationSeconds = if (request.maxDurationSeconds > 0) request.maxDurationSeconds else setting.maxDurationSec,
        )

        Log.i(TAG, "Starting autonomous agent call to ${effectiveRequest.toNumber} via ${provider.providerId}")
        val startResult = provider.startCall(setting, effectiveRequest)
        if (startResult.isFailure) {
            val error = startResult.exceptionOrNull()?.message ?: "Failed to initiate call"
            Log.e(TAG, "Call initiation failed: $error")
            return AgentCallStatus(
                callId = "",
                phase = AgentCallPhase.Failed,
                toNumber = effectiveRequest.toNumber,
                error = error,
            )
        }

        val callId = startResult.getOrThrow()
        currentCallId = callId

        _activeCall.value = AgentCallSnapshot(
            callId = callId,
            phase = AgentCallPhase.Queued,
            toNumber = effectiveRequest.toNumber,
        )

        updateNotification("Calling ${effectiveRequest.toNumber}", "Initiating call...")

        if (!wait) {
            // Launch background polling loop to keep state up to date
            startPollingLoop(provider, setting, callId, effectiveRequest.maxDurationSeconds)
            return AgentCallStatus(
                callId = callId,
                phase = AgentCallPhase.Queued,
                toNumber = effectiveRequest.toNumber,
            )
        }

        // Wait synchronously (in calling coroutine) until call concludes
        return pollUntilFinished(provider, setting, callId, effectiveRequest.maxDurationSeconds)
    }

    private suspend fun pollUntilFinished(
        provider: AgentCallProvider,
        setting: app.friendly.assistant.data.datastore.AgentCallSetting,
        callId: String,
        maxDurationSec: Int,
    ): AgentCallStatus {
        val startTime = System.currentTimeMillis()
        val timeoutMs = (maxDurationSec + 30) * 1000L
        var latestStatus = AgentCallStatus(callId = callId, phase = AgentCallPhase.Queued)

        try {
            while (true) {
                delay(2000L)
                val statusResult = provider.getCallStatus(setting, callId)
                if (statusResult.isSuccess) {
                    latestStatus = statusResult.getOrThrow()
                    _activeCall.value = _activeCall.value?.copy(
                        phase = latestStatus.phase,
                        durationSeconds = latestStatus.durationSeconds,
                        endedReason = latestStatus.endedReason,
                    )
                    _lastCallStatus.value = latestStatus

                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val info = when (latestStatus.phase) {
                        AgentCallPhase.Queued -> "Connecting..."
                        AgentCallPhase.Ringing -> "Ringing..."
                        AgentCallPhase.InProgress -> "In progress (${latestStatus.durationSeconds.takeIf { it > 0 } ?: elapsedSec}s)"
                        AgentCallPhase.Ended -> "Call completed"
                        AgentCallPhase.Failed -> "Call failed"
                    }
                    updateNotification("AI calling ${_activeCall.value?.toNumber.orEmpty()}", info)

                    if (latestStatus.phase == AgentCallPhase.Ended || latestStatus.phase == AgentCallPhase.Failed) {
                        break
                    }
                } else {
                    Log.w(TAG, "Status check error: ${statusResult.exceptionOrNull()?.message}")
                }

                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    Log.w(TAG, "Call timed out after $maxDurationSec seconds; ending call $callId")
                    provider.endCall(setting, callId)
                    break
                }
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Call polling cancelled; ending call $callId")
            provider.endCall(setting, callId)
            throw e
        } finally {
            clearNotification()
            _activeCall.value = null
            currentCallId = null
        }

        return latestStatus
    }

    private fun startPollingLoop(
        provider: AgentCallProvider,
        setting: app.friendly.assistant.data.datastore.AgentCallSetting,
        callId: String,
        maxDurationSec: Int,
    ) {
        activeJob?.cancel()
        activeJob = appScope.launch {
            pollUntilFinished(provider, setting, callId, maxDurationSec)
        }
    }

    suspend fun getCallStatus(callId: String? = null): AgentCallStatus {
        val targetId = callId ?: currentCallId ?: _lastCallStatus.value?.callId
        if (targetId.isNullOrBlank()) {
            return AgentCallStatus(callId = "", phase = AgentCallPhase.Failed, error = "No active or recent call found")
        }
        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        val provider = getProvider(displaySetting.agentCallSetting.provider)
        val result = provider.getCallStatus(displaySetting.agentCallSetting, targetId)
        return result.getOrElse {
            AgentCallStatus(callId = targetId, phase = AgentCallPhase.Failed, error = it.message)
        }
    }

    suspend fun endCall(callId: String? = null): Result<Unit> {
        val targetId = callId ?: currentCallId ?: _activeCall.value?.callId
        if (targetId.isNullOrBlank()) {
            return Result.success(Unit)
        }
        activeJob?.cancel()
        clearNotification()
        _activeCall.value = null
        currentCallId = null

        val displaySetting = settingsStore.settingsFlow.value.displaySetting
        val provider = getProvider(displaySetting.agentCallSetting.provider)
        return provider.endCall(displaySetting.agentCallSetting, targetId)
    }

    suspend fun testConnection(
        setting: app.friendly.assistant.data.datastore.AgentCallSetting? = null,
    ): Result<String> {
        val targetSetting = setting ?: settingsStore.settingsFlow.value.displaySetting.agentCallSetting
        val provider = getProvider(targetSetting.provider)
        return provider.testConnection(targetSetting)
    }

    private fun updateNotification(title: String, content: String) {
        runCatching {
            val endIntent = Intent(app, PhoneCallActionReceiver::class.java).apply {
                action = PhoneCallActionReceiver.ACTION_AGENT_CALL_END
            }
            val pendingEnd = PendingIntent.getBroadcast(
                app,
                NOTIFICATION_ID,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openIntent = Intent(app, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                app,
                NOTIFICATION_ID + 1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(app, PHONE_CALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_brand_neon)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingOpen)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End Call",
                    pendingEnd,
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
        }.onFailure {
            Log.w(TAG, "Failed to post agent call notification: ${it.message}")
        }
    }

    private fun clearNotification() {
        runCatching {
            NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        }
    }
}
