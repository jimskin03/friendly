package app.friendly.assistant.ui.pages.setting

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.friendly.assistant.R
import app.friendly.assistant.data.datastore.BackgroundEffectType
import app.friendly.assistant.data.datastore.DisplaySetting
import app.friendly.assistant.data.datastore.PhoneAutomationWindowMode
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.components.ui.CardGroup
import app.friendly.assistant.ui.components.ui.Select
import app.friendly.assistant.ui.hooks.rememberSharedPreferenceBoolean
import app.friendly.assistant.ui.theme.CustomColors
import app.friendly.assistant.utils.plus
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import com.dokar.sonner.ToastType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.friendly.assistant.data.datastore.isAgentCallActive
import app.friendly.assistant.service.phone.agentcall.AgentCallManager
import app.friendly.assistant.ui.context.LocalToaster
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesGeneralPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var ttsPlaybackSpeed by remember(settings.defaultTTSPlaybackSpeed) {
        mutableFloatStateOf(settings.defaultTTSPlaybackSpeed)
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val agentCallManager = koinInject<AgentCallManager>()
    var showAgentCallDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_preferences_general))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true
                )
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
                        trailingContent = {
                            Switch(
                                checked = createNewConversationOnStart,
                                onCheckedChange = { createNewConversationOnStart = it }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.sendOnEnter,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(sendOnEnter = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showMessageJumper,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showMessageJumper = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.showMessageJumper) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_title)) },
                            supportingContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = displaySetting.messageJumperOnLeft,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                                    }
                                )
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableAutoScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableAutoScroll = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_title)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.useAppIconStyleLoadingIndicator,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(useAppIconStyleLoadingIndicator = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_background_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableBlurEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableBlurEffect = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.enableBlurEffect) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_type)) },
                            supportingContent = {
                                Select(
                                    options = BackgroundEffectType.entries,
                                    selectedOption = displaySetting.backgroundEffectType,
                                    onOptionSelected = {
                                        updateDisplaySetting(displaySetting.copy(backgroundEffectType = it))
                                    },
                                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                                    optionToString = {
                                        when (it) {
                                            BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                            BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                        }
                                    },
                                )
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_surface_transparency_title)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.setting_display_page_surface_transparency_desc))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.chatSurfaceTransparency.toFloat(),
                                        onValueChange = {
                                            updateDisplaySetting(
                                                displaySetting.copy(
                                                    chatSurfaceTransparency = it.roundToInt().coerceIn(0, 100),
                                                )
                                            )
                                        },
                                        valueRange = 0f..100f,
                                        steps = 99,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(text = "${displaySetting.chatSurfaceTransparency}")
                                }
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableMessageGenerationHapticEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.skipCropImage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.pasteLongTextAsFile,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.pasteLongTextAsFile) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_threshold_title)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.pasteLongTextThreshold.toFloat(),
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt()))
                                        },
                                        valueRange = 100f..10000f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${displaySetting.pasteLongTextThreshold}")
                                }
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableVolumeKeyScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.enableVolumeKeyScroll) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_ratio)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.volumeKeyScrollRatio,
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                                        },
                                        valueRange = 0.25f..1.0f,
                                        steps = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%")
                                }
                            }
                        )
                    }
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_tts_settings)) },
                ) {
                    item(
                        headlineContent = {
                            Text(stringResource(R.string.setting_tts_page_default_playback_speed))
                        },
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.setting_tts_page_default_playback_speed_description))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = ttsPlaybackSpeed,
                                        onValueChange = {
                                            ttsPlaybackSpeed = (it * 10).roundToInt() / 10f
                                        },
                                        onValueChangeFinished = {
                                            vm.updateSettings(
                                                settings.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeed)
                                            )
                                        },
                                        valueRange = 0.5f..2.0f,
                                        steps = 14,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(text = "x${"%.1f".format(ttsPlaybackSpeed)}")
                                }
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadQuoted,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadOutsideBrackets,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.autoPlayTTSAfterGeneration,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_reply_with_voice_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_reply_with_voice_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.replyWithVoice,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(replyWithVoice = it))
                                }
                            )
                        },
                    )
                }
            }

            item {
                val context = LocalContext.current
                var showOverlayPermissionDialog by remember { mutableStateOf(false) }

                if (showOverlayPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showOverlayPermissionDialog = false },
                        title = { Text(stringResource(R.string.phone_mini_indicator_overlay_title)) },
                        text = { Text(stringResource(R.string.phone_mini_indicator_overlay_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showOverlayPermissionDialog = false
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    runCatching { context.startActivity(intent) }
                                }
                            ) {
                                Text(stringResource(R.string.phone_mini_indicator_overlay_continue))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverlayPermissionDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }

                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.phone_automation_window_mode_title)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.phone_automation_window_mode_desc))
                                Select(
                                    options = PhoneAutomationWindowMode.entries,
                                    selectedOption = displaySetting.phoneAutomationWindowMode,
                                    onOptionSelected = { mode ->
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                phoneAutomationWindowMode = mode,
                                                enablePhoneAutomationMiniIndicator = if (mode == PhoneAutomationWindowMode.OFF) {
                                                    displaySetting.enablePhoneAutomationMiniIndicator
                                                } else {
                                                    false
                                                },
                                            )
                                        )
                                    },
                                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                                    optionToString = {
                                        when (it) {
                                            PhoneAutomationWindowMode.OFF -> stringResource(R.string.phone_automation_window_mode_off)
                                            PhoneAutomationWindowMode.SPLIT -> stringResource(R.string.phone_automation_window_mode_split)
                                            PhoneAutomationWindowMode.POPUP -> stringResource(R.string.phone_automation_window_mode_popup)
                                        }
                                    },
                                )
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.phone_mini_indicator_title)) },
                        supportingContent = { Text(stringResource(R.string.phone_mini_indicator_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enablePhoneAutomationMiniIndicator &&
                                    displaySetting.phoneAutomationWindowMode == PhoneAutomationWindowMode.OFF,
                                enabled = displaySetting.phoneAutomationWindowMode == PhoneAutomationWindowMode.OFF,
                                onCheckedChange = { enabled ->
                                    if (displaySetting.phoneAutomationWindowMode == PhoneAutomationWindowMode.OFF) {
                                        if (enabled && !Settings.canDrawOverlays(context)) {
                                            showOverlayPermissionDialog = true
                                        }
                                        updateDisplaySetting(
                                            displaySetting.copy(enablePhoneAutomationMiniIndicator = enabled)
                                        )
                                    }
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.agent_call_setting_title)) },
                        supportingContent = { Text(stringResource(R.string.agent_call_setting_desc)) },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(
                                    onClick = { showAgentCallDialog = true }
                                ) {
                                    Text(stringResource(R.string.configure))
                                }
                                Switch(
                                    checked = displaySetting.agentCallSetting.enabled,
                                    onCheckedChange = { enabled ->
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                agentCallSetting = displaySetting.agentCallSetting.copy(enabled = enabled)
                                            )
                                        )
                                    }
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAgentCallDialog) {
        var provider by remember { mutableStateOf(displaySetting.agentCallSetting.provider) }
        var apiKey by remember { mutableStateOf(displaySetting.agentCallSetting.apiKey) }
        var phoneNumberId by remember { mutableStateOf(displaySetting.agentCallSetting.phoneNumberId) }
        var agentId by remember { mutableStateOf(displaySetting.agentCallSetting.agentId) }
        var voiceId by remember { mutableStateOf(displaySetting.agentCallSetting.voiceId) }
        var ownerName by remember { mutableStateOf(displaySetting.agentCallSetting.ownerName) }
        var discloseAi by remember { mutableStateOf(displaySetting.agentCallSetting.discloseAi) }
        var testing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAgentCallDialog = false },
            title = { Text(stringResource(R.string.agent_call_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.agent_call_provider),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                    Select(
                        options = listOf("vapi", "elevenlabs"),
                        selectedOption = provider,
                        onOptionSelected = { provider = it },
                        modifier = Modifier.fillMaxWidth(),
                        optionToString = {
                            when (it) {
                                "vapi" -> "Vapi"
                                "elevenlabs" -> "ElevenLabs"
                                else -> it
                            }
                        }
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.agent_call_api_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneNumberId,
                        onValueChange = { phoneNumberId = it },
                        label = { Text(stringResource(R.string.agent_call_phone_number_id)) },
                        placeholder = {
                            Text(
                                if (provider == "vapi") "Vapi Phone Number ID"
                                else "Twilio / ElevenLabs Phone Number ID"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text(stringResource(R.string.agent_call_owner_name)) },
                        placeholder = { Text(stringResource(R.string.agent_call_owner_name_desc)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = agentId,
                        onValueChange = { agentId = it },
                        label = { Text(stringResource(R.string.agent_call_agent_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (provider == "vapi") {
                        OutlinedTextField(
                            value = voiceId,
                            onValueChange = { voiceId = it },
                            label = { Text(stringResource(R.string.agent_call_voice_id)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.agent_call_disclose_ai))
                            Text(
                                stringResource(R.string.agent_call_disclose_ai_desc),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = discloseAi,
                            onCheckedChange = { discloseAi = it }
                        )
                    }
                    Button(
                        onClick = {
                            testing = true
                            scope.launch {
                                val currentSetting = displaySetting.agentCallSetting.copy(
                                    provider = provider,
                                    apiKey = apiKey.trim(),
                                    phoneNumberId = phoneNumberId.trim(),
                                    agentId = agentId.trim(),
                                    voiceId = voiceId.trim(),
                                    ownerName = ownerName.trim(),
                                    discloseAi = discloseAi,
                                )
                                val res = agentCallManager.testConnection(currentSetting)
                                testing = false
                                if (res.isSuccess) {
                                    toaster.show(
                                        message = context.getString(R.string.agent_call_test_success),
                                        type = ToastType.Success
                                    )
                                } else {
                                    val err = res.exceptionOrNull()?.message ?: "Unknown error"
                                    toaster.show(message = err, type = ToastType.Error)
                                }
                            }
                        },
                        enabled = !testing && apiKey.isNotBlank() && phoneNumberId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (testing) stringResource(R.string.calculating) else stringResource(R.string.agent_call_test_connection))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newSetting = displaySetting.agentCallSetting.copy(
                            provider = provider,
                            apiKey = apiKey.trim(),
                            phoneNumberId = phoneNumberId.trim(),
                            agentId = agentId.trim(),
                            voiceId = voiceId.trim(),
                            ownerName = ownerName.trim(),
                            discloseAi = discloseAi,
                        )
                        updateDisplaySetting(displaySetting.copy(agentCallSetting = newSetting))
                        showAgentCallDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgentCallDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
