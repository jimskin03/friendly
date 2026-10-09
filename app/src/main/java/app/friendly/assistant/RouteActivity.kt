package app.friendly.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.serialization.Serializable
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.db.DatabaseMigrationTracker
import app.friendly.assistant.data.db.MigrationState
import app.friendly.assistant.data.event.AppEvent
import app.friendly.assistant.data.event.AppEventBus
import app.friendly.assistant.ui.activity.SafeModeActivity
import app.friendly.assistant.ui.components.ui.TTSController
import app.friendly.assistant.ui.context.LocalASRState
import app.friendly.assistant.ui.context.LocalNavController
import app.friendly.assistant.ui.context.LocalSettings
import app.friendly.assistant.ui.context.LocalSharedTransitionScope
import app.friendly.assistant.ui.context.LocalTTSState
import app.friendly.assistant.ui.context.LocalToaster
import app.friendly.assistant.ui.context.Navigator
import app.friendly.assistant.ui.hooks.readBooleanPreference
import app.friendly.assistant.ui.hooks.readStringPreference
import app.friendly.assistant.ui.hooks.rememberCustomAsrState
import app.friendly.assistant.ui.hooks.rememberCustomTtsState
import app.friendly.assistant.ui.pages.apps.AppsPage
import app.friendly.assistant.ui.pages.apps.WebAppPage
import app.friendly.assistant.ui.pages.assistant.AssistantPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantBasicPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantDetailPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantExtensionsPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantLocalToolPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantMcpPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantMemoryPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantPromptPage
import app.friendly.assistant.ui.pages.assistant.detail.AssistantRequestPage
import app.friendly.assistant.ui.pages.backup.BackupPage
import app.friendly.assistant.ui.pages.chat.ChatPage
import app.friendly.assistant.ui.pages.debug.DebugPage
import app.friendly.assistant.ui.pages.extensions.ExtensionsPage
import app.friendly.assistant.ui.pages.extensions.PromptPage
import app.friendly.assistant.ui.pages.extensions.QuickMessagesPage
import app.friendly.assistant.ui.pages.extensions.skills.SkillDetailPage
import app.friendly.assistant.ui.pages.extensions.skills.SkillsPage
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspacePage
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspaceDetailPage
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.workspace.WorkspaceStorageArea
import app.friendly.assistant.ui.pages.favorite.FavoritePage
import app.friendly.assistant.ui.pages.history.HistoryPage
import app.friendly.assistant.ui.pages.log.LogPage
import app.friendly.assistant.ui.pages.search.SearchPage
import app.friendly.assistant.ui.pages.setting.SettingAboutPage
import app.friendly.assistant.ui.pages.setting.SettingPreferencesPage
import app.friendly.assistant.ui.pages.setting.SettingPreferencesThemePage
import app.friendly.assistant.ui.pages.setting.SettingPreferencesNotificationPage
import app.friendly.assistant.ui.pages.setting.SettingPreferencesGeneralPage
import app.friendly.assistant.ui.pages.setting.SettingPreferencesNetworkPage
import app.friendly.assistant.ui.pages.setting.SettingPreferencesUIPage
import app.friendly.assistant.ui.pages.setting.SettingThemePage
import app.friendly.assistant.ui.pages.setting.SettingFilesPage
import app.friendly.assistant.ui.pages.setting.SettingMcpPage
import app.friendly.assistant.ui.pages.setting.SettingModelPage
import app.friendly.assistant.ui.pages.setting.SettingHomeActionsPage
import app.friendly.assistant.ui.pages.setting.SettingPage
import app.friendly.assistant.ui.pages.setting.SettingProviderDetailPage
import app.friendly.assistant.ui.pages.setting.SettingProviderPage
import app.friendly.assistant.ui.pages.setting.SettingSearchDetailPage
import app.friendly.assistant.ui.pages.setting.SettingSearchPage
import app.friendly.assistant.ui.pages.setting.SettingSpeechPage
import app.friendly.assistant.ui.pages.share.handler.ShareHandlerPage
import app.friendly.assistant.ui.pages.stats.StatsPage
import app.friendly.assistant.ui.pages.webview.WebViewPage
import app.friendly.assistant.ui.theme.LocalDarkMode
import app.friendly.assistant.ui.theme.RikkahubTheme
import app.friendly.assistant.utils.CrashHandler
import app.friendly.assistant.utils.openUsageAccessSettings
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"

