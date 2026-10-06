package app.friendly.assistant.service.phone

import app.friendly.assistant.BuildConfig

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Voice
import app.friendly.assistant.ui.pages.chat.VoicePhase
import app.friendly.assistant.ui.theme.LocalDarkMode
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.FxListener
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.gesture.FxDrag
import com.petterp.floatingx.core.gesture.FxGesture
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.core.update
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import app.friendly.assistant.AppScope
import app.friendly.assistant.PHONE_AUTOMATION_NOTIFICATION_CHANNEL_ID
import app.friendly.assistant.R
import app.friendly.assistant.RouteActivity
import app.friendly.assistant.data.datastore.PhoneAutomationWindowMode
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.ui.hooks.readStringPreference
import app.friendly.assistant.ui.theme.RikkahubTheme
import app.friendly.assistant.utils.cancelNotification
import app.friendly.assistant.service.VoiceCaptureForegroundService
import app.friendly.assistant.utils.sendNotification

private const val TAG = "PhoneAutoMiniIndicator"
private const val FLOATING_TAG = "phone_automation_mini_indicator"
private const val CLOSE_ZONE_TAG = "phone_automation_mini_close_zone"

/**
 * Phone Automation mini indicator — compact premium glass cat mark that grows
 * into a short frosted status pill while a call, outbound dial, or the agent
 * is active.
 *
 * Entry is explicit activate() from the Phone sheet "Minimize with mini
 * indicator" or a long-press on the phone icon (the caller may moveTaskToBack).
 * When the preference is on, the session also starts on its own if a cellular
 * call is ringing or off-hook, an outbound ACTION_CALL has not yet gone live
 * and then idle, a phone tool is running, the reply that used one is still
 * generating, or other Phone Automation work is Running. Auto-show does not
 * minimize Friendly and does not ask for overlay permission. Without
 * SYSTEM_ALERT_WINDOW, the ongoing notification (id 2003) is the backup.
 *
 * Dragging the bubble to the close zone while a call or that work is still
 * live suppresses auto-show until that live condition is fully quiet (not
 * ringing, not off-hook, no outbound, no phone-tool step, not holding for
 * the reply, work not Running). The next live event may auto-show again.
 * Manual activate() clears the suppress flag.
 *
 * While the session is active:
 *  1. Ongoing notification while Friendly is backgrounded (OEM-safe backup; tap reopens app).
 *     The body uses the same live status line as the pill.
 *  2. System-overlay cat with that live line and LED whenever SYSTEM_ALERT_WINDOW
 *     is granted (shown immediately so it cannot race ProcessLifecycle ON_STOP).
 *
 * Single tap → bring Friendly back (last chat / last place).
 * Drag toward the bottom → X close zone; drop dismisses the mini session.
 *
 * Voice phase is reported by the chat screen (not injected). It can change the
 * line only while a mini session is already active; voice alone does not auto-show.
 *
 * While Phone Automation window mode is Split or Popup, the mini session stays
 * off: no auto-show, activate() is ignored, and an already-visible indicator
 * is hidden. A live call or phone-tool run does not bring it back.
 *
 * While the mini session is active, continuous voice STT is allowed to keep
 * running across ProcessLifecycle ON_STOP (see VoiceMode + VoiceCaptureForegroundService).
 */
