package me.rerere.rikkahub.service.phone

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.PHONE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.NotificationUtil

private const val TAG = "PhoneCallController"

enum class CellularCallStatus {
    Unknown,
    Idle,
    Ringing,
    Offhook,
}

data class CellularCallSnapshot(
    val accessEnabled: Boolean = false,
    val autoAnswerAttempt: Boolean = false,
    val status: CellularCallStatus = CellularCallStatus.Unknown,
    val number: String? = null,
    /** Dialed number for an ACTION_CALL that has not yet gone live and then idle. */
    val outboundNumber: String? = null,
    val callPhoneGranted: Boolean = false,
    val readPhoneStateGranted: Boolean = false,
    val answerPhoneCallsGranted: Boolean = false,
    val accessibilityActive: Boolean = false,
    val silentAnswerReliable: Boolean = false,
    val silentHangupReliable: Boolean = false,
    val limitation: String = PhoneCallController.LIMITATION,
)

data class PhoneCallActionResult(
    val success: Boolean,
    val action: String,
    val detail: String,
    val mode: String? = null,
    val attempts: List<String> = emptyList(),
)

/**
 * Opt-in cellular calling. Friendly is not the default dialer and does not place
 * WhatsApp or other VoIP calls.
 *
 * Place: [Intent.ACTION_CALL] when CALL_PHONE is granted, otherwise the system dialer.
 * State: TelephonyManager callback when READ_PHONE_STATE is granted.
 * Answer / hang-up: TelecomManager APIs are limited to the default phone app on
 * Android 10+, so those calls are best-effort and usually fail. Accessibility can
 * tap Answer / End on the in-call UI when that UI is visible; it is not silent or
 * reliable across OEMs. Incoming calls raise a notification that opens the dialer.
 */
