package app.friendly.assistant.ui.pages.folder

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Search01
import app.friendly.assistant.R
import app.friendly.assistant.Screen
import app.friendly.assistant.data.model.Folder
import app.friendly.assistant.data.model.FolderLabel
import app.friendly.assistant.data.repository.ConversationRepository
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.components.ui.CreateFolderDialog
import app.friendly.assistant.ui.components.ui.FolderBadge
import app.friendly.assistant.ui.components.ui.FolderLabelPicker
import app.friendly.assistant.ui.context.LocalNavController
import app.friendly.assistant.ui.pages.chat.ChatDrawerVM
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersPage(
    conversationRepo: ConversationRepository = koinInject(),
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val drawerVm: ChatDrawerVM = if (activity != null) {
        koinViewModel(viewModelStoreOwner = activity)
    } else {
        koinViewModel()
    }

    val folders by drawerVm.folders.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToEditLabel by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) {
            folders
        } else {
            folders.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search folders...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF64748B),
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = HugeIcons.Cancel01,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text(
                                text = "Folders",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                ),
                            )
                            Text(
                                text = "${folders.size} ${if (folders.size == 1) "folder" else "folders"} created",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Close search")
                        }
                    } else {
                        BackButton()
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(HugeIcons.Search01, contentDescription = "Search")
                        }
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(HugeIcons.Add01, contentDescription = "New folder")
                    }
                },
            )
        },
        floatingActionButton = {
            if (folders.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(HugeIcons.Add01, contentDescription = "New folder")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (folders.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x2238BDF8)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Folder01,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Text(
                            text = "No folders created yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Text(
                            text = "Create folders to organize and categorize your chat sessions into dedicated workspaces.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { showCreateFolderDialog = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Add01,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Create Folder")
                        }
                    }
                }
            } else if (filteredFolders.isEmpty()) {
                // Search result empty
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No folders found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "No folders match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredFolders, key = { it.id.toString() }) { folder ->
                        FolderCardItem(
                            folder = folder,
                            conversationRepo = conversationRepo,
                            onClick = {
                                navController.navigate(
                                    Screen.FolderConversations(
                                        folderId = folder.id.toString(),
                                        folderName = folder.name,
                                        folderLabelId = folder.label,
                                    )
                                )
                            },
                            onRename = { folderToRename = folder },
                            onEditLabel = { folderToEditLabel = folder },
                            onDelete = { folderToDelete = folder },
                        )
                    }
                }
            }
        }
    }

    // Dialog: Create Folder
    CreateFolderDialog(
        visible = showCreateFolderDialog,
        onDismissRequest = { showCreateFolderDialog = false },
        onConfirm = { name, label ->
            drawerVm.createFolder(name, label.id)
            showCreateFolderDialog = false
        }
    )

    // Dialog: Rename Folder
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // Dialog: Change Label
    folderToEditLabel?.let { folder ->
        var selectedLabel by remember(folder.id) {
            mutableStateOf(FolderLabel.fromId(folder.label))
        }
        AlertDialog(
            onDismissRequest = { folderToEditLabel = null },
            title = { Text("Change folder label") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Selected label",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = selectedLabel.color.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = selectedLabel.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = selectedLabel.color,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    FolderLabelPicker(
                        selected = selectedLabel,
                        onSelect = { selectedLabel = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.updateFolderLabel(folder.id, selectedLabel.id)
                        folderToEditLabel = null
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToEditLabel = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // Dialog: Delete Folder Confirmation
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.deleteFolder(folder.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.chat_page_delete_folder))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}

@Composable
private fun FolderCardItem(
    folder: Folder,
    conversationRepo: ConversationRepository,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onEditLabel: () -> Unit,
    onDelete: () -> Unit,
) {
    val folderLabel = remember(folder.label) { FolderLabel.fromId(folder.label) }
    val conversationCount by remember(folder.id) {
        conversationRepo.getConversationCountOfFolder(folder.id)
    }.collectAsStateWithLifecycle(initialValue = 0)

    val formattedDate = remember(folder.createAt) {
        runCatching {
            folder.createAt
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        }.getOrNull() ?: ""
    }

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x221E293B),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FolderBadge(
                label = folderLabel,
                size = 46.dp,
                iconSize = 22.dp,
                shapeRadius = 14.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val countText = if (conversationCount == 0) {
                        "No saved chats"
                    } else if (conversationCount == 1) {
                        "1 saved chat"
                    } else {
                        "$conversationCount saved chats"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                    )
                    if (formattedDate.isNotEmpty()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Options menu button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.MoreVertical,
                        contentDescription = "Options",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_page_rename_folder)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(HugeIcons.Edit01, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Change label") },
                        onClick = {
                            showMenu = false
                            onEditLabel()
                        },
                        leadingIcon = {
                            Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.chat_page_delete_folder),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    )
                }
            }

            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