class PhoneAutomationMiniIndicatorManager(
    private val app: Application,
    appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val phoneCallController: PhoneCallController,
    private val agentCallManager: app.friendly.assistant.service.phone.agentcall.AgentCallManager,
) {
    companion object {
        const val NOTIFICATION_ID = 2003
        const val EXTRA_FOCUS_INPUT = "focusInput"
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val MINI_BAR_LONG_PRESS_TIMEOUT_MS = 500L

        private val _voicePhase = MutableStateFlow(VoicePhase.Off)
        val voicePhase: StateFlow<VoicePhase> = _voicePhase.asStateFlow()
    }

    /** Chat screen reports the current voice phase. Not a reason to auto-show. */
    fun reportVoicePhase(phase: VoicePhase) {
        _voicePhase.value = phase
    }

    private var control: FxControl? = null
    private var closeZoneControl: FxControl? = null
    private var notificationVisible = false

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _showOptions = MutableStateFlow(false)
    val showOptions: StateFlow<Boolean> = _showOptions.asStateFlow()

    fun lockPosition() {
        control?.update {
            gesture {
                drag = FxDrag.DISABLED
                click = false
                longPressTimeout = MINI_BAR_LONG_PRESS_TIMEOUT_MS
            }
        }
    }

    fun unlockPosition() {
        control?.update {
            gesture {
                drag = FxDrag.IMMEDIATE
                click = true
                longPressTimeout = MINI_BAR_LONG_PRESS_TIMEOUT_MS
            }
        }
    }

    fun dismissMenu() {
        if (_showOptions.value) {
            _showOptions.value = false
            unlockPosition()
        }
    }

    /**
     * User dragged the bubble away while a call or phone-automation work was
     * still live, including the gap while a reply that used a phone tool is
     * still generating. Stays set until that live condition is fully quiet
     * so we do not recreate the bubble on the next status tick.
     */
    @Volatile
    private var suppressAutoShow: Boolean = false

    private val _focusInputRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusInputRequests: SharedFlow<Unit> = _focusInputRequests.asSharedFlow()


    private val dragListener = object : FxListener {
        private var dragStartY = 0f
        private var dragStartTime = 0L

        override fun onClick(control: FxControl, view: View) {
            if (_showOptions.value) {
                dismissMenu()
            } else {
                bringFriendlyToFront(focusInput = false)
            }
        }

        override fun onLongClick(control: FxControl, view: View) {
            Log.i(TAG, "Mini indicator long-pressed (>=500ms) -> locking position and opening menu")
            _showOptions.value = true
            lockPosition()
            val parentView = view.parent as? View
            val releaseListener = View.OnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.setOnTouchListener(null)
                        parentView?.setOnTouchListener(null)
                        Log.i(TAG, "Touch released after long press -> unlocking position")
                        view.post {
                            unlockPosition()
                        }
                        return@OnTouchListener true
                    }
                }
                false
            }
            view.setOnTouchListener(releaseListener)
            parentView?.setOnTouchListener(releaseListener)
        }

        override fun onDragStart(control: FxControl) {
            if (_showOptions.value) {
                dismissMenu()
            }
            val pos = runCatching { control.position }.getOrNull()
            dragStartY = pos?.y ?: 0f
            dragStartTime = System.currentTimeMillis()
            ensureCloseZoneVisible()
        }

        override fun onDrag(control: FxControl, x: Float, y: Float) {
            if (dragStartY == 0f) {
                val pos = runCatching { control.position }.getOrNull()
                dragStartY = pos?.y ?: y
                dragStartTime = System.currentTimeMillis()
            }
        }

        override fun onDragEnd(control: FxControl, x: Float, y: Float) {
            val metrics = app.resources.displayMetrics
            val screenH = metrics.heightPixels.toFloat()
            val screenW = metrics.widthPixels.toFloat()
            // Prefer screen position from control; fall back to listener coords.
            val pos = runCatching { control.position }.getOrNull()
            val px = pos?.x ?: x
            val py = pos?.y ?: y
            val deltaY = py - dragStartY
            val elapsed = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1L)
            val velocityY = deltaY / elapsed.toFloat()

            hideCloseZone()

            val nearBottom = py > screenH * 0.70f
            val nearCenterX = px in (screenW * 0.2f)..(screenW * 0.8f)
            val isFlingToBottom = py > screenH * 0.60f && deltaY > 120f && velocityY > 0.35f
            val isBottomDrop = (nearBottom && nearCenterX) || py > screenH * 0.78f || isFlingToBottom

            if (isBottomDrop) {
                Log.i(TAG, "Mini indicator dismissed via downward swipe/drop at ($px,$py), deltaY=$deltaY, velocityY=$velocityY")
                dismiss()
            }
            dragStartY = 0f
            dragStartTime = 0L
        }
    }

    init {
        val isAppForeground = MutableStateFlow(
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> isAppForeground.value = true
                    Lifecycle.Event.ON_STOP -> isAppForeground.value = false
                    else -> Unit
                }
            }
        )

        appScope.launch(Dispatchers.Main.immediate) {
            combine(
                combine(
                    settingsStore.settingsFlow,
                    _sessionActive,
                    PhoneAutomationService.workStatus,
                    isAppForeground,
                    phoneCallController.snapshot,
                ) { settings, session, workStatus, foreground, call ->
                    MiniSyncInput(
                        enabled = !BuildConfig.IS_PLAY_BUILD &&
                            settings.displaySetting.enablePhoneAutomationMiniIndicator &&
                            settings.displaySetting.phoneAutomationWindowMode == PhoneAutomationWindowMode.OFF,
                        sessionActive = session,
                        workStatus = workStatus,
                        appForeground = foreground,
                        call = call,
                        agentCall = null,
                    )
                },
                agentCallManager.activeCall,
                PhoneAutomationService.activity,
                voicePhase,
            ) { input, agentCall, activity, voice ->
                val synced = input.copy(agentCall = agentCall)
                IndicatorState(
                    enabled = synced.enabled,
                    sessionActive = synced.sessionActive,
                    workStatus = synced.workStatus,
                    appForeground = synced.appForeground,
                    canDrawOverlays = Settings.canDrawOverlays(app),
                    live = isAutoShowLive(synced.call, synced.workStatus, activity, agentCall),
                    statusLine = formatMiniLiveLine(
                        app,
                        resolveMiniLive(
                            work = synced.workStatus,
                            activity = activity,
                            call = synced.call,
                            voice = voice,
                            sessionActive = synced.sessionActive,
                            agentCall = agentCall,
                        ),
                    ),
                )
            }
                .distinctUntilChanged()
                .collectLatest { state ->
                    if (!state.live) {
                        suppressAutoShow = false
                    }
                    val shouldAuto = state.enabled && state.live && !suppressAutoShow && !state.sessionActive
                    if (shouldAuto) {
                        _sessionActive.value = true
                        Log.i(TAG, "Mini session auto-shown (overlay=${Settings.canDrawOverlays(app)})")
                    }
                    syncIndicators(
                        if (shouldAuto) state.copy(sessionActive = true) else state,
                    )
                }
        }
    }

    /** Start a mini session (caller may minimize Friendly after this). Clears auto-show suppress. */
    fun activate() {
        if (BuildConfig.IS_PLAY_BUILD) {
            Log.i(TAG, "activate ignored — play build has no overlay automation")
            return
        }
        val display = settingsStore.settingsFlow.value.displaySetting
        if (
            !display.enablePhoneAutomationMiniIndicator ||
            display.phoneAutomationWindowMode != PhoneAutomationWindowMode.OFF
        ) {
            Log.i(TAG, "activate ignored — mini indicator disabled or window mode is not Off")
            return
        }
        suppressAutoShow = false
        _sessionActive.value = true
        Log.i(TAG, "Mini session activated (overlay=${Settings.canDrawOverlays(app)})")
    }

    /** End the mini session and tear down overlay + notification. */
    fun dismiss() {
        if (!_sessionActive.value) return
        if (isAutoShowLive(
                phoneCallController.snapshot.value,
                PhoneAutomationService.workStatus.value,
                PhoneAutomationService.activity.value,
                agentCallManager.activeCall.value,
            )
        ) {
            suppressAutoShow = true
            Log.i(TAG, "Mini session dismissed during live call/work; auto-show suppressed")
        }
        _sessionActive.value = false
        // Mini closed → background mic session is no longer authorized.
        VoiceCaptureForegroundService.release(app)
        Log.i(TAG, "Mini session dismissed")
    }

    fun isSessionActive(): Boolean = _sessionActive.value

    private data class IndicatorState(
        val enabled: Boolean,
        val sessionActive: Boolean,
        val workStatus: PhoneAutomationWorkStatus,
        val appForeground: Boolean,
        val canDrawOverlays: Boolean,
        val live: Boolean,
        val statusLine: String?,
    ) {
        /** Ongoing notification only while Friendly is backgrounded. */
        val shouldShowStatus: Boolean
            get() = enabled && sessionActive && !appForeground

        /**
         * Floating cat whenever the mini session is active and overlay permission
         * is granted — including briefly while still foreground so activate() +
         * moveTaskToBack cannot race ProcessLifecycle ON_STOP and leave the indicator missing.
         */
        val shouldShowOverlay: Boolean
            get() = enabled && sessionActive && canDrawOverlays
    }

    private fun syncIndicators(state: IndicatorState) {
        Log.d(
            TAG,
            "sync enabled=${state.enabled} session=${state.sessionActive} " +
                "foreground=${state.appForeground} overlay=${state.canDrawOverlays} " +
                "status=${state.workStatus} live=${state.live} line=${state.statusLine} " +
                "showStatus=${state.shouldShowStatus} showOverlay=${state.shouldShowOverlay}",
        )

        // Settings toggled off while session active → end session.
        if (state.sessionActive && !state.enabled) {
            _sessionActive.value = false
            VoiceCaptureForegroundService.release(app)
            hideOverlay()
            hideCloseZone()
            hideNotification()
            return
        }

        if (!state.enabled || !state.sessionActive) {
            hideOverlay()
            hideCloseZone()
            hideNotification()
            return
        }

        if (state.shouldShowStatus) {
            showOrUpdateNotification(state.statusLine)
        } else {
            hideNotification()
        }

        if (state.shouldShowOverlay) {
            showOverlay()
        } else {
            hideOverlay()
            hideCloseZone()
            if (!state.canDrawOverlays) {
                Log.i(TAG, "Overlay skipped (no SYSTEM_ALERT_WINDOW); notification backup when backgrounded")
            }
        }
    }

    private fun hideNotification() {
        if (!notificationVisible) return
        app.cancelNotification(NOTIFICATION_ID)
        notificationVisible = false
    }

    private fun showOrUpdateNotification(statusLine: String?) {
        val statusText = statusLine?.takeIf { it.isNotBlank() }
            ?: app.getString(R.string.phone_mini_indicator_idle)
        val posted = app.sendNotification(
            channelId = PHONE_AUTOMATION_NOTIFICATION_CHANNEL_ID,
            notificationId = NOTIFICATION_ID,
        ) {
            title = app.getString(R.string.phone_automation)
            content = app.getString(R.string.phone_automation_notification_content, statusText)
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_SERVICE
            contentIntent = openAppPendingIntent(focusInput = false)
        }
        notificationVisible = posted
        if (!posted) {
            Log.w(TAG, "Failed to post phone automation notification (permission denied?)")
        }
    }

    private fun openAppPendingIntent(focusInput: Boolean): PendingIntent {
        return PendingIntent.getActivity(
            app,
            if (focusInput) NOTIFICATION_ID + 1 else NOTIFICATION_ID,
            buildOpenIntent(focusInput),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun buildOpenIntent(focusInput: Boolean): Intent {
        val conversationId = PhoneAutomationService.lastConversationId
            ?: app.readStringPreference("lastConversationId")
        return Intent(app, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
            if (focusInput) {
                putExtra(EXTRA_FOCUS_INPUT, true)
            }
        }
    }

    private fun hideOverlay() {
        _showOptions.value = false
        control?.let {
            runCatching { it.removeListener(dragListener) }
            runCatching { it.hide() }
            runCatching { it.cancel() }
        }
        control = null
        if (FloatingX.isInstalled(FLOATING_TAG)) {
            runCatching { FloatingX.uninstall(FLOATING_TAG) }
        }
    }

    private fun hideCloseZone() {
        closeZoneControl?.let {
            runCatching { it.hide() }
            runCatching { it.cancel() }
        }
        closeZoneControl = null
        if (FloatingX.isInstalled(CLOSE_ZONE_TAG)) {
            runCatching { FloatingX.uninstall(CLOSE_ZONE_TAG) }
        }
    }

    private fun ensureCloseZoneVisible() {
        if (!Settings.canDrawOverlays(app)) return
        val existing = closeZoneControl ?: FloatingX.controlOrNull(CLOSE_ZONE_TAG)
        if (existing != null) {
            closeZoneControl = existing
            if (!existing.isShowing) existing.show()
            return
        }
        try {
            val installed = FloatingX.install(CLOSE_ZONE_TAG) {
                anchor(FxGravity.BOTTOM_CENTER, dx = 0f, dy = -48f)
                animation(FxAnimations.fade())
                enableLog("$TAG-close")
                gesture(FxGesture.DisplayOnly)
                systemHost(app) {
                    theme(R.style.Theme_Rikkahub)
                    permission(FxPermissionStrategy.skip())
                }
                compose {
                    RikkahubTheme {
                        CloseZonePill()
                    }
                }
            }
            closeZoneControl = installed
            installed.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show close zone", e)
            closeZoneControl = null
        }
    }

    private fun showOverlay() {
        val existing = control ?: FloatingX.controlOrNull(FLOATING_TAG)
        if (existing != null) {
            control = existing
            runCatching { existing.removeListener(dragListener) }
            existing.addListener(dragListener)
            _showOptions.value = false
            runCatching {
                existing.update {
                    gesture {
                        click = true
                        longPress = true
                        drag = FxDrag.IMMEDIATE
                        longPressTimeout = MINI_BAR_LONG_PRESS_TIMEOUT_MS
                    }
                }
            }
            if (!existing.isShowing) {
                runCatching { existing.show() }
                    .onFailure { Log.w(TAG, "Existing overlay show failed; reinstalling", it) }
                    .onSuccess { return }
                // Fall through to reinstall if show failed.
                hideOverlay()
            } else {
                return
            }
        }

        try {
            if (FloatingX.isInstalled(FLOATING_TAG)) {
                runCatching { FloatingX.uninstall(FLOATING_TAG) }
            }
            _showOptions.value = false
            val installed = FloatingX.install(FLOATING_TAG) {
                anchor(FxGravity.TOP_END, dx = 16f, dy = 120f)
                animation(FxAnimations.fade())
                enableLog(TAG)
                gesture {
                    click = true
                    longPress = true
                    drag = FxDrag.IMMEDIATE
                    longPressTimeout = MINI_BAR_LONG_PRESS_TIMEOUT_MS
                }
                systemHost(app) {
                    theme(R.style.Theme_Rikkahub)
                    permission(FxPermissionStrategy.skip())
                }
                compose {
                    RikkahubTheme {
                        val work by PhoneAutomationService.workStatus.collectAsState()
                        val activity by PhoneAutomationService.activity.collectAsState()
                        val call by phoneCallController.snapshot.collectAsState()
                        val agentCall by agentCallManager.activeCall.collectAsState()
                        val voice by voicePhase.collectAsState()
                        val session by sessionActive.collectAsState()
                        val showOpt by showOptions.collectAsState()
                        val screenH = app.resources.displayMetrics.heightPixels.toFloat()
                        val menuAbove = (control?.position?.y ?: 0f) > screenH * 0.65f
                        MiniIndicatorBubble(
                            status = miniIndicatorStatus(
                                work = work,
                                activity = activity,
                                call = call,
                                voice = voice,
                                sessionActive = session,
                                agentCall = agentCall,
                            ),
                            showOptions = showOpt,
                            menuAbove = menuAbove,
                            onDismissMenu = { dismissMenu() },
                            onBackToApp = { bringFriendlyToFront(focusInput = false) },
                            onNewPrompt = {
                                dismissMenu()
                                newPrompt()
                            },
                            onStartVoice = {
                                dismissMenu()
                                startNewVoice()
                            },
                        )
                    }
                }
            }
            control = installed
            installed.addListener(dragListener)
            installed.show()
            Log.i(TAG, "Phone automation overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show phone automation mini indicator overlay", e)
            control = null
        }
    }

    /**
     * Same path as a mini-bar tap: reorder the existing Friendly task and open
     * [PhoneAutomationService.lastConversationId] (or the saved last chat).
     * Does not call moveTaskToBack and does not ask for overlay permission.
     * If that conversation is already the resumed top screen, this does not
     * relaunch, so the activity is not restarted and the transcript stays put.
     * A dismissed mini bar (suppressAutoShow) does not block this.
     */
    fun bringFriendlyToFront(focusInput: Boolean) {
        if (focusInput) {
            _focusInputRequests.tryEmit(Unit)
        }
        val conversationId = PhoneAutomationService.lastConversationId
            ?: app.readStringPreference("lastConversationId")
        if (
            !conversationId.isNullOrBlank() &&
            RouteActivity.resumedTopChatId == conversationId
        ) {
            Log.i(TAG, "Already showing conversation $conversationId; not relaunching")
            return
        }
        runCatching { app.startActivity(buildOpenIntent(focusInput)) }
            .onFailure { Log.e(TAG, "Unable to bring Friendly to front", it) }
    }

    fun newPrompt() {
        val intent = Intent(app, RouteActivity::class.java).apply {
            action = RouteActivity.ACTION_NEW_PROMPT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FOCUS_INPUT, true)
        }
        _focusInputRequests.tryEmit(Unit)
        runCatching { app.startActivity(intent) }
            .onFailure { Log.e(TAG, "Unable to start new prompt", it) }
    }

    fun startNewVoice() {
        val intent = Intent(app, RouteActivity::class.java).apply {
            action = RouteActivity.ACTION_NEW_VOICE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { app.startActivity(intent) }
            .onFailure { Log.e(TAG, "Unable to start new voice", it) }
    }
}

@Composable
private fun CloseZonePill() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.phone_mini_indicator_close),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private data class MiniSyncInput(
    val enabled: Boolean,
    val sessionActive: Boolean,
    val workStatus: PhoneAutomationWorkStatus,
    val appForeground: Boolean,
    val call: CellularCallSnapshot,
    val agentCall: app.friendly.assistant.service.phone.agentcall.AgentCallSnapshot?,
)