class RouteActivity : ComponentActivity() {
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private var navStack: MutableList<NavKey>? = null
    private val pendingIntents = ArrayDeque<Intent>()
    private var topChatId: String? = null

    companion object {
        const val ACTION_NEW_PROMPT = "app.friendly.assistant.action.NEW_PROMPT"
        const val ACTION_NEW_VOICE = "app.friendly.assistant.action.NEW_VOICE"

        /**
         * Conversation at the top of the nav stack while this activity is resumed.
         * Null when Friendly is not resumed or another screen is on top.
         */
        @Volatile
        var resumedTopChatId: String? = null
            private set
    }

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            RikkahubTheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(
                                OkHttpNetworkFetcherFactory(
                                    callFactory = { okHttpClient },
                                    cacheStrategy = { CacheControlCacheStrategy() },
                                )
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                AppRoutes()
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onResume() {
        super.onResume()
        resumedTopChatId = topChatId
    }

    override fun onPause() {
        resumedTopChatId = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val backStack = navStack ?: run {

            pendingIntents.addLast(intent)
            return
        }
        if (intent.action == ACTION_NEW_PROMPT) {
            backStack.clear()
            backStack.add(Screen.Chat(Uuid.random().toString()))
            return
        }
        if (intent.action == ACTION_NEW_VOICE) {
            backStack.clear()
            backStack.add(Screen.Chat(id = Uuid.random().toString(), autoStartVoice = true))
            return
        }
        val destination = when (intent.action) {
            Intent.ACTION_SEND -> Screen.ShareHandler(
                text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                streamUri = intent.getStringExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
            )
            else -> intent.getStringExtra("conversationId")?.let { Screen.Chat(it) }
        }
        // Already on this chat: do not push another copy (that would remount the transcript).
        if (destination is Screen.Chat) {
            val current = backStack.lastOrNull()
            if (current is Screen.Chat && current.id == destination.id) return
        }
        if (destination != null && backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun AppRoutes() {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val eventBus = koinInject<AppEventBus>()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.OpenUsageAccessSettings -> this@RouteActivity.openUsageAccessSettings()
                    is AppEvent.ChatGenerationUpdate -> Unit
                    is AppEvent.ChatGenerationEnded -> Unit
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        val startScreen = Screen.Chat(
            id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                Uuid.random().toString()
            } else {
                readStringPreference(
                    "lastConversationId",
                    Uuid.random().toString()
                ) ?: Uuid.random().toString()
            }
        )

        val backStack = rememberNavBackStack(startScreen)
        val topChat = (backStack.lastOrNull() as? Screen.Chat)?.id
        SideEffect {
            navStack = backStack
            topChatId = topChat
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                resumedTopChatId = topChat
            }
            while (pendingIntents.isNotEmpty()) {
                handleIntent(pendingIntents.removeFirst())
            }
        }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                    alignment = Alignment.TopCenter,
                    showCloseButton = true,
                )
                TTSController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NavDisplay(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        onBack = { backStack.removeLastOrNull() },
                        transitionSpec = {
                            if (backStack.size == 1) fadeIn() togetherWith fadeOut()
                            else {
                                slideInHorizontally { it } togetherWith
                                    slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                            }
                        },
                        popTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        entryProvider = entryProvider {
                            entry<Screen.Chat>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                    + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) },
                                    folderId = key.folderId,
                                    folderName = key.folderName,
                                    folderLabelId = key.folderLabelId,
                                    autoStartVoice = key.autoStartVoice,
                                )
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            entry<Screen.History> {
                                HistoryPage()
                            }

                            entry<Screen.FolderConversations> { key ->
                                app.friendly.assistant.ui.pages.folder.FolderConversationsPage(
                                    folderId = key.folderId,
                                    folderName = key.folderName,
                                    folderLabelId = key.folderLabelId,
                                )
                            }

                            entry<Screen.Folders> {
                                app.friendly.assistant.ui.pages.folder.FoldersPage()
                            }

                            entry<Screen.Favorite> {
                                FavoritePage()
                            }

                            entry<Screen.Assistant> {
                                AssistantPage()
                            }

                            entry<Screen.AssistantDetail> { key ->
                                AssistantDetailPage(key.id)
                            }

                            entry<Screen.AssistantBasic> { key ->
                                AssistantBasicPage(key.id)
                            }

