package app.friendly.assistant.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.friendly.assistant.R
import app.friendly.assistant.data.model.HomeAction
import app.friendly.assistant.data.model.HomeActionKind
import app.friendly.assistant.data.model.InstalledWebApp
import app.friendly.assistant.data.model.normalizeHomeActions
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.components.ui.Select
import app.friendly.assistant.ui.theme.CustomColors
import app.friendly.assistant.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingHomeActionsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var drafts by remember { mutableStateOf<List<HomeAction>?>(null) }

    LaunchedEffect(settings.init, settings.homeActions) {
        if (!settings.init && drafts == null) {
            drafts = normalizeHomeActions(settings.homeActions)
        }
    }

    fun persist(next: List<HomeAction>) {
        drafts = next
        if (!settings.init) {
            vm.updateSettings(settings.copy(homeActions = normalizeHomeActions(next)))
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_home_actions)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        val actions = drafts
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.setting_home_actions_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actions != null) {
                itemsIndexed(actions, key = { index, _ -> index }) { index, action ->
                    HomeActionEditor(
                        index = index,
                        action = action,
                        apps = settings.installedWebApps,
                        onChange = { updated ->
                            val next = actions.toMutableList()
                            next[index] = updated
                            persist(next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeActionEditor(
    index: Int,
    action: HomeAction,
    apps: List<InstalledWebApp>,
    onChange: (HomeAction) -> Unit,
) {
    val promptLabel = stringResource(R.string.home_action_prompt)
    val launchLabel = stringResource(R.string.home_action_launch_app)
    val selectedApp = apps.firstOrNull { it.id.toString() == action.appId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_action_slot, index + 1),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = action.label,
                onValueChange = { onChange(action.copy(label = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.home_action_label)) },
            )
            Select(
                options = HomeActionKind.entries,
                selectedOption = action.kind,
                onOptionSelected = { kind ->
                    if (kind == action.kind) return@Select
                    val appId = when {
                        kind != HomeActionKind.LAUNCH_APP -> action.appId
                        action.appId.isNotBlank() && apps.any { it.id.toString() == action.appId } -> action.appId
                        else -> apps.firstOrNull()?.id?.toString().orEmpty()
                    }
                    onChange(action.copy(kind = kind, appId = appId))
                },
                optionToString = {
                    when (it) {
                        HomeActionKind.PROMPT -> promptLabel
                        HomeActionKind.LAUNCH_APP -> launchLabel
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            when (action.kind) {
                HomeActionKind.PROMPT -> {
                    OutlinedTextField(
                        value = action.prompt,
                        onValueChange = { onChange(action.copy(prompt = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text(stringResource(R.string.home_action_prompt_text)) },
                    )
                }

                HomeActionKind.LAUNCH_APP -> {
                    if (apps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_action_no_apps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Select(
                            options = apps,
                            selectedOption = selectedApp ?: apps.first(),
                            onOptionSelected = { app ->
                                onChange(action.copy(appId = app.id.toString()))
                            },
                            optionToString = { it.name.ifBlank { it.startUrl } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
