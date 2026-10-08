package app.friendly.assistant.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.friendly.assistant.BuildConfig
import app.friendly.assistant.R
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.pages.setting.components.PaidThemePreviewGrid
import app.friendly.assistant.ui.pages.setting.components.PresetThemeButtonGroup
import app.friendly.assistant.ui.theme.CustomColors
import app.friendly.assistant.ui.theme.PresetThemes
import app.friendly.assistant.ui.theme.presets.PaidThemes
import app.friendly.assistant.utils.plus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingThemePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val paidUnlocked = !BuildConfig.IS_PLAY_BUILD || settings.paidThemesUnlocked
    var showPaidLockDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_theme_setting)) },
                navigationIcon = { BackButton() },
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
            if (settings.dynamicColor) {
                item("dynamicColorHint") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.setting_theme_page_dynamic_color_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!settings.dynamicColor) {
                item("presetThemes") {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.setting_theme_page_preset_themes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceBright)
                        ) {
                            PresetThemeButtonGroup(
                                themeId = settings.themeId,
                                themes = PresetThemes,
                                modifier = Modifier.fillMaxWidth(),
                                onChangeTheme = {
                                    vm.updateSettings(settings.copy(themeId = it))
                                }
                            )
                        }
                    }
                }

                item("paidThemes") {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.setting_theme_page_paid_themes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceBright)
                        ) {
                            PaidThemePreviewGrid(
                                themeId = settings.themeId,
                                themes = PaidThemes,
                                lockedThemeIds = if (paidUnlocked) {
                                    emptySet()
                                } else {
                                    PaidThemes.map { it.id }.toSet()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onChangeTheme = {
                                    vm.updateSettings(settings.copy(themeId = it))
                                },
                                onLockedClick = {
                                    showPaidLockDialog = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPaidLockDialog) {
        AlertDialog(
            onDismissRequest = { showPaidLockDialog = false },
            title = { Text(stringResource(R.string.setting_theme_page_paid_locked_title)) },
            text = { Text(stringResource(R.string.setting_theme_page_paid_locked_message)) },
            confirmButton = {
                TextButton(onClick = { showPaidLockDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
