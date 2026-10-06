package me.rerere.rikkahub.ui.pages.chat.phone

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.phone.PhoneAutomationService
import me.rerere.rikkahub.ui.context.LocalToaster
import androidx.compose.runtime.LaunchedEffect
import me.rerere.rikkahub.service.phone.PhoneCallController
import android.os.Build
import me.rerere.rikkahub.ui.components.ui.permission.PermissionAnswerPhoneCalls
import me.rerere.rikkahub.ui.components.ui.permission.PermissionBluetoothConnect
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCallPhone
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionReadPhoneState
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAutomationSheet(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismissRequest: () -> Unit,
    onMinimizeWithMiniIndicator: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val phoneCallController = koinInject<PhoneCallController>()
    val callPermissions = rememberPermissionState(
        permissions = buildSet {
            add(PermissionCallPhone)
            add(PermissionReadPhoneState)
            add(PermissionAnswerPhoneCalls)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(PermissionBluetoothConnect)
            }
        }
    )
    PermissionManager(callPermissions)
    LaunchedEffect(callPermissions.allPermissionsGranted) {
        phoneCallController.syncListener()
        if (!callPermissions.allRequiredPermissionsGranted) {
            callPermissions.requestPermissions()
        }
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
            }

        }
    }
}
