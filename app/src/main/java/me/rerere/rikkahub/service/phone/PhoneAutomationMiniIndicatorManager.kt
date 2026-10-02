package me.rerere.rikkahub.service.phone

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.SmartPhone01
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

/**
 * Surfaces Phone Automation status while Friendly is backgrounded:
 * 1. Ongoing notification (always; OEM-safe fallback, tap to reopen app)
 * 2. System-overlay bubble when [Settings.canDrawOverlays] is granted
 *
 * Respects the Preferences toggle. Overlay is best-effort — many OEMs restrict
 * overlays, so the notification is the reliable path.
 */
class PhoneAutomationMiniIndicatorManager(
    private val app: Application,
    appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    companion object {
        const val NOTIFICATION_ID = 2003
    }

    private var control: FxControl? = null
    private var notificationVisible = false

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
                PhoneAutomationService.isConnected,
                PhoneAutomationService.workStatus,
                isAppForeground,
            ) { settings, connected, workStatus, foreground ->
                IndicatorState(
                    enabled = settings.displaySetting.enablePhoneAutomationMiniIndicator,
                    connected = connected,
                    workStatus = workStatus,
                    appForeground = foreground,
                    // Sampled on every combine emission (incl. lifecycle / status changes).
                    canDrawOverlays = Settings.canDrawOverlays(app),
                )
            }
                .distinctUntilChanged()
                .collectLatest { state ->
                    syncIndicators(state)
                }
        }
    }

    private data class IndicatorState(
        val enabled: Boolean,
        val connected: Boolean,
        val workStatus: PhoneAutomationWorkStatus,
        val appForeground: Boolean,
        val canDrawOverlays: Boolean,
    ) {
        /** Show status chrome whenever automation is connected and app is backgrounded. */
        val shouldShowStatus: Boolean
            get() = enabled && connected && !appForeground

        val shouldShowOverlay: Boolean
            get() = shouldShowStatus && canDrawOverlays
    }

    private fun syncIndicators(state: IndicatorState) {
        Log.d(
            TAG,
            "sync enabled=${state.enabled} connected=${state.connected} " +
                "foreground=${state.appForeground} overlay=${state.canDrawOverlays} " +
                "status=${state.workStatus} showStatus=${state.shouldShowStatus} " +
                "showOverlay=${state.shouldShowOverlay}",
        )

        if (!state.shouldShowStatus) {
            hideOverlay()
            hideNotification()
            return
        }

        // Notification first — works without SYSTEM_ALERT_WINDOW and survives OEM overlay kills.
        showOrUpdateNotification(state.workStatus)

        if (state.shouldShowOverlay) {
            showOverlay()
        } else {
            hideOverlay()
            if (!state.canDrawOverlays) {
                Log.i(TAG, "Overlay skipped (no SYSTEM_ALERT_WINDOW); notification is active")
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
            contentIntent = openAppPendingIntent()
        }
        notificationVisible = posted
        if (!posted) {
            Log.w(TAG, "Failed to post phone automation notification (permission denied?)")
        }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val conversationId = PhoneAutomationService.lastConversationId
            ?: app.readStringPreference("lastConversationId")
        val intent = Intent(app, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra("conversationId", conversationId)
            }
        }
        return PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun hideOverlay() {
        control?.let {
            runCatching { it.hide() }
            runCatching { it.cancel() }
        }
        control = null
        if (FloatingX.isInstalled(FLOATING_TAG)) {
            runCatching { FloatingX.uninstall(FLOATING_TAG) }
        }
    }

    private fun showOverlay() {
        val existing = control ?: FloatingX.controlOrNull(FLOATING_TAG)
        if (existing != null) {
            control = existing
            if (!existing.isShowing) {
                existing.show()
            }
            return
        }

        try {
            // systemHost only — AppHost fallback is useless while backgrounded.
            val installed = FloatingX.install(FLOATING_TAG) {
                anchor(FxGravity.TOP_END, dx = 16f, dy = 120f)
                animation(FxAnimations.fade())
                enableLog(TAG)
                systemHost(app) {
                    theme(R.style.Theme_Rikkahub)
                    // Permission already verified via canDrawOverlays; do not auto-prompt
                    // from background (Android 10+ blocks background Activity starts).
                    permission(FxPermissionStrategy.skip())
                }
                compose {
                    RikkahubTheme {
                        val status by PhoneAutomationService.workStatus.collectAsState()
                        MiniIndicatorBubble(
                            status = status,
                            onClick = { bringFriendlyToFront() },
                        )
                    }
                }
            }
            control = installed
            installed.show()
            Log.i(TAG, "Phone automation overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show phone automation mini indicator overlay", e)
            control = null
            // Notification already posted in syncIndicators — keep that as the fallback.
        }
    }

    private fun bringFriendlyToFront() {
        val conversationId = PhoneAutomationService.lastConversationId
            ?: app.readStringPreference("lastConversationId")
        val intent = Intent(app, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra("conversationId", conversationId)
            }
        }
        runCatching { app.startActivity(intent) }
            .onFailure { Log.e(TAG, "Unable to bring Friendly to front", it) }
    }
}

@Composable
private fun MiniIndicatorBubble(
    status: PhoneAutomationWorkStatus,
    onClick: () -> Unit,
) {
    val (label, dotColor) = when (status) {
        PhoneAutomationWorkStatus.Running ->
            stringResource(R.string.phone_mini_indicator_running) to MaterialTheme.colorScheme.primary
        PhoneAutomationWorkStatus.Error ->
            stringResource(R.string.phone_mini_indicator_error) to MaterialTheme.colorScheme.error
        PhoneAutomationWorkStatus.Idle ->
            stringResource(R.string.phone_mini_indicator_idle) to Color(0xFF4CAF50)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = HugeIcons.SmartPhone01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
