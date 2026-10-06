package me.rerere.rikkahub.service.phone

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore

private const val TAG = "CallAudioBridge"

/**
 * Agent-on-call audio path (SCO-first foundation).
 *
 * When [enableAgentSpeakOnCalls] is on and a cellular call is Active (off-hook),
 * starts Bluetooth SCO so call audio routes through the communication streams that
 * existing ASR ([VOICE_COMMUNICATION] capture) and TTS ([USAGE_VOICE_COMMUNICATION]
 * playback) already use. Stops SCO on call end, toggle off, or destroy.
 *
 * Caveats (OEM-dependent):
 * - SCO often needs a real BT headset nearby or OEM call-audio routing support.
 * - True "app as headset" virtual HFP is not implemented here.
 * - No full-audio logging; only state transitions are logged.
 * - No auto-answer; outbound [place_call] first.
 */
enum class CallAudioBridgeStatus {
    Idle,
    Starting,
    Bridging,
    Stopping,
    Error,
}

data class CallAudioBridgeSnapshot(
    val status: CallAudioBridgeStatus = CallAudioBridgeStatus.Idle,
    val speakOnCallsEnabled: Boolean = false,
    val scoOn: Boolean = false,
    val detail: String = "",
)

class CallAudioBridge(
    private val app: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val phoneCallController: PhoneCallController,
) {
    private val audioManager = app.getSystemService(AudioManager::class.java)

    private val _snapshot = MutableStateFlow(CallAudioBridgeSnapshot())
    val snapshot: StateFlow<CallAudioBridgeSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var scoReceiverRegistered = false

    @Volatile
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    @Volatile
    private var wanted = false

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            Log.i(TAG, "SCO audio state=$state wanted=$wanted")
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    if (wanted) {
                        publish(
                            CallAudioBridgeStatus.Bridging,
                            scoOn = true,
                            detail = "Bluetooth SCO connected. ASR/TTS use communication streams on the call path.",
                        )
                    }
                }
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    if (wanted) {
                        // Some OEMs flap; keep Starting so we can retry via setCommunicationDevice.
                        publish(
                            CallAudioBridgeStatus.Starting,
                            scoOn = false,
                            detail = "SCO disconnected while bridge wanted; retrying communication device selection.",
                        )
                        preferScoCommunicationDevice()
                    } else {
                        publish(CallAudioBridgeStatus.Idle, scoOn = false, detail = "SCO released.")
                    }
                }
                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                    publish(
                        CallAudioBridgeStatus.Error,
                        scoOn = false,
                        detail = "SCO error from AudioManager. OEM call-audio routing may block headset-role SCO.",
                    )
                }
            }
        }
    }

    init {
        appScope.launch(Dispatchers.Main.immediate) {
            combine(
                settingsStore.settingsFlow,
                phoneCallController.snapshot,
            ) { settings, call ->
                val enabled = settings.displaySetting.enableAgentSpeakOnCalls
                val active = call.session == CallSessionPhase.Active
                enabled to active
            }
                .distinctUntilChanged()
                .collect { (enabled, active) ->
                    val shouldBridge = enabled && active
                    if (shouldBridge) startBridge() else stopBridge(reason = if (!enabled) "toggle_off" else "call_not_active")
                }
        }
    }

    fun currentSnapshot(): CallAudioBridgeSnapshot = _snapshot.value

    /** True when the bridge is attempting or holding SCO for an active call. */
    fun isBridgeLive(): Boolean {
        val s = _snapshot.value.status
        return s == CallAudioBridgeStatus.Starting || s == CallAudioBridgeStatus.Bridging
    }

    /** Called when the FGS is destroyed so SCO cannot outlive the service. */
    fun releaseForDestroy() {
        stopBridge(reason = "service_destroy")
    }

    private fun startBridge() {
        if (wanted && (_snapshot.value.status == CallAudioBridgeStatus.Starting ||
                _snapshot.value.status == CallAudioBridgeStatus.Bridging)
        ) {
            return
        }
        wanted = true
        publish(
            CallAudioBridgeStatus.Starting,
            scoOn = false,
            detail = "Starting Bluetooth SCO for agent-on-call voice.",
        )
        CallAudioBridgeService.acquire(app)
        registerScoReceiver()
        try {
            previousAudioMode = audioManager.mode
            // MODE_IN_COMMUNICATION suits VoIP-style capture/playback used by ASR/TTS.
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            preferScoCommunicationDevice()
            Log.i(TAG, "SCO start requested (mode=IN_COMMUNICATION)")
        } catch (e: SecurityException) {
            Log.w(TAG, "SCO start blocked (Bluetooth/connect permission?)", e)
            publish(
                CallAudioBridgeStatus.Error,
                scoOn = false,
                detail = "SCO start SecurityException: grant BLUETOOTH_CONNECT and keep call audio routing available. ${e.message?.take(80).orEmpty()}",
            )
        } catch (e: Exception) {
            Log.w(TAG, "SCO start failed", e)
            publish(
                CallAudioBridgeStatus.Error,
                scoOn = false,
                detail = "SCO start failed (${e.javaClass.simpleName}). OEM may not expose headset-role SCO to third-party apps.",
            )
        }
    }

    private fun stopBridge(reason: String) {
        if (!wanted && _snapshot.value.status == CallAudioBridgeStatus.Idle) {
            CallAudioBridgeService.release(app)
            return
        }
        wanted = false
        publish(CallAudioBridgeStatus.Stopping, scoOn = _snapshot.value.scoOn, detail = "Stopping SCO ($reason).")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { audioManager.clearCommunicationDevice() }
            }
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            audioManager.mode = previousAudioMode
        } catch (e: Exception) {
            Log.w(TAG, "SCO stop failed", e)
        } finally {
            unregisterScoReceiver()
            CallAudioBridgeService.release(app)
            publish(CallAudioBridgeStatus.Idle, scoOn = false, detail = "Bridge idle ($reason).")
            Log.i(TAG, "SCO stopped ($reason)")
        }
    }

    private fun preferScoCommunicationDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val sco = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        } ?: return
        val ok = runCatching { audioManager.setCommunicationDevice(sco) }.getOrDefault(false)
        Log.i(TAG, "setCommunicationDevice(SCO)=$ok id=${sco.id}")
    }

    private fun registerScoReceiver() {
        if (scoReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        ContextCompat.registerReceiver(
            app,
            scoReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scoReceiverRegistered = true
    }

    private fun unregisterScoReceiver() {
        if (!scoReceiverRegistered) return
        runCatching { app.unregisterReceiver(scoReceiver) }
        scoReceiverRegistered = false
    }

    private fun publish(status: CallAudioBridgeStatus, scoOn: Boolean, detail: String) {
        _snapshot.value = CallAudioBridgeSnapshot(
            status = status,
            speakOnCallsEnabled = settingsStore.settingsFlow.value.displaySetting.enableAgentSpeakOnCalls,
            scoOn = scoOn,
            detail = detail,
        )
    }

    fun bluetoothConnectGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun bluetoothAdapterEnabled(): Boolean {
        val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            app.getSystemService(BluetoothManager::class.java)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
        return adapter?.isEnabled == true
    }
}
