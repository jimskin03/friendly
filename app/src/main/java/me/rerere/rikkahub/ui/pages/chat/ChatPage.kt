package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import android.content.Intent
import android.provider.Settings as AndroidSettings
import me.rerere.rikkahub.service.phone.PhoneAutomationMiniIndicatorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.activity.ComponentActivity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.platform.LocalContext
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.CreateFolderDialog
import me.rerere.rikkahub.ui.components.ui.HomeBottomBar
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.model.FolderLabel
import me.rerere.rikkahub.ui.components.ui.CreateFolderDialog
import me.rerere.rikkahub.ui.components.ui.FolderBadge
import me.rerere.rikkahub.ui.components.ui.MoveToFolderSheet
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import io.ktor.client.HttpClient
import me.rerere.rikkahub.data.remote.DesktopControlClient
import me.rerere.rikkahub.data.remote.DesktopControlDefaults
import me.rerere.rikkahub.ui.pages.chat.desktop.DesktopActiveBanner
import me.rerere.rikkahub.ui.pages.chat.desktop.DesktopControlSheet
import me.rerere.rikkahub.ui.pages.chat.phone.PhoneAutomationSheet
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(
    id: Uuid,
    text: String?,
    files: List<Uri>,
    nodeId: Uuid? = null,
    folderId: String? = null,
    folderName: String? = null,
    folderLabelId: String? = null,
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val isInitializing by vm.isInitializing.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val hazeState = rememberHazeState()
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    LaunchedEffect(id, folderId) {
        if (folderId != null) {
            val folderUuid = runCatching { Uuid.parse(folderId) }.getOrNull()
            if (folderUuid != null) {
                vm.initFolderId(folderUuid)
            }
        }
    }

    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    val handleBack: () -> Unit = {
        if (navController.canPop) {
            navController.popBackStack()
        } else {
            navController.clearAndNavigate(Screen.Chat(Uuid.random().toString()))
        }
    }

    BackHandler(enabled = navController.canPop || conversation.messageNodes.isNotEmpty()) {
        handleBack()
    }

    ChatPageContent(
        hazeState = hazeState,
        onStartVoiceMode = startVoiceMode,
        inputState = inputState,
        loadingJob = loadingJob,
        processingStatus = processingStatus,
        setting = setting,
        conversation = conversation,
        isInitializing = isInitializing,
        folderId = folderId,
        folderName = folderName,
        folderLabelId = folderLabelId,
        navController = navController,
        vm = vm,
        chatListState = chatListState,
        enableWebSearch = enableWebSearch,
        currentChatModel = currentChatModel,
        bigScreen = isBigScreen,
        errors = errors,
        onDismissError = { vm.dismissError(it) },
        onClearAllErrors = { vm.clearAllErrors() },
        onBack = handleBack,
    )
}

