package me.rerere.rikkahub.ui.pages.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppPage(
    appId: String,
    vm: AppsVM = koinViewModel(),
) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val id = remember(appId) { runCatching { Uuid.parse(appId) }.getOrNull() }
    val app = settings.installedWebApps.firstOrNull { it.id == id }
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val state = rememberWebViewState(
        url = app?.startUrl?.takeIf { it.isNotBlank() } ?: "about:blank",
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )

    BackHandler(enabled = app != null && state.canGoBack) {
        state.goBack()
    }

    if (app == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps_page_title)) },
                    navigationIcon = { BackButton() },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (!settings.init) {
                    Text(
                        text = stringResource(R.string.apps_page_missing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = stringResource(R.string.apps_page_remove),
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.apps_page_remove)) },
                                leadingIcon = { Icon(HugeIcons.Delete01, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRemoveConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    RikkaConfirmDialog(
        show = showRemoveConfirm,
        title = stringResource(R.string.apps_page_remove_title),
        confirmText = stringResource(R.string.apps_page_remove),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRemoveConfirm = false
            val removingId = app.id
            scope.launch {
                vm.removeApp(removingId)
                navController.popBackStack()
            }
        },
        onDismiss = { showRemoveConfirm = false },
    ) {
        Text(stringResource(R.string.apps_page_remove_message, app.name))
    }
    }
}
