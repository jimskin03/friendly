package me.rerere.rikkahub.ui.pages.folder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.FolderLabel
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FolderBadge
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderConversationsPage(
    folderId: String,
    folderName: String,
    folderLabelId: String = "planning",
    conversationRepo: ConversationRepository = koinInject(),
    chatService: ChatService = koinInject(),
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val folderUuid = remember(folderId) {
        runCatching { Uuid.parse(folderId) }.getOrNull()
    }
    val folderLabel = remember(folderLabelId) { FolderLabel.fromId(folderLabelId) }

    val conversations = remember(folderUuid) {
        if (folderUuid != null) {
            conversationRepo.getConversationsOfFolderPaging(folderUuid)
        } else {
            kotlinx.coroutines.flow.emptyFlow()
        }
    }.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FolderBadge(
                            label = folderLabel,
                            size = 32.dp,
                            iconSize = 16.dp,
                            shapeRadius = 10.dp,
                        )
                        Column {
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Saved chat sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            val newChatId = Uuid.random()
                            scope.launch {
                                if (folderUuid != null) {
                                    chatService.initializeConversation(newChatId, folderUuid)
                                }
                                navigateToChatPage(
                                    navigator = navController,
                                    chatId = newChatId,
                                    folderId = folderId,
                                    folderName = folderName,
                                    folderLabelId = folderLabelId,
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = HugeIcons.Add01,
                            contentDescription = "New chat in folder",
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val newChatId = Uuid.random()
                    scope.launch {
                        if (folderUuid != null) {
                            chatService.initializeConversation(newChatId, folderUuid)
                        }
                        navigateToChatPage(
                            navigator = navController,
                            chatId = newChatId,
                            folderId = folderId,
                            folderName = folderName,
                            folderLabelId = folderLabelId,
                        )
                    }
                },
                containerColor = folderLabel.color,
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = "New chat in folder",
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { contentPadding ->
        if (conversations.itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FolderBadge(
                        label = folderLabel,
                        size = 64.dp,
                        iconSize = 32.dp,
                        shapeRadius = 20.dp,
                    )
                    Text(
                        text = "No saved chat sessions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Chat sessions saved in \"$folderName\" will be organized here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val newChatId = Uuid.random()
                            scope.launch {
                                if (folderUuid != null) {
                                    chatService.initializeConversation(newChatId, folderUuid)
                                }
                                navigateToChatPage(
                                    navigator = navController,
                                    chatId = newChatId,
                                    folderId = folderId,
                                    folderName = folderName,
                                    folderLabelId = folderLabelId,
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = HugeIcons.Message01,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Start new chat")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = conversations.itemCount,
                    key = conversations.itemKey { it.id.toString() },
                ) { index ->
                    val conversation = conversations[index]
                    if (conversation != null) {
                        SwipeableFolderConversationItem(
                            conversation = conversation,
                            onClick = {
                                navigateToChatPage(
                                    navigator = navController,
                                    chatId = conversation.id,
                                    folderId = folderId,
                                    folderName = folderName,
                                    folderLabelId = folderLabelId,
                                )
                            },
                            onDelete = {
                                scope.launch {
                                    conversationRepo.deleteConversation(conversation)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Conversation deleted",
                                        actionLabel = "Undo",
                                        withDismissAction = true,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        conversationRepo.insertConversation(conversation)
                                    }
                                }
                            },
                            onTogglePin = {
                                scope.launch {
                                    chatService.toggleConversationPinned(conversation.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableFolderConversationItem(
    conversation: Conversation,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val positionThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = positionThreshold,
        )
    }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = HugeIcons.Delete01,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (conversation.isPinned) {
                            Icon(
                                imageVector = HugeIcons.Pin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = conversation.title.ifBlank { "New conversation" }.trim(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = conversation.updateAt.toLocalDateTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin,
                            contentDescription = if (conversation.isPinned) "Unpin" else "Pin",
                        )
                    }
                }
            )
        }
    }
}
