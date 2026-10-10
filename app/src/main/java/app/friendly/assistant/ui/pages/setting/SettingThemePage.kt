package app.friendly.assistant.ui.pages.setting

import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.friendly.assistant.R
import app.friendly.assistant.data.billing.PaidThemeProducts
import app.friendly.assistant.data.billing.PaidThemeStore
import app.friendly.assistant.data.billing.StoreEvent
import app.friendly.assistant.data.billing.StoreStatus
import app.friendly.assistant.ui.context.LocalToaster
import com.dokar.sonner.ToastType
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.pages.setting.components.PaidThemeStoreSection
import app.friendly.assistant.ui.pages.setting.components.PresetThemeButtonGroup
import app.friendly.assistant.ui.theme.CustomColors
import app.friendly.assistant.ui.theme.PresetThemes
import app.friendly.assistant.ui.theme.presets.PaidThemes
import app.friendly.assistant.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingThemePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val store: PaidThemeStore = koinInject()
    val storeState by store.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val toaster = LocalToaster.current
    val resources = LocalResources.current
    var showUnavailableDialog by remember { mutableStateOf(false) }
    val latestSettings by rememberUpdatedState(settings)

    // Fresh prices and purchases each time the page opens (Play may have changed them).
    LaunchedEffect(store) { store.refresh() }
    LaunchedEffect(store) {
        store.events.collect { event ->
            when (event) {
                is StoreEvent.Purchased -> {
                    val themeId = PaidThemeProducts.themeFor(event.productId)
                    if (themeId != null) {
                        vm.updateSettings(latestSettings.copy(themeId = themeId))
                        toaster.show(resources.getString(R.string.setting_theme_page_paid_purchased), type = ToastType.Success)
                    } else {
                        toaster.show(resources.getString(R.string.setting_theme_page_paid_bundle_purchased), type = ToastType.Success)
                    }
                }

                StoreEvent.Pending -> toaster.show(
                    resources.getString(R.string.setting_theme_page_paid_pending_message),
                    type = ToastType.Info,
                )

                StoreEvent.Restored -> toaster.show(
                    resources.getString(R.string.setting_theme_page_paid_restored),
                    type = ToastType.Success,
                )

                StoreEvent.NothingToRestore -> toaster.show(
                    resources.getString(R.string.setting_theme_page_paid_nothing_to_restore),
                    type = ToastType.Info,
                )

                StoreEvent.Unavailable -> showUnavailableDialog = true
                StoreEvent.Failed -> toaster.show(
                    resources.getString(R.string.setting_theme_page_paid_failed),
                    type = ToastType.Error,
                )
            }
        }
    }

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
                            PaidThemeStoreSection(
                                themeId = settings.themeId,
                                themes = PaidThemes,
                                store = storeState,
                                onSelectTheme = {
                                    vm.updateSettings(settings.copy(themeId = it))
                                },
                                onLockedClick = { themeId ->
                                    when (storeState.status) {
                                        StoreStatus.Ready -> if (!storeState.isPending(themeId) && activity != null) {
                                            store.purchase(activity, PaidThemeProducts.forTheme(themeId))
                                        } else if (storeState.isPending(themeId)) {
                                            toaster.show(
                                                resources.getString(R.string.setting_theme_page_paid_pending_message),
                                                type = ToastType.Info,
                                            )
                                        }

                                        StoreStatus.Offline -> store.retry()
                                        StoreStatus.Unavailable -> showUnavailableDialog = true
                                        StoreStatus.Connecting, StoreStatus.Unlocked -> Unit
                                    }
                                },
                                onBuyBundle = {
                                    if (activity != null) store.purchase(activity, PaidThemeProducts.BUNDLE)
                                },
                                onRetry = { store.retry() },
                                onRestore = { store.restore() },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showUnavailableDialog = false },
            title = { Text(stringResource(R.string.setting_theme_page_paid_locked_title)) },
            text = { Text(stringResource(R.string.setting_theme_page_paid_unavailable)) },
            confirmButton = {
                TextButton(onClick = { showUnavailableDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