private enum class MiniLiveKind {
    OnCall,
    Ringing,
    Calling,
    Failed,
    Working,
    Listening,
    Speaking,
    Idle,
}

private data class MiniLiveResolution(
    val kind: MiniLiveKind,
    val labelRes: Int? = null,
    val numberArg: String? = null,
)

private data class MiniIndicatorStatus(
    val kind: MiniLiveKind,
    val label: String?,
    val ledColor: Color,
    val pulse: Boolean,
)

private fun isAutoShowLive(
    call: CellularCallSnapshot,
    work: PhoneAutomationWorkStatus,
    activity: PhoneAutomationActivity,
    agentCall: app.friendly.assistant.service.phone.agentcall.AgentCallSnapshot? = null,
): Boolean {
    if (agentCall != null &&
        agentCall.phase != app.friendly.assistant.service.phone.agentcall.AgentCallPhase.Ended &&
        agentCall.phase != app.friendly.assistant.service.phone.agentcall.AgentCallPhase.Failed
    ) {
        return true
    }
    if (call.status == CellularCallStatus.Ringing || call.status == CellularCallStatus.Offhook) return true
    if (!call.outboundNumber.isNullOrBlank()) return true
    if (activity.toolRunning || activity.holdingForGeneration) return true
    return work == PhoneAutomationWorkStatus.Running
}

