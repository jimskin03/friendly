package me.rerere.rikkahub.service.phone

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.FxListener
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.layout.FxGravity
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
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.PHONE_AUTOMATION_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.rikkahub.utils.sendNotification

private const val TAG = "PhoneAutoMiniIndicator"
private const val FLOATING_TAG = "phone_automation_mini_indicator"
private const val CLOSE_ZONE_TAG = "phone_automation_mini_close_zone"

/**
 * Phone Automation mini indicator — compact floating rabbit face.
 *
 * Entry: explicit activate() from Phone sheet "Minimize with mini indicator"
 * or long-press on the phone icon (never the primary phone tap). Caller
 * minimizes Friendly via moveTaskToBack. While the session is active:
 *  1. Ongoing notification while Friendly is backgrounded (OEM-safe backup; tap reopens app)
 *  2. System-overlay rabbit face with Idle/Running/Error LED whenever SYSTEM_ALERT_WINDOW
 *     is granted (shown immediately on activate so it cannot race ProcessLifecycle ON_STOP /
 *     moveTaskToBack)
 *
 * Single tap → bring Friendly back (last chat / last place).
 * Drag toward the bottom → X close zone; drop dismisses the mini session.
 */
class PhoneAutomationMiniIndicatorManager(
    private val app: Application,
    appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    companion object {
        const val NOTIFICATION_ID = 2003
        const val EXTRA_FOCUS_INPUT = "focusInput"
        const val EXTRA_CONVERSATION_ID = "conversationId"
    }

    private var control: FxControl? = null
    private var closeZoneControl: FxControl? = null
    private var notificationVisible = false

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _focusInputRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusInputRequests: SharedFlow<Unit> = _focusInputRequests.asSharedFlow()


    private val dragListener = object : FxListener {
        override fun onDragStart(control: FxControl) {
            ensureCloseZoneVisible()
        }

        override fun onDrag(control: FxControl, x: Float, y: Float) {
            // Close zone stays visible for the whole drag.
        }

        override fun onDragEnd(control: FxControl, x: Float, y: Float) {
            val metrics = app.resources.displayMetrics
            val screenH = metrics.heightPixels.toFloat()
            val screenW = metrics.widthPixels.toFloat()
            // Prefer screen position from control; fall back to listener coords.
            val pos = runCatching { control.position }.getOrNull()
            val px = pos?.x ?: x
            val py = pos?.y ?: y
            val nearBottom = py > screenH * 0.72f
            val nearCenterX = px in (screenW * 0.2f)..(screenW * 0.8f)
            hideCloseZone()
            if (nearBottom && nearCenterX) {
                Log.i(TAG, "Mini indicator dismissed via close zone drop at ($px,$py)")
                dismiss()
            }
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
                settingsStore.settingsFlow,
                _sessionActive,
                PhoneAutomationService.workStatus,
                isAppForeground,
            ) { settings, session, workStatus, foreground ->
                IndicatorState(
                    enabled = settings.displaySetting.enablePhoneAutomationMiniIndicator,
                    sessionActive = session,
                    workStatus = workStatus,
                    appForeground = foreground,
                    canDrawOverlays = Settings.canDrawOverlays(app),
                )
            }
                .distinctUntilChanged()
                .collectLatest { state ->
                    syncIndicators(state)
                }
        }
    }

    /** Start a mini session (caller should minimize Friendly after this). */
    fun activate() {
        if (!settingsStore.settingsFlow.value.displaySetting.enablePhoneAutomationMiniIndicator) {
            Log.i(TAG, "activate ignored — mini indicator disabled in Preferences")
            return
        }
        _sessionActive.value = true
        Log.i(TAG, "Mini session activated (overlay=${Settings.canDrawOverlays(app)})")
    }

    /** End the mini session and tear down overlay + notification. */
    fun dismiss() {
        if (!_sessionActive.value) return
        _sessionActive.value = false
        Log.i(TAG, "Mini session dismissed")
    }

    fun isSessionActive(): Boolean = _sessionActive.value

    private data class IndicatorState(
        val enabled: Boolean,
        val sessionActive: Boolean,
        val workStatus: PhoneAutomationWorkStatus,
        val appForeground: Boolean,
        val canDrawOverlays: Boolean,
    ) {
        /** Ongoing notification only while Friendly is backgrounded. */
        val shouldShowStatus: Boolean
            get() = enabled && sessionActive && !appForeground

        /**
         * Floating rabbit whenever the mini session is active and overlay permission
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
                "status=${state.workStatus} showStatus=${state.shouldShowStatus} " +
                "showOverlay=${state.shouldShowOverlay}",
        )

        // Settings toggled off while session active → end session.
        if (state.sessionActive && !state.enabled) {
            _sessionActive.value = false
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
            showOrUpdateNotification(state.workStatus)
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

    private fun showOrUpdateNotification(status: PhoneAutomationWorkStatus) {
        val statusText = when (status) {
            PhoneAutomationWorkStatus.Running ->
                app.getString(R.string.phone_mini_indicator_running)
            PhoneAutomationWorkStatus.Error ->
                app.getString(R.string.phone_mini_indicator_error)
            PhoneAutomationWorkStatus.Idle ->
                app.getString(R.string.phone_mini_indicator_idle)
        }
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
            val installed = FloatingX.install(FLOATING_TAG) {
                anchor(FxGravity.TOP_END, dx = 16f, dy = 120f)
                animation(FxAnimations.fade())
                enableLog(TAG)
                systemHost(app) {
                    theme(R.style.Theme_Rikkahub)
                    permission(FxPermissionStrategy.skip())
                }
                compose {
                    RikkahubTheme {
                        val status by PhoneAutomationService.workStatus.collectAsState()
                        MiniIndicatorBubble(
                            status = status,
                            onBackToApp = { bringFriendlyToFront(focusInput = false) },
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

    private fun bringFriendlyToFront(focusInput: Boolean) {
        if (focusInput) {
            _focusInputRequests.tryEmit(Unit)
        }
        runCatching { app.startActivity(buildOpenIntent(focusInput)) }
            .onFailure { Log.e(TAG, "Unable to bring Friendly to front", it) }
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

@Composable
private fun MiniIndicatorBubble(
    status: PhoneAutomationWorkStatus,
    onBackToApp: () -> Unit,
) {
    val statusLabel = when (status) {
        PhoneAutomationWorkStatus.Running ->
            stringResource(R.string.phone_mini_indicator_running)
        PhoneAutomationWorkStatus.Error ->
            stringResource(R.string.phone_mini_indicator_error)
        PhoneAutomationWorkStatus.Idle ->
            stringResource(R.string.phone_mini_indicator_idle)
    }
    val ledColor = when (status) {
        PhoneAutomationWorkStatus.Running -> MaterialTheme.colorScheme.primary
        PhoneAutomationWorkStatus.Error -> MaterialTheme.colorScheme.error
        PhoneAutomationWorkStatus.Idle -> Color(0xFF4CAF50)
    }
    val a11y = stringResource(R.string.phone_mini_indicator_back_to_app) + " · " + statusLabel

    // Compact app-logo rabbit (~square). Sketch size vs old Idle pill ≈ 48–56dp.
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(52.dp)
            .semantics { contentDescription = a11y }
            .clickable(onClick = onBackToApp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
            )
        }
        // Status LED badge (Idle green / Running primary / Error)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(1.dp)
                .size(14.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(ledColor, CircleShape),
            )
        }
    }
}