@Composable
private fun ChatPageContent(
    hazeState: HazeState,
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    isInitializing: Boolean = false,
    folderId: String? = null,
    folderName: String? = null,
    folderLabelId: String? = null,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val httpClient: HttpClient = koinInject()
    var showDesktopSheet by remember { mutableStateOf(false) }
    var desktopStreamUrl by remember { mutableStateOf<String?>(null) }
    var showPhoneAutomationSheet by remember { mutableStateOf(false) }
    var showPhoneMiniOverlayDialog by remember { mutableStateOf(false) }
    val phoneMiniIndicator: PhoneAutomationMiniIndicatorManager = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val assistant = setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        setting.getCurrentChatModel()?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val drawerVm: ChatDrawerVM = if (activity != null) {
        koinViewModel(viewModelStoreOwner = activity)
    } else {
        koinViewModel()
    }
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveToFolderSheet by rememberSaveable { mutableStateOf(false) }

    val effectiveFolderUuid = remember(conversation.folderId, folderId) {
        conversation.folderId ?: runCatching { folderId?.let { Uuid.parse(it) } }.getOrNull()
    }
    val currentFolder = remember(effectiveFolderUuid, folders) {
        if (effectiveFolderUuid != null) {
            folders.firstOrNull { it.id == effectiveFolderUuid }
        } else null
    }
    val activeFolderName = folderName ?: currentFolder?.name
    val activeFolderLabelId = folderLabelId ?: currentFolder?.label ?: "planning"
    val isFolderChat = effectiveFolderUuid != null || activeFolderName != null

    // Mini indicator "Send new prompt" → focus Ask-me-anything input
    LaunchedEffect(phoneMiniIndicator) {
        phoneMiniIndicator.focusInputRequests.collect {
            inputState.requestFocus()
        }
    }
    LaunchedEffect(Unit) {
        val act = context as? ComponentActivity ?: return@LaunchedEffect
        if (act.intent?.getBooleanExtra(PhoneAutomationMiniIndicatorManager.EXTRA_FOCUS_INPUT, false) == true) {
            act.intent?.removeExtra(PhoneAutomationMiniIndicatorManager.EXTRA_FOCUS_INPUT)
            inputState.requestFocus()
        }
    }

    fun activatePhoneMiniMode() {
        if (!setting.displaySetting.enablePhoneAutomationMiniIndicator) {
            toaster.show(
                message = context.getString(R.string.phone_mini_indicator_disabled_toast),
                type = ToastType.Warning,
            )
            return
        }
        if (!AndroidSettings.canDrawOverlays(context)) {
            showPhoneMiniOverlayDialog = true
            return
        }
        phoneMiniIndicator.activate()
        (context as? ComponentActivity)?.moveTaskToBack(true)
    }

    if (showPhoneMiniOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneMiniOverlayDialog = false },
            title = { Text(context.getString(R.string.phone_mini_indicator_overlay_title)) },
            text = { Text(context.getString(R.string.phone_mini_indicator_overlay_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPhoneMiniOverlayDialog = false
                        val intent = Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }
                        // Stay in foreground so the user can grant overlay, then tap phone again.
                    }
                ) {
                    Text(context.getString(R.string.phone_mini_indicator_overlay_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhoneMiniOverlayDialog = false
                        // Notification backup still works without SYSTEM_ALERT_WINDOW.
                        phoneMiniIndicator.activate()
                        (context as? ComponentActivity)?.moveTaskToBack(true)
                    }
                ) {
                    Text(context.getString(R.string.cancel))
                }
            },
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                if (conversation.messageNodes.isNotEmpty() || isFolderChat) {
                    TopBar(
                        settings = setting,
                        conversation = conversation,
                        folders = folders,
                        previewMode = previewMode,
                        currentFolder = currentFolder,
                        folderName = activeFolderName,
                        folderLabelId = activeFolderLabelId,
                        onBack = onBack,
                        onMoveFolder = {
                            showMoveToFolderSheet = true
                        },
                        onNewChat = {
                            navController.clearAndNavigate(Screen.Chat(Uuid.random().toString()))
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onUpdateTitle = {
                            vm.updateTitle(it)
                        }
                    )
                }
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DesktopActiveBanner(
                        visible = desktopStreamUrl != null && !showDesktopSheet,
                        onOpenDesktop = { showDesktopSheet = true },
                        onSnapToChat = {
                            val token = setting.networkSetting.desktopControlApiToken
                            if (token.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val baseUrl = setting.networkSetting.desktopControlBaseUrl.ifBlank { DesktopControlDefaults.BASE_URL }
                                        val client = DesktopControlClient(httpClient, baseUrl, token)
                                        val res = client.screenshot()
                                        val bytes = android.util.Base64.decode(res.image_b64, android.util.Base64.DEFAULT)
                                        val filesManager: FilesManager = org.koin.java.KoinJavaComponent.getKoin().get()
                                        val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
                                        if (uris.isNotEmpty()) {
                                            inputState.addImages(uris)
                                            toaster.show("Screenshot attached to chat", ToastType.Success)
                                        }
                                    } catch (e: Exception) {
                                        toaster.show(e.message ?: "Failed to snap desktop", ToastType.Error)
                                    }
                                }
                            }
                        },
                        onStopStream = {
                            val token = setting.networkSetting.desktopControlApiToken
                            if (token.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val baseUrl = setting.networkSetting.desktopControlBaseUrl.ifBlank { DesktopControlDefaults.BASE_URL }
                                        val client = DesktopControlClient(httpClient, baseUrl, token)
                                        client.stopStream()
                                        desktopStreamUrl = null
                                        toaster.show("Desktop stream stopped", ToastType.Info)
                                    } catch (e: Exception) {
                                        toaster.show(e.message ?: "Failed to stop desktop stream", ToastType.Error)
                                    }
                                }
                            } else {
                                desktopStreamUrl = null
                            }
                        }
                    )

                    ChatInput(
                        includeNavigationBarPadding = conversation.messageNodes.isNotEmpty() || isFolderChat,
                        onStartVoiceMode = onStartVoiceMode,
                        voiceState = voiceState,
                        onStopVoiceMode = vm.voiceSession::stop,
                        onOpenComputer = {
                            showDesktopSheet = true
                        },
                        onOpenPhone = {
                            activatePhoneMiniMode()
                        },
                        onLongOpenPhone = {
                            showPhoneAutomationSheet = true
                        },
                        state = inputState,
                        messageQueue = messageQueue,
                        onRemoveQueuedMessage = vm::removeQueuedMessage,
                        onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                        onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                        onResumeMessageQueue = vm::resumeMessageQueue,
                        loading = loadingJob != null,
                        settings = setting,
                        hazeState = hazeState,
                        completionProviders = completionProviders,
                        onCancelClick = {
                            vm.stopGeneration()
                        },
                        enableSearch = enableWebSearch,
                        onUpdateSearchMode = { mode ->
                            val current = setting.getCurrentAssistant()
                            val model = setting.getCurrentChatModel()
                            vm.updateSettings(
                                setting.copy(
                                    assistants = setting.assistants.map { assistant ->
                                        if (assistant.id == current.id) {
                                            assistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
                                        } else {
                                            assistant
                                        }
                                    },
                                    providers = if (model == null) {
                                        setting.providers
                                    } else {
                                        setting.providers.map { provider ->
                                            provider.editModel(
                                                model.copy(
                                                    tools = if (mode == SearchMode.BUILT_IN) {
                                                        model.tools + BuiltInTools.Search
                                                    } else {
                                                        model.tools - BuiltInTools.Search
                                                    }
                                                )
                                            )
                                        }
                                    },
                                )
                            )
                        },
                        onSendClick = { fromVoiceInput ->
                            if (currentChatModel == null) {
                                toaster.show("Please select a model first", type = ToastType.Error)
                                return@ChatInput
                            }
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
                                )
                            } else {
                                vm.handleMessageSend(
                                    inputState.getContents(),
                                    fromVoiceInput = fromVoiceInput,
                                )
                                scope.launch {
                                    delay(100.milliseconds)
                                    chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                }
                            }
                            inputState.clearInput()
                        },
                        onLongSendClick = {
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
                                )
                            } else {
                                vm.handleMessageSend(content = inputState.getContents(), answer = false)
                                scope.launch {
                                    chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                }
                            }
                            inputState.clearInput()
                        },
                        onUpdateChatModel = {
                            vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                        },
                        onUpdateAssistant = {
                            vm.updateSettings(
                                setting.copy(
                                    assistants = setting.assistants.map { assistant ->
                                        if (assistant.id == it.id) {
                                            it
                                        } else {
                                            assistant
                                        }
                                    }
                                )
                            )
                        },
                        onUpdateSearchService = { index ->
                            vm.updateSettings(
                                setting.copy(
                                    searchServiceSelected = index
                                )
                            )
                        },
                        onMoreClick = {
                            showFilesSheet = true
                        },
                    )
                    if (conversation.messageNodes.isEmpty() && !isFolderChat) {
                        HomeBottomBar(
                            onAnalyze = {
                                navController.navigate(Screen.Stats)
                            },
                            onSettings = {
                                navController.navigate(Screen.Setting)
                            },
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                folders = folders,
                folderName = if (isFolderChat) (activeFolderName ?: "Folder") else null,
                folderLabelId = activeFolderLabelId,
                isInitializing = isInitializing,
                onSelectFolder = { folder ->
                    navController.navigate(
                        Screen.FolderConversations(
                            folderId = folder.id.toString(),
                            folderName = folder.name,
                            folderLabelId = folder.label,
                        )
                    )
                },
                onSeeAllFolders = {
                    navController.navigate(Screen.Folders)
                },
                onOpenSearch = {
                    navController.navigate(Screen.MessageSearch)
                },
                onOpenActivity = {
                    navController.navigate(Screen.History)
                },
                onOpenAssistant = {
                    navController.navigate(Screen.Assistant)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Setting)
                },
                onNewFolder = {
                    showCreateFolderDialog = true
                },
                onOpenFavorite = {
                    navController.navigate(Screen.Favorite)
                },
                onQuickCreateFolder = { name, labelId ->
                    drawerVm.createFolder(name, labelId)
                },
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(
                        conversation.copy(
                            messageNodes = conversation.messageNodes.map { node ->
                                if (node.id == newNode.id) {
                                    newNode
                                } else {
                                    node
                                }
                            }
                        ))
                    vm.saveConversationAsync()
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.requestScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onToggleFavorite = { node ->
                    vm.toggleMessageFavorite(node)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                    vm.saveConversationAsync()
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }

        if (showPhoneAutomationSheet) {
            val filesManager: FilesManager = koinInject()
            PhoneAutomationSheet(
                assistant = assistant,
                onUpdateAssistant = { updatedAssistant ->
                    vm.updateSettings(
                        setting.copy(
                            assistants = setting.assistants.map {
                                if (it.id == updatedAssistant.id) updatedAssistant else it
                            }
                        )
                    )
                },
                onDismissRequest = { showPhoneAutomationSheet = false },
                onAppendPrompt = { prompt ->
                    val current = inputState.textContent.text.toString()
                    if (current.isNotBlank()) {
                        inputState.setMessageText("$current\n\n$prompt")
                    } else {
                        inputState.setMessageText(prompt)
                    }
                },
                onAttachScreenshot = { bytes ->
                    scope.launch {
                        val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
                        if (uris.isNotEmpty()) {
                            inputState.addImages(uris)
                        }
                    }
                }
            )
        }

        if (showDesktopSheet) {
            val filesManager: FilesManager = koinInject()
            DesktopControlSheet(
                assistant = assistant,
                onUpdateAssistant = { updatedAssistant ->
                    vm.updateSettings(
                        setting.copy(
                            assistants = setting.assistants.map {
                                if (it.id == updatedAssistant.id) updatedAssistant else it
                            }
                        )
                    )
                },
                networkSetting = setting.networkSetting,
                onUpdateNetworkSetting = { updatedNetworkSetting ->
                    vm.updateSettings(
                        setting.copy(networkSetting = updatedNetworkSetting)
                    )
                },
                onDismissRequest = { showDesktopSheet = false },
                onAppendPrompt = { prompt ->
                    val current = inputState.textContent.text.toString()
                    if (current.isNotBlank()) {
                        inputState.setMessageText("$current\n\n$prompt")
                    } else {
                        inputState.setMessageText(prompt)
                    }
                },
                onAttachScreenshot = { bytes ->
                    scope.launch {
                        val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
                        if (uris.isNotEmpty()) {
                            inputState.addImages(uris)
                        }
                    }
                },
                httpClient = httpClient,
                isStreaming = desktopStreamUrl != null,
                currentViewerUrl = desktopStreamUrl,
                onStreamStarted = { url ->
                    desktopStreamUrl = url
                },
                onStreamStopped = {
                    desktopStreamUrl = null
                }
            )
        }

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                visible = true,
                onDismissRequest = { showCreateFolderDialog = false },
                onConfirm = { name, label ->
                    drawerVm.createFolder(name, label.id)
                    showCreateFolderDialog = false
                }
            )
        }

        if (showMoveToFolderSheet) {
            MoveToFolderSheet(
                folders = folders,
                currentFolderId = conversation.folderId,
                onDismissRequest = { showMoveToFolderSheet = false },
                onSelectFolder = { selectedFolderId ->
                    vm.moveConversationToFolder(selectedFolderId)
                    val folderName = folders.firstOrNull { it.id == selectedFolderId }?.name
                    toaster.show(
                        if (folderName != null) "Saved to $folderName" else "Removed from folder",
                        type = ToastType.Success
                    )
                },
                onCreateNewFolder = { name, labelId ->
                    drawerVm.createFolder(name, labelId)
                }
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = {
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { assistant ->
                            if (assistant.id == it.id) {
                                it
                            } else {
                                assistant
                            }
                        }
                    )
                )
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onStartVoiceMode = if (voiceState.phase == VoicePhase.Off) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    folders: List<Folder>,
    previewMode: Boolean,
    currentFolder: Folder? = null,
    folderName: String? = null,
    folderLabelId: String? = null,
    onBack: () -> Unit,
    onMoveFolder: () -> Unit,
    onNewChat: () -> Unit,
    onClickMenu: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                val assistant = settings.getCurrentAssistant()
                val model = settings.getCurrentChatModel()
                val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
                val assistantName = assistant.name.ifBlank { defaultAssistantName }
                val sessionTitle = conversation.title.trim()
                val modelName = model?.displayName
                val activeFolderName = folderName ?: currentFolder?.name
                val activeFolderLabelId = folderLabelId ?: currentFolder?.label ?: "planning"
                val activeFolderLabel = remember(activeFolderLabelId) { FolderLabel.fromId(activeFolderLabelId) }

                if (activeFolderName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FolderBadge(
                            label = activeFolderLabel,
                            size = 32.dp,
                            iconSize = 16.dp,
                            shapeRadius = 10.dp,
                        )
                        Column {
                            Text(
                                text = if (sessionTitle.isNotEmpty()) sessionTitle else activeFolderName,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (sessionTitle.isNotEmpty()) {
                                    "$activeFolderName · $assistantName"
                                } else {
                                    "Saved in $activeFolderName · $assistantName"
                                },
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                } else {
                    val detail = when {
                        sessionTitle.isNotEmpty() && !modelName.isNullOrBlank() -> "$sessionTitle · $modelName"
                        sessionTitle.isNotEmpty() -> sessionTitle
                        !modelName.isNullOrBlank() -> stringResource(
                            R.string.assistant_home_ready_with_model,
                            modelName,
                        )
                        else -> stringResource(R.string.assistant_home_ready)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Column {
                            Text(
                                text = assistantName,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = detail,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                color = LocalContentColor.current.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        },
        actions = {
            val folderToDisplay = currentFolder ?: remember(conversation.folderId, folders) {
                folders.firstOrNull { it.id == conversation.folderId }
            }
            if (folderToDisplay != null) {
                val label = FolderLabel.fromId(folderToDisplay.label)
                IconButton(onClick = onMoveFolder) {
                    FolderBadge(
                        label = label,
                        size = 30.dp,
                        iconSize = 16.dp,
                        shapeRadius = 8.dp,
                    )
                }
            } else {
                IconButton(onClick = onMoveFolder) {
                    Icon(HugeIcons.Folder01, "Save to folder")
                }
            }

            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            IconButton(
                onClick = {
                    onNewChat()
                }
            ) {
                Icon(HugeIcons.Add01, stringResource(R.string.chat_page_new_chat))
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}
