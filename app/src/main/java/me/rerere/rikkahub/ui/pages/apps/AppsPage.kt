package me.rerere.rikkahub.ui.pages.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Grid
import me.rerere.hugeicons.stroke.Heart
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.FolderLabel
import me.rerere.rikkahub.data.model.InstalledWebApp
import me.rerere.rikkahub.data.model.WebAppLaunchMode
import me.rerere.rikkahub.data.model.WebAppZoomMode
import me.rerere.rikkahub.data.model.normalizeHttpsStartUrl
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FolderBadge
import me.rerere.rikkahub.ui.components.ui.FolderLabelPicker
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppsPage(vm: AppsVM = koinViewModel()) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toaster = LocalToaster.current
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val favoriteLimit = stringResource(R.string.apps_favorite_limit)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.apps_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.apps_page_add))
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!settings.init && settings.installedWebApps.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Grid,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.apps_page_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(settings.installedWebApps, key = { it.id }) { app ->
                InstalledAppCard(
                    app = app,
                    onClick = { navController.navigate(Screen.WebApp(app.id.toString())) },
                    onEdit = { editingId = app.id.toString() },
                )
            }
        }
    }

    val editing = settings.installedWebApps.firstOrNull { it.id.toString() == editingId }
    if (editing != null) {
        AppOptionsDialog(
            app = editing,
            onDismiss = { editingId = null },
            onLaunchMode = { vm.updateLaunchMode(editing.id, it) },
            onZoomMode = { vm.updateZoomMode(editing.id, it) },
            onIcon = { vm.updateIcon(editing.id, it.id) },
            onFavorite = { favorite ->
                if (!vm.setFavorite(editing.id, favorite)) {
                    toaster.show(favoriteLimit, type = ToastType.Error)
                }
            },
        )
    }

    if (showAddDialog) {
        AddAppDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                if (vm.addApp(name, url)) {
                    showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledWebApp,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val label = FolderLabel.entries.firstOrNull { it.id.equals(app.iconId, ignoreCase = true) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (label != null) {
                FolderBadge(label = label, size = 40.dp, iconSize = 20.dp, shapeRadius = 12.dp)
            } else {
                Icon(
                    imageVector = HugeIcons.Grid,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.favorite) {
                        Icon(
                            HugeIcons.Heart,
                            contentDescription = stringResource(R.string.apps_favorite),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (app.description.isNotBlank()) {
                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = app.startUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    HugeIcons.PencilEdit01,
                    contentDescription = stringResource(R.string.apps_edit),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppOptionsDialog(
    app: InstalledWebApp,
    onDismiss: () -> Unit,
    onLaunchMode: (WebAppLaunchMode) -> Unit,
    onZoomMode: (WebAppZoomMode) -> Unit,
    onIcon: (FolderLabel) -> Unit,
    onFavorite: (Boolean) -> Unit,
) {
    val selectedIcon = FolderLabel.entries.firstOrNull { it.id.equals(app.iconId, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.apps_launch_mode),
                    style = MaterialTheme.typography.titleSmall,
                )
                LaunchModePicker(
                    selected = app.launchMode,
                    onSelect = onLaunchMode,
                )
                Text(
                    text = stringResource(R.string.apps_zoom),
                    style = MaterialTheme.typography.titleSmall,
                )
                ZoomModePicker(
                    selected = app.zoomMode,
                    onSelect = onZoomMode,
                )
                Text(
                    text = stringResource(R.string.apps_assign_logo),
                    style = MaterialTheme.typography.titleSmall,
                )
                FolderLabelPicker(
                    selected = selectedIcon,
                    onSelect = onIcon,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.apps_favorite))
                    Switch(
                        checked = app.favorite,
                        onCheckedChange = onFavorite,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.apps_edit_done))
            }
        },
    )
}

@Composable
private fun LaunchModePicker(
    selected: WebAppLaunchMode,
    onSelect: (WebAppLaunchMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        WebAppLaunchMode.entries.forEach { mode ->
            val label = when (mode) {
                WebAppLaunchMode.COMPACT -> stringResource(R.string.apps_launch_compact)
                WebAppLaunchMode.FULLSIZE -> stringResource(R.string.apps_launch_fullsize)
                WebAppLaunchMode.FULLSCREEN -> stringResource(R.string.apps_launch_fullscreen)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(mode) }
                    .padding(4.dp),
            ) {
                PhoneOutline(
                    mode = mode,
                    selected = mode == selected,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mode == selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoomModePicker(
    selected: WebAppZoomMode,
    onSelect: (WebAppZoomMode) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WebAppZoomMode.entries.forEach { mode ->
            val label = when (mode) {
                WebAppZoomMode.AUTO -> stringResource(R.string.apps_zoom_auto)
                WebAppZoomMode.FIT_WIDTH -> stringResource(R.string.apps_zoom_fit_width)
                WebAppZoomMode.PERCENT_75 -> stringResource(R.string.apps_zoom_75)
                WebAppZoomMode.PERCENT_90 -> stringResource(R.string.apps_zoom_90)
                WebAppZoomMode.PERCENT_100 -> stringResource(R.string.apps_zoom_100)
                WebAppZoomMode.PERCENT_110 -> stringResource(R.string.apps_zoom_110)
                WebAppZoomMode.PERCENT_125 -> stringResource(R.string.apps_zoom_125)
            }
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun PhoneOutline(
    mode: WebAppLaunchMode,
    selected: Boolean,
) {
    val shape = RoundedCornerShape(8.dp)
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(72.dp)
            .border(width = if (selected) 2.dp else 1.5.dp, color = color, shape = shape)
            .padding(3.dp),
    ) {
        when (mode) {
            WebAppLaunchMode.COMPACT -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.28f)),
                )
            }
            WebAppLaunchMode.FULLSIZE -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.28f)),
                )
            }
            WebAppLaunchMode.FULLSCREEN -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(5.dp))
                        .background(color.copy(alpha = if (selected) 0.85f else 0.45f)),
                )
            }
        }
    }
}

@Composable
private fun AddAppDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var urlError by rememberSaveable { mutableStateOf(false) }
    val httpsError = stringResource(R.string.apps_page_url_https_only)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_page_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.apps_page_name)) },
                    supportingText = { Text(stringResource(R.string.apps_page_name_hint)) },
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.apps_page_url)) },
                    isError = urlError,
                    supportingText = if (urlError) {
                        { Text(httpsError) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (normalizeHttpsStartUrl(url) == null) {
                        urlError = true
                    } else {
                        onConfirm(name, url)
                    }
                },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.apps_page_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
