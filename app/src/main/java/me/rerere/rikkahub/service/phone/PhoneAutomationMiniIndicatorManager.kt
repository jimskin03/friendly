package me.rerere.rikkahub.service.phone

import android.app.Application
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.petterp.floatingx.app.AppHost
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.theme.RikkahubTheme

private const val TAG = "PhoneAutoMiniIndicator"
private const val FLOATING_TAG = "phone_automation_mini_indicator"

/**
 * Shows a small system-overlay status bubble when Phone Automation is active
 * and Friendly is in the background. Respects the Preferences toggle and
 * [Settings.canDrawOverlays].
 */
class PhoneAutomationMiniIndicatorManager(
    private val app: Application,
    appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    private var control: FxControl? = null

    init {
        val isAppForeground = kotlinx.coroutines.flow.MutableStateFlow(
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
                isAppForeground,
            ) { settings, connected, foreground ->
                IndicatorState(
                    enabled = settings.displaySetting.enablePhoneAutomationMiniIndicator,
                    connected = connected,
                    appForeground = foreground,
                    canDrawOverlays = Settings.canDrawOverlays(app),
                )
            }
                .distinctUntilChanged()
                .collectLatest { state ->
                    syncOverlay(state)
                }
        }
    }

    private data class IndicatorState(
        val enabled: Boolean,
        val connected: Boolean,
        val appForeground: Boolean,
        val canDrawOverlays: Boolean,
    ) {
        val shouldShow: Boolean
            get() = enabled && connected && !appForeground && canDrawOverlays
    }

    private fun syncOverlay(state: IndicatorState) {
        if (!state.shouldShow) {
            hideOverlay()
            return
        }
        showOverlay()
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
            // Status text updates via workStatus StateFlow collected inside compose content.
            if (!existing.isShowing) {
                existing.show()
            }
            return
        }

        try {
            val installed = FloatingX.install(FLOATING_TAG) {
                anchor(FxGravity.TOP_END, dx = 16f, dy = 120f)
                animation(FxAnimations.fade())
                systemHost(app) {
                    theme(R.style.Theme_Rikkahub)
                    // Permission must already be granted (we only show when canDrawOverlays).
                    // Do not auto-prompt from background — Android 10+ blocks it.
                    permission(FxPermissionStrategy.skip())
                    fallback(AppHost.builder(app).build())
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show phone automation mini indicator", e)
            control = null
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

