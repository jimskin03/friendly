package app.friendly.assistant.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Book03
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.WavingHand01
import app.friendly.assistant.R
import app.friendly.assistant.Screen
import app.friendly.assistant.data.datastore.isNotConfigured
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import me.rerere.hugeicons.stroke.PencilEdit01
import app.friendly.assistant.ui.components.ui.UIAvatar
import app.friendly.assistant.ui.hooks.useEditState
import app.friendly.assistant.ui.hooks.EditStateContent
import app.friendly.assistant.data.files.FilesManager
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.components.ui.CardGroup
import app.friendly.assistant.ui.components.ui.Select
import app.friendly.assistant.ui.context.LocalNavController
import app.friendly.assistant.ui.context.Navigator
import app.friendly.assistant.ui.hooks.rememberColorMode
import app.friendly.assistant.ui.theme.ColorMode
import app.friendly.assistant.ui.theme.CustomColors
import app.friendly.assistant.utils.joinQQGroup
import app.friendly.assistant.utils.openUrl
import app.friendly.assistant.utils.plus
import app.friendly.assistant.utils.writeClipboardText
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = koinInject()



    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            item("userProfile") {
                UserProfileCard(
                    settings = settings,
                    onUpdateSettings = { vm.updateSettings(it) },
                )
            }

            item("generalSettings") {
                var colorMode by rememberColorMode()
                val selectedColorModeText = when (colorMode) {
                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_general_settings)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Sun01, null) },
                        trailingContent = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = {
                                    colorMode = it
                                    navController.navigate(Screen.Setting) {
                                        popUpTo(Screen.Setting) {
                                            inclusive = true
                                        }
                                    }
                                },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.width(150.dp)
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_color_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferences) },
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingHomeActions) },
                        leadingContent = { Icon(HugeIcons.Sparkles, null) },
                        supportingContent = { Text(stringResource(R.string.setting_home_actions_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_home_actions)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Assistant) },
                        leadingContent = { Icon(HugeIcons.LookTop, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Extensions) },
                        leadingContent = { Icon(HugeIcons.Package, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                    )
                }
            }

            item("modelServices") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_model_and_services)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingModels) },
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(HugeIcons.Megaphone01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingMcp) },
                        leadingContent = { Icon(HugeIcons.McpServer, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                    )
                }
            }

            item("dataSettings") {
                val storageState by produceState(-1 to 0L) {
                    value = filesManager.countChatFiles()
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_data_settings)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Backup) },
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingFiles) },
                        leadingContent = { Icon(HugeIcons.ImageUpload, null) },
                        supportingContent = {
                            if (storageState.first == -1) {
                                Text(stringResource(R.string.calculating))
                            } else {
                                Text(
                                    stringResource(
                                        R.string.setting_page_chat_storage_desc,
                                        storageState.first,
                                        storageState.second / 1024 / 1024.0
                                    )
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                }
            }

            item("about") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Stats) },
                        leadingContent = { Icon(HugeIcons.ChartColumn, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_analyze_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_analyze)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingAbout) },
                        leadingContent = { Icon(HugeIcons.Book03, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_about_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigWarningCard(navController: Navigator) {
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ListItem(
                supportingContent = {
                    Text(stringResource(R.string.setting_page_config_api_desc))
                },
                leadingContent = {
                    Icon(HugeIcons.Alert01, null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            ) {
                Text(stringResource(R.string.setting_page_config_api_title))
            }

            TextButton(
                onClick = {
                    navController.navigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}

@Composable
private fun UserProfileCard(
    settings: app.friendly.assistant.data.datastore.Settings,
    onUpdateSettings: (app.friendly.assistant.data.datastore.Settings) -> Unit,
) {
    val nicknameEditState = useEditState<String> { newNickname ->
        onUpdateSettings(
            settings.copy(
                displaySetting = settings.displaySetting.copy(
                    userNickname = newNickname.trim()
                )
            )
        )
    }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.setting_page_profile)) },
    ) {
        item(
            leadingContent = {
                UIAvatar(
                    name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    value = settings.displaySetting.userAvatar,
                    onUpdate = { newAvatar ->
                        onUpdateSettings(
                            settings.copy(
                                displaySetting = settings.displaySetting.copy(
                                    userAvatar = newAvatar
                                )
                            )
                        )
                    },
                    modifier = Modifier.size(52.dp),
                )
            },
            headlineContent = {
                Text(
                    text = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(stringResource(R.string.setting_page_profile_desc))
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        nicknameEditState.open(settings.displaySetting.userNickname)
                    }
                ) {
                    Icon(
                        imageVector = HugeIcons.PencilEdit01,
                        contentDescription = stringResource(R.string.chat_page_edit_nickname),
                    )
                }
            },
            onClick = {
                nicknameEditState.open(settings.displaySetting.userNickname)
            }
        )
    }

    nicknameEditState.EditStateContent { nickname, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                nicknameEditState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_nickname))
            },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