private fun knownCallNumber(call: CellularCallSnapshot): String? =
    call.number?.takeIf { it.isNotBlank() } ?: call.outboundNumber?.takeIf { it.isNotBlank() }

private fun labeledNumber(number: String?, plainRes: Int, withNumberRes: Int): Pair<Int, String?> =
    if (number.isNullOrBlank()) plainRes to null else withNumberRes to number

private fun stepStatusRes(step: PhoneAutomationStep): Int = when (step) {
    PhoneAutomationStep.LaunchApp -> R.string.phone_mini_status_launching
    PhoneAutomationStep.Screenshot -> R.string.phone_mini_status_screenshot
    PhoneAutomationStep.Inspect -> R.string.phone_mini_status_inspect
    PhoneAutomationStep.Click -> R.string.phone_mini_status_tapping
    PhoneAutomationStep.Swipe -> R.string.phone_mini_status_swiping
    PhoneAutomationStep.Type -> R.string.phone_mini_status_typing
    PhoneAutomationStep.PressKey -> R.string.phone_mini_status_press_key
    PhoneAutomationStep.PlaceCall -> R.string.phone_mini_status_calling
    PhoneAutomationStep.EndCall -> R.string.phone_mini_status_ending_call
    PhoneAutomationStep.MuteCall -> R.string.phone_mini_status_checking_call
    PhoneAutomationStep.ReadCall -> R.string.phone_mini_status_checking_call
    PhoneAutomationStep.AssertVisible -> R.string.phone_mini_status_inspect
    PhoneAutomationStep.ScrollUntilVisible -> R.string.phone_mini_status_swiping
    PhoneAutomationStep.RunFlow,
    PhoneAutomationStep.ManageFlows,
    PhoneAutomationStep.None,
    PhoneAutomationStep.Other -> R.string.phone_mini_status_working
}