                            entry<Screen.AssistantPrompt> { key ->
                                AssistantPromptPage(key.id)
                            }

                            entry<Screen.AssistantMemory> { key ->
                                AssistantMemoryPage(key.id)
                            }

                            entry<Screen.AssistantRequest> { key ->
                                AssistantRequestPage(key.id)
                            }

                            entry<Screen.AssistantMcp> { key ->
                                AssistantMcpPage(key.id)
                            }

                            entry<Screen.AssistantLocalTool> { key ->
                                AssistantLocalToolPage(key.id)
                            }

                            entry<Screen.AssistantInjections> { key ->
                                AssistantExtensionsPage(key.id)
                            }


                            entry<Screen.Setting> {
                                SettingPage()
                            }

                            entry<Screen.SettingHomeActions> {
                                SettingHomeActionsPage()
                            }

                            entry<Screen.Backup> {
                                BackupPage()
                            }

                            entry<Screen.WebView> { key ->
                                WebViewPage(key.url, key.contentId)
                            }

                            entry<Screen.Apps> {
                                AppsPage()
                            }

                            entry<Screen.WebApp> { key ->
                                WebAppPage(key.id)
                            }

                            entry<Screen.SettingTheme> {
                                SettingThemePage()
                            }

                            entry<Screen.SettingPreferences> {
                                SettingPreferencesPage()
                            }

                            entry<Screen.SettingPreferencesTheme> {
                                SettingPreferencesThemePage()
                            }

                            entry<Screen.SettingPreferencesNotification> {
                                SettingPreferencesNotificationPage()
                            }

                            entry<Screen.SettingPreferencesGeneral> {
                                SettingPreferencesGeneralPage()
                            }

                            entry<Screen.SettingPreferencesUI> {
                                SettingPreferencesUIPage()
                            }

                            entry<Screen.SettingPreferencesNetwork> {
                                SettingPreferencesNetworkPage()
                            }

                            entry<Screen.SettingProvider> {
                                SettingProviderPage()
                            }

                            entry<Screen.SettingProviderDetail> { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels> {
                                SettingModelPage()
                            }

                            entry<Screen.SettingAbout> {
                                SettingAboutPage()
                            }

                            entry<Screen.SettingSearch> {
                                SettingSearchPage()
                            }

                            entry<Screen.SettingSearchDetail> { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech> {
                                SettingSpeechPage()
                            }

                            entry<Screen.SettingMcp> {
                                SettingMcpPage()
                            }


                            entry<Screen.SettingFiles> {
                                SettingFilesPage()
                            }


                            entry<Screen.Debug> {
                                DebugPage()
                            }

                            entry<Screen.Log> {
                                LogPage()
                            }

                            entry<Screen.Extensions> {
                                ExtensionsPage()
                            }

                            entry<Screen.QuickMessages> {
                                QuickMessagesPage()
                            }

                            entry<Screen.Prompts> {
                                PromptPage()
                            }

                            entry<Screen.Skills> {
                                SkillsPage()
                            }

                            entry<Screen.Workspaces> {
                                WorkspacePage()
                            }

                            entry<Screen.WorkspaceDetail> { key ->
                                WorkspaceDetailPage(key.id)
                            }

                            entry<Screen.WorkspaceTerminal> { key ->
                                WorkspaceTerminalPage(key.id)
                            }

                            entry<Screen.WorkspaceFileEditor> { key ->
                                WorkspaceFileEditorPage(
                                    id = key.id,
                                    area = WorkspaceStorageArea.valueOf(key.area),
                                    path = key.path,
                                )
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.MessageSearch> {
                                SearchPage()
                            }

                            entry<Screen.Stats> {
                                StatsPage()
                            }
                        }
                    )
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[Dev Mode]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface Screen : NavKey {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null,
        val folderId: String? = null,
        val folderName: String? = null,
        val folderLabelId: String? = null,
        val autoStartVoice: Boolean = false,
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data class FolderConversations(
        val folderId: String,
        val folderName: String,
        val folderLabelId: String = "planning",
    ) : Screen

    @Serializable
    data object Folders : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen


    @Serializable
    data object Setting : Screen

    @Serializable
    data object SettingHomeActions : Screen

    @Serializable
    data object Apps : Screen

    @Serializable
    data class WebApp(val id: String) : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPreferences : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingPreferencesNetwork : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen


    @Serializable
    data object SettingFiles : Screen


    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen
}