class PhoneCallController(
    private val app: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    private val telephony = app.getSystemService(TelephonyManager::class.java)
    private val telecom = app.getSystemService(TelecomManager::class.java)

    private val _snapshot = MutableStateFlow(CellularCallSnapshot())
    val snapshot: StateFlow<CellularCallSnapshot> = _snapshot.asStateFlow()

    private var listenerRegistered = false
    private var answerAttemptedForRing = false

    /** True after Ringing or Offhook has been seen for the current outbound attempt. */
    @Volatile
    private var outboundSawLive: Boolean = false

    @Suppress("DEPRECATION")
    private val legacyListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallState(state, phoneNumber)
        }
    }

    private var modernCallback: Any? = null

    init {
        appScope.launch(Dispatchers.Main.immediate) {
            settingsStore.settingsFlow.collect {
                syncListener()
            }
        }
    }

    fun syncListener() {
        val readGranted = hasPermission(Manifest.permission.READ_PHONE_STATE)
        publishSnapshot(status = _snapshot.value.status, number = _snapshot.value.number)
        if (!readGranted) {
            unregisterListener()
            if (_snapshot.value.status != CellularCallStatus.Unknown) {
                publishSnapshot(CellularCallStatus.Unknown, number = null)
            }
            return
        }
        if (listenerRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernCallback = PhoneCallTelephony31.register(telephony, app) { state ->
                    handleCallState(state, number = null)
                }
            } else {
                @Suppress("DEPRECATION")
                telephony.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            listenerRegistered = true
            Log.i(TAG, "Telephony call-state listener registered")
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to register telephony listener", e)
            listenerRegistered = false
        }
    }

    suspend fun placeCall(rawNumber: String): PhoneCallActionResult = withContext(Dispatchers.Main) {
        when (val parsed = parseNumber(rawNumber)) {
            is ParsedNumber.Emergency -> PhoneCallActionResult(
                success = false,
                action = "place_call",
                detail = "Refusing to dial an emergency number.",
            )
            is ParsedNumber.Invalid -> PhoneCallActionResult(
                success = false,
                action = "place_call",
                detail = parsed.reason,
            )
            is ParsedNumber.Ok -> {
                val granted = hasPermission(Manifest.permission.CALL_PHONE)
                val uri = Uri.fromParts("tel", parsed.number, null)
                val intent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    if (granted) {
                        app.startActivity(intent)
                    } else {
                        app.startPhoneAutomationActivity(intent)
                    }
                    val mode = if (granted) "action_call" else "action_dial"
                    if (mode == "action_call") {
                        outboundSawLive = false
                        publishSnapshot(
                            status = _snapshot.value.status,
                            number = _snapshot.value.number,
                            outboundNumber = parsed.number,
                        )
                    }
                    PhoneCallActionResult(
                        success = true,
                        action = "place_call",
                        mode = mode,
                        detail = if (granted) {
                            "Started a cellular call to ${parsed.number} with the system phone app. " +
                                "Voice input remains active while the cellular call is in progress. " +
                                "WhatsApp and other VoIP apps are not used."
                        } else {
                            "CALL_PHONE is not granted, so the system dialer was opened with ${parsed.number} filled in. " +
                                "The user must tap call. Grant phone permissions in Phone Automation to place calls directly."
                        },
                    )
                } catch (e: Exception) {
                    val notified = postActionNotification(
                        title = app.getString(R.string.phone_call_place_notification_title),
                        text = app.getString(R.string.phone_call_place_notification_body, parsed.number),
                        contentIntent = activityPendingIntent(intent, REQUEST_PLACE),
                    )
                    Log.w(TAG, "ACTION_CALL/DIAL start failed; notification=$notified", e)
                    PhoneCallActionResult(
                        success = false,
                        action = "place_call",
                        mode = "notification_fallback",
                        detail = "Android blocked starting the dialer from the background (${e.javaClass.simpleName}). " +
                            (if (notified) "A notification was posted; tap it to continue the call."
                            else "Notification permission is missing, so the dialer could not be opened."),
                    )
                }
            }
        }
    }

    suspend fun endCall(): PhoneCallActionResult = withContext(Dispatchers.Main) {
        val status = _snapshot.value.status
        if (status == CellularCallStatus.Idle) {
            return@withContext PhoneCallActionResult(
                success = false,
                action = "end_call",
                detail = "No active cellular call (state is idle).",
            )
        }
        val attempts = mutableListOf<String>()
        val telecom = tryTelecomEnd()
        attempts += "telecom_end_call:$telecom"
        if (telecom == "ok") {
            return@withContext PhoneCallActionResult(
                success = true,
                action = "end_call",
                mode = "telecom",
                attempts = attempts,
                detail = "TelecomManager.endCall() reported success. This usually only works for the default phone app.",
            )
        }
        val service = PhoneAutomationService.instance
        if (service != null && safeToTouchCallUi(PhoneCallUiAction.End)) {
            if (service.performCallUiAction(PhoneCallUiAction.End)) {
                attempts += "accessibility_click:ok"
                return@withContext PhoneCallActionResult(
                    success = true,
                    action = "end_call",
                    mode = "accessibility_click",
                    attempts = attempts,
                    detail = "Tapped an End control in the phone UI. This is best-effort and depends on the dialer.",
                )
            }
            attempts += "accessibility_click:not_found"
            val hit = service.findCallControl(PhoneCallUiAction.End)
            if (hit != null && service.click(hit.x, hit.y)) {
                attempts += "accessibility_gesture:ok"
                return@withContext PhoneCallActionResult(
                    success = true,
                    action = "end_call",
                    mode = "accessibility_gesture",
                    attempts = attempts,
                    detail = "Dispatched a tap on the End control. Confirm the call actually dropped.",
                )
            }
            attempts += "accessibility_gesture:not_found"
        } else {
            attempts += "accessibility:unavailable_or_unsafe"
        }
        val shown = tryShowInCallScreen()
        attempts += "show_in_call_screen:$shown"
        if (shown == "ok") {
            delay(450)
            if (PhoneAutomationService.instance?.performCallUiAction(PhoneCallUiAction.End) == true) {
                attempts += "accessibility_after_show:ok"
                return@withContext PhoneCallActionResult(
                    success = true,
                    action = "end_call",
                    mode = "accessibility_after_show",
                    attempts = attempts,
                    detail = "Opened the in-call screen and tapped End. Confirm the call dropped.",
                )
            }
        }
        openDialer()
        attempts += "opened_dialer"
        PhoneCallActionResult(
            success = false,
            action = "end_call",
            mode = "dialer_fallback",
            attempts = attempts,
            detail = "Could not silently hang up. Android 10+ allows TelecomManager.endCall() only for the default dialer, " +
                "and Friendly is not a full phone app. The system dialer was opened so the user can tap End. " +
                "WhatsApp calls are not controlled. Attempts: ${attempts.joinToString()}",
        )
    }

    fun currentSnapshot(): CellularCallSnapshot {
        publishSnapshot(_snapshot.value.status, _snapshot.value.number)
        return _snapshot.value
    }

    fun openDialer(number: String? = null) {
        val uri = number?.let { Uri.fromParts("tel", it, null) }
        val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startPhoneAutomationActivity(intent) }
            .onFailure { Log.w(TAG, "Unable to open dialer", it) }
    }

    fun attemptAnswerFromNotification() {
        appScope.launch(Dispatchers.Main) { attemptAnswer() }
    }

    fun attemptEndFromNotification() {
        appScope.launch(Dispatchers.Main) { endCall() }
    }

    private suspend fun attemptAnswer(): PhoneCallActionResult {
        if (_snapshot.value.status != CellularCallStatus.Ringing &&
            _snapshot.value.status != CellularCallStatus.Unknown
        ) {
            openDialer()
            return PhoneCallActionResult(
                success = false,
                action = "answer_call",
                detail = "No ringing cellular call. Opened the dialer. Silent auto-answer is not reliable.",
            )
        }
        val attempts = mutableListOf<String>()
        val telecom = tryTelecomAccept()
        attempts += "telecom_accept:$telecom"
        if (telecom == "ok") {
            return PhoneCallActionResult(
                success = true,
                action = "answer_call",
                mode = "telecom",
                attempts = attempts,
                detail = "TelecomManager.acceptRingingCall() reported success. On Android 10+ this usually requires being the default dialer.",
            )
        }
        val service = PhoneAutomationService.instance
        if (service != null && safeToTouchCallUi(PhoneCallUiAction.Answer)) {
            if (service.performCallUiAction(PhoneCallUiAction.Answer)) {
                attempts += "accessibility_click:ok"
                return PhoneCallActionResult(
                    success = true,
                    action = "answer_call",
                    mode = "accessibility_click",
                    attempts = attempts,
                    detail = "Tapped an Answer control. This is not silent and often misses OEM incoming-call UIs.",
                )
            }
            attempts += "accessibility_click:not_found"
            val hit = service.findCallControl(PhoneCallUiAction.Answer)
            if (hit != null && service.click(hit.x, hit.y)) {
                attempts += "accessibility_gesture:ok"
                return PhoneCallActionResult(
                    success = true,
                    action = "answer_call",
                    mode = "accessibility_gesture",
                    attempts = attempts,
                    detail = "Dispatched a tap on Answer. Confirm the call connected.",
                )
            }
        } else {
            attempts += "accessibility:unavailable_or_unsafe"
        }
        openDialer()
        attempts += "opened_dialer"
        return PhoneCallActionResult(
            success = false,
            action = "answer_call",
            mode = "dialer_fallback",
            attempts = attempts,
            detail = "Silent auto-answer is not available to a non-dialer app. Opened the system phone UI instead. " +
                "Attempts: ${attempts.joinToString()}",
        )
    }

    private fun handleCallState(state: Int, number: String?) {
        val status = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CellularCallStatus.Ringing
            TelephonyManager.CALL_STATE_OFFHOOK -> CellularCallStatus.Offhook
            else -> CellularCallStatus.Idle
        }
        val previous = _snapshot.value.status
        val outbound = when (status) {
            CellularCallStatus.Ringing, CellularCallStatus.Offhook -> {
                outboundSawLive = true
                _snapshot.value.outboundNumber
            }
            else -> if (outboundSawLive) {
                outboundSawLive = false
                null
            } else {
                _snapshot.value.outboundNumber
            }
        }
        val shownNumber = number?.takeIf { it.isNotBlank() }
            ?: _snapshot.value.number?.takeIf { status != CellularCallStatus.Idle }
        publishSnapshot(status, shownNumber, outboundNumber = outbound)
        Log.i(TAG, "Call state $previous -> $status")

        if (status == CellularCallStatus.Ringing && previous != CellularCallStatus.Ringing) {
            answerAttemptedForRing = false
            showIncomingNotification(shownNumber)
        } else if (status != CellularCallStatus.Ringing) {
            NotificationUtil.cancel(app, INCOMING_NOTIFICATION_ID)
            answerAttemptedForRing = false
        }
    }

    private fun showIncomingNotification(number: String?) {
        val who = number ?: app.getString(R.string.phone_call_number_hidden)
        val openDialerIntent = Intent(app, PhoneCallActionReceiver::class.java).apply {
            action = PhoneCallActionReceiver.ACTION_OPEN_DIALER
        }
        val notification = NotificationCompat.Builder(app, PHONE_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(app.getString(R.string.phone_call_incoming_title))
            .setContentText(app.getString(R.string.phone_call_incoming_body, who))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(broadcastPendingIntent(openDialerIntent, REQUEST_OPEN_DIALER))
            .addAction(
                0,
                app.getString(R.string.phone_call_action_open_dialer),
                broadcastPendingIntent(openDialerIntent, REQUEST_OPEN_DIALER),
            )
            .build()
        if (!NotificationUtil.hasNotificationPermission(app)) {
            Log.w(TAG, "Incoming call notification skipped; POST_NOTIFICATIONS missing")
            openDialer()
            return
        }
        androidx.core.app.NotificationManagerCompat.from(app).notify(INCOMING_NOTIFICATION_ID, notification)
    }

    private fun postActionNotification(title: String, text: String, contentIntent: PendingIntent): Boolean {
        if (!NotificationUtil.hasNotificationPermission(app)) return false
        val notification = NotificationCompat.Builder(app, PHONE_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        androidx.core.app.NotificationManagerCompat.from(app).notify(PLACE_NOTIFICATION_ID, notification)
        return true
    }

    @Suppress("DEPRECATION")
    private fun tryTelecomEnd(): String = try {
        if (telecom.endCall()) "ok" else "returned_false"
    } catch (e: SecurityException) {
        "security:${e.message?.take(120)}"
    } catch (e: Exception) {
        "error:${e.javaClass.simpleName}"
    }

    @Suppress("DEPRECATION")
    private fun tryTelecomAccept(): String = try {
        telecom.acceptRingingCall()
        "ok"
    } catch (e: SecurityException) {
        "security:${e.message?.take(120)}"
    } catch (e: Exception) {
        "error:${e.javaClass.simpleName}"
    }

    private fun tryShowInCallScreen(): String = try {
        telecom.showInCallScreen(false)
        "ok"
    } catch (e: SecurityException) {
        "security"
    } catch (e: Exception) {
        "error:${e.javaClass.simpleName}"
    }

    private fun safeToTouchCallUi(action: PhoneCallUiAction): Boolean {
        val status = _snapshot.value.status
        if (action == PhoneCallUiAction.Answer && status == CellularCallStatus.Ringing) return true
        if (action == PhoneCallUiAction.End &&
            (status == CellularCallStatus.Offhook || status == CellularCallStatus.Ringing)
        ) {
            return true
        }
        val pkg = PhoneAutomationService.instance
            ?.rootInActiveWindow
            ?.packageName
            ?.toString()
            .orEmpty()
            .lowercase()
        return pkg.contains("dialer") || pkg.contains("incall") || pkg.contains("telecom")
    }

    private fun publishSnapshot(
        status: CellularCallStatus,
        number: String?,
        outboundNumber: String? = _snapshot.value.outboundNumber,
    ) {
        _snapshot.value = CellularCallSnapshot(
            accessEnabled = true,
            autoAnswerAttempt = false,
            status = status,
            number = number,
            outboundNumber = outboundNumber,
            callPhoneGranted = hasPermission(Manifest.permission.CALL_PHONE),
            readPhoneStateGranted = hasPermission(Manifest.permission.READ_PHONE_STATE),
            answerPhoneCallsGranted = hasPermission(Manifest.permission.ANSWER_PHONE_CALLS),
            accessibilityActive = PhoneAutomationService.isRunning(),
            silentAnswerReliable = false,
            silentHangupReliable = false,
            limitation = LIMITATION,
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun unregisterListener() {
        if (!listenerRegistered) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PhoneCallTelephony31.unregister(telephony, modernCallback)
                modernCallback = null
            } else {
                @Suppress("DEPRECATION")
                telephony.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        listenerRegistered = false
    }

    private fun activityPendingIntent(intent: Intent, request: Int): PendingIntent =
        PendingIntent.getActivity(
            app,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcastPendingIntent(intent: Intent, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            app,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val LIMITATION =
            "Cellular calls only, via the system phone app. Friendly is not the default dialer, so silent " +
                "auto-answer and silent hang-up are not reliable on Android 10+ (TelecomManager rejects them). " +
                "Hang-up and answer fall back to accessibility taps on the phone UI plus a notification that opens " +
                "the dialer. WhatsApp and other VoIP apps are not controlled. Call state and the incoming number " +
                "require READ_PHONE_STATE; the number is often hidden by Android."

        private const val INCOMING_NOTIFICATION_ID = 2005
        private const val PLACE_NOTIFICATION_ID = 2006
        private const val REQUEST_OPEN_DIALER = 41
        private const val REQUEST_ANSWER = 42
        private const val REQUEST_PLACE = 43

        private val emergencyNumbers = setOf(
            "112", "911", "999", "000", "110", "119", "118", "190", "192", "193",
        )

        fun parseNumber(raw: String): ParsedNumber {
            var value = raw.trim()
            if (value.startsWith("tel:", ignoreCase = true)) value = value.substring(4)
            value = value.replace(Regex("[\\s().-]"), "")
            if (!value.matches(Regex("^\\+?[0-9]{3,16}$"))) {
                return ParsedNumber.Invalid(
                    "Number must be 3–16 digits with an optional leading +. WhatsApp usernames and USSD codes are not supported.",
                )
            }
            val digits = value.removePrefix("+")
            if (digits in emergencyNumbers) return ParsedNumber.Emergency
            return ParsedNumber.Ok(value)
        }
    }
}

sealed class ParsedNumber {
    data class Ok(val number: String) : ParsedNumber()
    data class Invalid(val reason: String) : ParsedNumber()
    data object Emergency : ParsedNumber()
}