/**
 * First match wins. Cellular or agent call lines beat a phone-tool step. Voice is ignored when Off,
 * and voice Error never produces a line. Listening / Speaking are only returned when
 * a mini session is already active.
 */
private fun resolveMiniLive(
    work: PhoneAutomationWorkStatus,
    activity: PhoneAutomationActivity,
    call: CellularCallSnapshot,
    voice: VoicePhase,
    sessionActive: Boolean,
    agentCall: app.friendly.assistant.service.phone.agentcall.AgentCallSnapshot? = null,
): MiniLiveResolution {
    if (agentCall != null &&
        agentCall.phase != app.friendly.assistant.service.phone.agentcall.AgentCallPhase.Ended &&
        agentCall.phase != app.friendly.assistant.service.phone.agentcall.AgentCallPhase.Failed
    ) {
        val (res, arg) = labeledNumber(
            agentCall.toNumber,
            R.string.phone_mini_status_on_call,
            R.string.phone_mini_status_on_call_number,
        )
        return MiniLiveResolution(MiniLiveKind.OnCall, res, arg)
    }
    val number = knownCallNumber(call)
    val outbound = call.outboundNumber?.takeIf { it.isNotBlank() }
    return when {
        call.status == CellularCallStatus.Offhook -> {
            val (res, arg) = labeledNumber(
                number,
                R.string.phone_mini_status_on_call,
                R.string.phone_mini_status_on_call_number,
            )
            MiniLiveResolution(MiniLiveKind.OnCall, res, arg)
        }
        call.status == CellularCallStatus.Ringing -> {
            val (res, arg) = labeledNumber(
                number,
                R.string.phone_mini_status_ringing,
                R.string.phone_mini_status_ringing_number,
            )
            MiniLiveResolution(MiniLiveKind.Ringing, res, arg)
        }
        outbound != null &&
            call.status != CellularCallStatus.Ringing &&
            call.status != CellularCallStatus.Offhook -> {
            val (res, arg) = labeledNumber(
                outbound,
                R.string.phone_mini_status_calling,
                R.string.phone_mini_status_calling_number,
            )
            MiniLiveResolution(MiniLiveKind.Calling, res, arg)
        }
        activity.toolRunning -> MiniLiveResolution(
            MiniLiveKind.Working,
            stepStatusRes(activity.step),
        )
        activity.holdingForGeneration -> MiniLiveResolution(
            MiniLiveKind.Working,
            R.string.phone_mini_status_working,
        )
        activity.failed || work == PhoneAutomationWorkStatus.Error -> MiniLiveResolution(
            MiniLiveKind.Failed,
            R.string.phone_mini_status_failed,
        )
        work == PhoneAutomationWorkStatus.Running -> MiniLiveResolution(
            MiniLiveKind.Working,
            R.string.phone_mini_status_working,
        )
        sessionActive && voice != VoicePhase.Off && (
            voice == VoicePhase.Listening ||
                voice == VoicePhase.Transcribing ||
                voice == VoicePhase.Connecting
            ) -> MiniLiveResolution(
            MiniLiveKind.Listening,
            R.string.phone_mini_status_listening,
        )
        sessionActive && voice == VoicePhase.Speaking -> MiniLiveResolution(
            MiniLiveKind.Speaking,
            R.string.phone_mini_status_speaking,
        )
        else -> MiniLiveResolution(MiniLiveKind.Idle)
    }
}

