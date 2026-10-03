package me.rerere.rikkahub.ui.pages.chat.phone

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.phone.PhoneAutomationService
import me.rerere.rikkahub.ui.context.LocalToaster
import java.io.ByteArrayOutputStream
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.phone.PhoneCallController
import me.rerere.rikkahub.ui.components.ui.permission.PermissionAnswerPhoneCalls
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCallPhone
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionReadPhoneState
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhoneAutomationSheet(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismissRequest: () -> Unit,
    onAppendPrompt: (String) -> Unit,
    onAttachScreenshot: (ByteArray) -> Unit,
    onMinimizeWithMiniIndicator: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val phoneCallController = koinInject<PhoneCallController>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val callAccess = settings.displaySetting.enablePhoneCallAccess
    val autoAnswer = settings.displaySetting.enablePhoneCallAutoAnswerAttempt
    val callPermissions = rememberPermissionState(
        permissions = setOf(
            PermissionCallPhone,
            PermissionReadPhoneState,
            PermissionAnswerPhoneCalls,
        )
    )
    PermissionManager(callPermissions)
    LaunchedEffect(callPermissions.allPermissionsGranted) {
        phoneCallController.syncListener()
    }

    val isRunning = PhoneAutomationService.isRunning()
    val isEnabled = PhoneAutomationService.isAccessibilityEnabled(context)
    val hasPhoneTools = assistant.localTools.contains(LocalToolOption.PhoneAutomation)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.SmartPhone01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.phone_automation),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.phone_automation_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(HugeIcons.Cancel01, contentDescription = "Close")
                }
            }

            // Service Status Card
            if (isRunning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = HugeIcons.CheckmarkCircle02,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.phone_service_status_active),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Ready to inspect screens, perform gestures, and automate apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = HugeIcons.Alert02,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.phone_service_status_inactive),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = stringResource(R.string.phone_service_inactive_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.phone_service_enable_button))
                        }
                    }
                }
            }

            // AI Tools Toggle for Assistant
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.phone_tools_toggle_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.phone_tools_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = hasPhoneTools,
                        onCheckedChange = { enable ->
                            if (enable && !isRunning) {
                                toaster.show(
                                    message = context.getString(R.string.phone_service_inactive_desc),
                                    type = ToastType.Warning
                                )
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                            val updatedTools = if (enable) {
                                assistant.localTools + LocalToolOption.PhoneAutomation
                            } else {
                                assistant.localTools - LocalToolOption.PhoneAutomation
                            }
                            onUpdateAssistant(assistant.copy(localTools = updatedTools))
                        }
                    )
                }
            }


            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.phone_call_access_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.phone_call_access_sheet_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = callAccess,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsStore.update { current ->
                                        current.copy(
                                            displaySetting = current.displaySetting.copy(
                                                enablePhoneCallAccess = enabled,
                                            )
                                        )
                                    }
                                }
                                if (enabled && !callPermissions.allRequiredPermissionsGranted) {
                                    callPermissions.requestPermissions()
                                }
                                phoneCallController.syncListener()
                            },
                        )
                    }
                    if (!callPermissions.allRequiredPermissionsGranted) {
                        Text(
                            text = stringResource(R.string.phone_call_permissions_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = { callPermissions.requestPermissions() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.phone_call_grant_permissions))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.phone_call_auto_answer_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.phone_call_auto_answer_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoAnswer,
                            enabled = callAccess,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsStore.update { current ->
                                        current.copy(
                                            displaySetting = current.displaySetting.copy(
                                                enablePhoneCallAutoAnswerAttempt = enabled,
                                            )
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Explicit mini indicator — never the primary phone-icon action
            if (onMinimizeWithMiniIndicator != null) {
                OutlinedButton(
                    onClick = onMinimizeWithMiniIndicator,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = HugeIcons.SmartPhone01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.phone_mini_indicator_minimize_button))
                }
                Text(
                    text = stringResource(R.string.phone_mini_indicator_minimize_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quick Actions
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Inspect Screen
                ElevatedCard(
                    onClick = {
                        val service = PhoneAutomationService.instance
                        if (service == null) {
                            toaster.show(
                                message = "Enable Accessibility service first",
                                type = ToastType.Warning
                            )
                            return@ElevatedCard
                        }
                        val inspection = service.inspectScreen()
                        val prompt = buildString {
                            appendLine("Current Phone Screen Analysis:")
                            appendLine("App Package: ${inspection.packageName}")
                            appendLine("Window: ${inspection.windowTitle}")
                            appendLine("Interactive Elements (${inspection.interactiveElements.size}):")
                            inspection.interactiveElements.take(15).forEach { node ->
                                val label = node.text.ifBlank { node.description }
                                appendLine("- [${node.className}] \"$label\" (Center: ${node.centerX}, ${node.centerY})")
                            }
                            appendLine("\nPlease suggest what actions can be taken on this screen.")
                        }
                        onAppendPrompt(prompt)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = HugeIcons.GlobalSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.phone_action_inspect_screen),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.phone_action_inspect_screen_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Take Screenshot
                ElevatedCard(
                    onClick = {
                        val service = PhoneAutomationService.instance
                        if (service == null) {
                            toaster.show(
                                message = "Enable Accessibility service first",
                                type = ToastType.Warning
                            )
                            return@ElevatedCard
                        }
                        scope.launch {
                            val bitmap = service.takeScreenshot()
                            if (bitmap != null) {
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                                onAttachScreenshot(stream.toByteArray())
                                onAppendPrompt("Please analyze this phone screenshot and help me.")
                                onDismissRequest()
                            } else {
                                toaster.show(
                                    message = "Failed to capture screenshot (Android 11+ required)",
                                    type = ToastType.Error
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = HugeIcons.Image01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.phone_action_take_screenshot),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.phone_action_take_screenshot_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Sample Automation Prompts
            Text(
                text = "Example Automation Prompts",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Clickable whenever phone tools are on and accessibility is allowed
            val promptsEnabled = hasPhoneTools && isEnabled
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val prompts = listOf(
                    "Open YouTube and search for relaxing music",
                    "Open Settings and check battery status",
                    "Open Google Maps and find coffee shops near me",
                    "Inspect screen and tap the next button",
                    "Take a screenshot and summarize what is displayed"
                )
                prompts.forEach { promptText ->
                    SuggestionChip(
                        onClick = {
                            onAppendPrompt(promptText)
                            onDismissRequest()
                        },
                        enabled = promptsEnabled,
                        label = {
                            Text(
                                text = promptText,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }
        }
    }
}