private fun formatMiniLiveLine(app: Application, resolved: MiniLiveResolution): String? {
    val res = resolved.labelRes ?: return null
    val arg = resolved.numberArg
    return if (arg != null) app.getString(res, arg) else app.getString(res)
}

@Composable
private fun miniIndicatorStatus(
    work: PhoneAutomationWorkStatus,
    activity: PhoneAutomationActivity,
    call: CellularCallSnapshot,
    voice: VoicePhase,
    sessionActive: Boolean,
    agentCall: app.friendly.assistant.service.phone.agentcall.AgentCallSnapshot? = null,
): MiniIndicatorStatus {
    val resolved = resolveMiniLive(work, activity, call, voice, sessionActive, agentCall)
    val label = resolved.labelRes?.let { res ->
        val arg = resolved.numberArg
        if (arg != null) stringResource(res, arg) else stringResource(res)
    }
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val green = Color(0xFF34C759)
    val amber = Color(0xFFFF9F0A)
    val (ledColor, pulse) = when (resolved.kind) {
        MiniLiveKind.OnCall, MiniLiveKind.Idle -> green to false
        MiniLiveKind.Ringing, MiniLiveKind.Calling -> amber to true
        MiniLiveKind.Failed -> error to false
        MiniLiveKind.Working, MiniLiveKind.Listening, MiniLiveKind.Speaking -> primary to true
    }
    return MiniIndicatorStatus(
        kind = resolved.kind,
        label = label,
        ledColor = ledColor,
        pulse = pulse,
    )
}

@Composable
private fun MiniIndicatorBubble(
    status: MiniIndicatorStatus,
    showOptions: Boolean,
    menuAbove: Boolean,
    onDismissMenu: () -> Unit,
    onBackToApp: () -> Unit,
    onNewPrompt: () -> Unit,
    onStartVoice: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(showOptions) {
        if (showOptions) {
            delay(8000)
            onDismissMenu()
        }
    }

    val spoken = status.label ?: stringResource(R.string.phone_mini_indicator_idle)
    val a11y = stringResource(R.string.phone_mini_indicator_back_to_app) + " · " + spoken
    val ledColor = status.ledColor
    val dark = LocalDarkMode.current
    val discBrush = if (dark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color(0xFF2C2C2E).copy(alpha = 0.82f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.96f),
                Color(0xFFE8ECF2).copy(alpha = 0.90f),
            )
        )
    }
    val rimBrush = if (dark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.06f),
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.25f),
                Color(0xFFB8C0CC).copy(alpha = 0.35f),
            )
        )
    }
    val ledRing = if (dark) Color(0xFF1C1C1E) else Color.White
    val pulse = status.pulse
    val expanded = status.kind != MiniLiveKind.Idle && !status.label.isNullOrBlank()
    val shape = if (expanded) RoundedCornerShape(27.dp) else CircleShape
    val transition = rememberInfiniteTransition(label = "mini_led_pulse")
    val animatedAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mini_led_alpha",
    )
    val animatedScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mini_led_glow",
    )
    val pulseAlpha = if (pulse) animatedAlpha else 1f
    val pulseScale = if (pulse) animatedScale else 1f

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (menuAbove) {
            AnimatedVisibility(
                visible = showOptions,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                MiniIndicatorOptionsMenu(
                    onNewPrompt = onNewPrompt,
                    onStartVoice = onStartVoice,
                    dark = dark,
                    rimBrush = rimBrush,
                )
            }
        }

        // 54dp glass disc, or a 54dp-tall pill (max ~240dp) with one status line.
        Box(
            modifier = Modifier
                .padding(8.dp)
                .height(54.dp)
                .then(
                    if (expanded) Modifier.widthIn(max = 240.dp).wrapContentWidth() else Modifier.width(54.dp),
                )
                .semantics { contentDescription = a11y }
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = if (dark) 0.45f else 0.18f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.22f),
                )
                .clip(shape)
                .background(discBrush, shape)
                .border(width = 1.dp, brush = rimBrush, shape = shape),
        ) {
            // Soft inner highlight (glass sheen)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.14f else 0.35f),
                                Color.Transparent,
                                Color.Transparent,
                            )
                        )
                    ),
            )
            Row(
                modifier = Modifier.height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_neon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .padding(6.dp)
                        .clip(CircleShape),
                )
                if (expanded) {
                    Text(
                        text = status.label.orEmpty(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (dark) Color.White else Color(0xFF1C1C1E),
                        modifier = Modifier
                            .padding(end = 28.dp)
                            .widthIn(max = 158.dp),
                    )
                }
            }
            // LED: bottom-end of the disc when collapsed, trailing end of the pill when expanded.
            Box(
                modifier = Modifier
                    .align(if (expanded) Alignment.CenterEnd else Alignment.BottomEnd)
                    .then(if (expanded) Modifier.padding(end = 8.dp) else Modifier.padding(3.dp))
                    .size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (pulse) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .scale(pulseScale)
                            .background(ledColor.copy(alpha = 0.28f * pulseAlpha), CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .shadow(
                            elevation = 3.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = ledColor.copy(alpha = 0.35f),
                            spotColor = ledColor.copy(alpha = 0.45f),
                        )
                        .background(ledRing, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ledColor.copy(alpha = pulseAlpha), CircleShape),
                    )
                }
            }
        }

        if (!menuAbove) {
            AnimatedVisibility(
                visible = showOptions,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                MiniIndicatorOptionsMenu(
                    onNewPrompt = onNewPrompt,
                    onStartVoice = onStartVoice,
                    dark = dark,
                    rimBrush = rimBrush,
                )
            }
        }
    }
}

@Composable
private fun MiniIndicatorOptionsMenu(
    onNewPrompt: () -> Unit,
    onStartVoice: () -> Unit,
    dark: Boolean,
    rimBrush: Brush,
) {
    val menuBg = if (dark) {
        Color(0xFF1E1E20).copy(alpha = 0.94f)
    } else {
        Color(0xFFF2F4F7).copy(alpha = 0.96f)
    }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .widthIn(min = 180.dp, max = 220.dp)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.6f else 0.22f),
            )
            .border(width = 1.dp, brush = rimBrush, shape = shape),
        shape = shape,
        color = menuBg,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            // Option 1: New prompt
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewPrompt)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.shortcut_new_prompt),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) Color.White else Color(0xFF1C1C1E),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f),
            )

            // Option 2: Start new voice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStartVoice)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Voice,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.phone_mini_start_new_voice),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) Color.White else Color(0xFF1C1C1E),
                )
            }
        }
    }
}
